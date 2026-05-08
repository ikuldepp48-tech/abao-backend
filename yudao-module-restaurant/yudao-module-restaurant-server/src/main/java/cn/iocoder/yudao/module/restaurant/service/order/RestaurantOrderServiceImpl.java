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
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderLogDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSkuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSpuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderItemMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderLogMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderMapper;
import cn.iocoder.yudao.module.restaurant.enums.order.OrderStatusEnum;
import cn.iocoder.yudao.module.restaurant.service.order.event.OrderPaidEvent;
import cn.iocoder.yudao.module.restaurant.service.table.RestaurantTableService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.springframework.transaction.annotation.Transactional;
import static cn.iocoder.yudao.module.restaurant.enums.LogRecordConstants.*;

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
@Slf4j
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

    @Resource
    private RestaurantOrderLogMapper orderLogMapper;

    @Resource
    private RestaurantTableService tableService;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    @LogRecord(type = ORDER_TYPE, subType = ORDER_CREATE_SUB_TYPE, bizNo = "{{#order.orderNo}}",
            success = ORDER_CREATE_SUCCESS)
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
    @LogRecord(type = ORDER_TYPE, subType = ORDER_CANCEL_SUB_TYPE, bizNo = "{{#orderId}}",
            success = ORDER_CANCEL_SUCCESS)
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId, Long memberId) {
        RestaurantOrderDO order = orderMapper.selectById(orderId);
        if (order == null || !order.getMemberId().equals(memberId)) {
            throw exception(ORDER_NOT_EXISTS);
        }
        updateOrderStatus(orderId, 5, 0, memberId, "顾客取消订单");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onPaySuccess(String orderNo, Long payOrderId) {
        RestaurantOrderDO order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw exception(ORDER_NOT_EXISTS);
        }
        // 幂等：非待支付状态直接返回
        if (order.getStatus() != 0) {
            return;
        }
        order.setStatus(1); // 已支付
        order.setPayStatus(1);
        order.setPayTime(LocalDateTime.now());
        orderMapper.updateById(order);

        // 记录操作日志
        insertLog(order.getId(), 0, 1, 1, 0L, "支付成功回调，payOrderId=" + payOrderId);

        // 占用桌台
        if (order.getTableId() != null && order.getTableId() > 0) {
            try {
                tableService.occupyTable(order.getTableId(), order.getId());
            } catch (Exception e) {
                // 桌台占用失败不阻断支付回调
                log.error("[onPaySuccess][桌台占用失败 tableId({}) orderId({})]", order.getTableId(), order.getId(), e);
            }
        }

        // 发布支付成功事件（事务提交后触发 KDS 推送）
        eventPublisher.publishEvent(new OrderPaidEvent(order.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderStatus(Long orderId, Integer newStatus, Integer operatorType, Long operatorId, String remark) {
        RestaurantOrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw exception(ORDER_NOT_EXISTS);
        }
        Integer fromStatus = order.getStatus();
        if (!OrderStatusEnum.canTransition(fromStatus, newStatus)) {
            throw exception(ORDER_STATUS_INVALID, fromStatus, newStatus);
        }
        order.setStatus(newStatus);
        if (newStatus == 4) { // COMPLETED
            order.setCompleteTime(LocalDateTime.now());
        }
        orderMapper.updateById(order);

        insertLog(orderId, fromStatus, newStatus, operatorType, operatorId, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderBySystem(Long orderId) {
        RestaurantOrderDO order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != 0) {
            return;
        }
        order.setStatus(5); // 已取消
        orderMapper.updateById(order);

        insertLog(order.getId(), 0, 5, 1, 0L, "系统自动取消，超时未支付");

        // 释放桌台
        if (order.getTableId() != null && order.getTableId() > 0) {
            try {
                tableService.releaseTable(order.getTableId());
            } catch (Exception e) {
                log.error("[cancelOrderBySystem][释放桌台失败 tableId({}) orderId({})]", order.getTableId(), order.getId(), e);
            }
        }
    }

    @Override
    public RestaurantOrderDO getOrderByOrderNo(String orderNo) {
        return orderMapper.selectByOrderNo(orderNo);
    }

    private void insertLog(Long orderId, Integer fromStatus, Integer toStatus, Integer operatorType, Long operatorId, String remark) {
        RestaurantOrderLogDO log = RestaurantOrderLogDO.builder()
                .orderId(orderId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .operatorType(operatorType)
                .operatorId(operatorId)
                .remark(remark)
                .build();
        orderLogMapper.insert(log);
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
        vo.setPayTime(order.getPayTime());

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
