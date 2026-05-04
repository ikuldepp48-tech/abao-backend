package cn.iocoder.yudao.module.restaurant.service.order;

import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderCreateReqDTO;
import cn.iocoder.yudao.module.restaurant.controller.admin.order.vo.RestaurantOrderPageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.app.order.vo.AppOrderCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.app.order.vo.AppOrderRespVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSkuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderItemDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSkuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSpuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderItemMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.*;

@Service
public class RestaurantOrderServiceImpl implements RestaurantOrderService {

    @Resource
    private RestaurantOrderMapper orderMapper;

    @Resource
    private RestaurantOrderItemMapper orderItemMapper;

    @Resource
    private RestaurantDishSkuMapper dishSkuMapper;

    @Resource
    private RestaurantDishSpuMapper dishSpuMapper;

    @Resource
    private PayOrderApi payOrderApi;

    @Override
    @Transactional
    public AppOrderRespVO createOrder(Long memberId, String userIp, AppOrderCreateReqVO reqVO) {
        // 幂等性检查：同一 clientOrderNo + memberId 不重复创建
        RestaurantOrderDO existing = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RestaurantOrderDO>()
                        .eq(RestaurantOrderDO::getClientOrderNo, reqVO.getClientOrderNo())
                        .eq(RestaurantOrderDO::getMemberId, memberId));
        if (existing != null) {
            return buildResp(existing);
        }

        // 生成订单号: RT + yyyyMMdd + 6位随机
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String orderNo = "RT" + datePart + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase();

        // 服务端计算金额（不信任客户端传的金额）
        BigDecimal originalAmount = BigDecimal.ZERO;
        for (AppOrderCreateReqVO.OrderItem item : reqVO.getItems()) {
            // 查 SKU 获取价格和 spuId
            RestaurantDishSkuDO sku = dishSkuMapper.selectById(item.getSkuId());
            if (sku == null) {
                throw exception(DISH_SKU_NOT_EXISTS);
            }
            BigDecimal skuPrice = sku.getPrice() != null ? sku.getPrice() : BigDecimal.ZERO;

            // 计算加料总价（同时验证客户端传的加料价格）
            BigDecimal addonTotal = BigDecimal.ZERO;
            if (item.getAddons() != null) {
                for (AppOrderCreateReqVO.AddonItem addon : item.getAddons()) {
                    addonTotal = addonTotal.add(addon.getPrice() != null ? addon.getPrice() : BigDecimal.ZERO);
                }
            }

            BigDecimal itemUnitPrice = skuPrice.add(addonTotal);
            originalAmount = originalAmount.add(itemUnitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        // 创建订单
        RestaurantOrderDO order = RestaurantOrderDO.builder()
                .orderNo(orderNo)
                .memberId(memberId)
                .storeId(reqVO.getStoreId())
                .tableId(reqVO.getTableId())
                .orderType(reqVO.getOrderType())
                .dinerCount(reqVO.getDinerCount())
                .status(0) // 待支付
                .payStatus(0)
                .originalAmount(originalAmount)
                .discountAmount(BigDecimal.ZERO)
                .payAmount(originalAmount)
                .remark(reqVO.getRemark())
                .clientOrderNo(reqVO.getClientOrderNo())
                .build();
        orderMapper.insert(order);

        // 创建订单明细
        for (AppOrderCreateReqVO.OrderItem item : reqVO.getItems()) {
            RestaurantDishSkuDO sku = dishSkuMapper.selectById(item.getSkuId());
            BigDecimal skuPrice = sku.getPrice() != null ? sku.getPrice() : BigDecimal.ZERO;

            // 查 SPU 获取菜品名
            RestaurantDishSpuDO spu = dishSpuMapper.selectById(sku.getSpuId());
            String spuName = spu != null ? spu.getName() : "";

            BigDecimal addonTotal = BigDecimal.ZERO;
            String addonsJson = "[]";
            if (item.getAddons() != null && !item.getAddons().isEmpty()) {
                addonTotal = item.getAddons().stream()
                        .map(a -> a.getPrice() != null ? a.getPrice() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                addonsJson = item.getAddons().stream()
                        .map(a -> "{\"id\":" + a.getId() + ",\"name\":\"" + a.getName() + "\",\"price\":" + a.getPrice() + "}")
                        .collect(Collectors.joining(",", "[", "]"));
            }
            BigDecimal unitPrice = skuPrice.add(addonTotal);
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));

            RestaurantOrderItemDO orderItem = RestaurantOrderItemDO.builder()
                    .orderId(order.getId())
                    .spuId(sku.getSpuId())
                    .spuName(spuName)
                    .skuId(item.getSkuId())
                    .skuName(sku.getName())
                    .unitPrice(unitPrice)
                    .quantity(item.getQuantity())
                    .addonsJson(addonsJson)
                    .customerRemark(item.getCustomerRemark())
                    .subtotal(subtotal)
                    .build();
            orderItemMapper.insert(orderItem);
        }

        // 调用支付服务创建支付单
        PayOrderCreateReqDTO payReq = new PayOrderCreateReqDTO();
        payReq.setAppKey("abao_restaurant");
        payReq.setUserIp(userIp);
        payReq.setUserId(memberId);
        payReq.setUserType(UserTypeEnum.MEMBER.getValue());
        payReq.setMerchantOrderId(orderNo);
        payReq.setSubject("阿堡餐饮订单");
        payReq.setBody("订单号:" + orderNo);
        payReq.setPrice(order.getPayAmount().multiply(new BigDecimal("100")).intValue());
        payReq.setExpireTime(LocalDateTime.now().plusMinutes(15));
        Long payOrderId = payOrderApi.createOrder(payReq).getData();
        order.setPayOrderId(payOrderId);
        orderMapper.updateById(order);

        return buildResp(order);
    }

    @Override
    public AppOrderRespVO getOrderDetail(Long orderId) {
        RestaurantOrderDO order = orderMapper.selectById(orderId);
        if (order == null) throw exception(ORDER_NOT_EXISTS);
        return buildResp(order);
    }

    @Override
    public AppOrderRespVO getOrderDetail(Long orderId, Long memberId) {
        RestaurantOrderDO order = orderMapper.selectById(orderId);
        if (order == null || !order.getMemberId().equals(memberId)) {
            throw exception(ORDER_NOT_EXISTS);
        }
        return buildResp(order);
    }

    @Override
    public PageResult<RestaurantOrderDO> getOrderPage(Long memberId, Integer pageNo, Integer pageSize, Integer status) {
        return orderMapper.selectPage(
                new cn.iocoder.yudao.framework.common.pojo.PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }},
                new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<RestaurantOrderDO>()
                        .eq(RestaurantOrderDO::getMemberId, memberId)
                        .eqIfPresent(RestaurantOrderDO::getStatus, status)
                        .orderByDesc(RestaurantOrderDO::getId));
    }

    @Override
    public PageResult<RestaurantOrderDO> getOrderPage(RestaurantOrderPageReqVO reqVO) {
        return orderMapper.selectPage(reqVO);
    }

    @Override
    public void cancelOrder(Long orderId, Long memberId) {
        RestaurantOrderDO order = orderMapper.selectById(orderId);
        if (order == null || !order.getMemberId().equals(memberId)) {
            throw exception(ORDER_NOT_EXISTS);
        }
        if (order.getStatus() != 0) {
            throw exception(ORDER_STATUS_ERROR);
        }
        order.setStatus(5); // 已取消
        orderMapper.updateById(order);
    }

    @Override
    public Map<String, Long> getOrderCount(Long memberId) {
        List<RestaurantOrderDO> orders = orderMapper.selectList(
                new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<RestaurantOrderDO>()
                        .eq(RestaurantOrderDO::getMemberId, memberId));
        long pending = orders.stream().filter(o -> o.getStatus() == 0).count();
        long progress = orders.stream().filter(o -> o.getStatus() >= 1 && o.getStatus() <= 3).count();
        long done = orders.stream().filter(o -> o.getStatus() == 4).count();
        Map<String, Long> result = new HashMap<>();
        result.put("pending", pending);
        result.put("progress", progress);
        result.put("done", done);
        return result;
    }

    private AppOrderRespVO buildResp(RestaurantOrderDO order) {
        AppOrderRespVO vo = new AppOrderRespVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStoreId(order.getStoreId());
        vo.setTableId(order.getTableId());
        vo.setOrderType(order.getOrderType());
        vo.setDinerCount(order.getDinerCount());
        vo.setStatus(order.getStatus());
        vo.setPayStatus(order.getPayStatus());
        vo.setOriginalAmount(order.getOriginalAmount());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setRemark(order.getRemark());
        vo.setCreateTime(order.getCreateTime());
        vo.setPayOrderId(order.getPayOrderId());

        // 查询明细
        List<RestaurantOrderItemDO> items = orderItemMapper.selectListByOrderId(order.getId());
        vo.setItems(items.stream().map(i -> {
            AppOrderRespVO.OrderItem item = new AppOrderRespVO.OrderItem();
            item.setSpuName(i.getSpuName());
            item.setSkuName(i.getSkuName());
            item.setUnitPrice(i.getUnitPrice());
            item.setQuantity(i.getQuantity());
            item.setAddonsDesc(i.getAddonsJson());
            item.setCustomerRemark(i.getCustomerRemark());
            item.setSubtotal(i.getSubtotal());
            return item;
        }).collect(Collectors.toList()));

        return vo;
    }

}
