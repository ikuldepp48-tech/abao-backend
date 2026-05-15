package cn.iocoder.yudao.module.restaurant.service.kds;

import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.kitchen.RestaurantKitchenStationDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderItemDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSpuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.kitchen.RestaurantKitchenStationMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderItemMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderLogMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderMapper;
import cn.iocoder.yudao.module.restaurant.service.kitchen.KdsPushService;
import com.mzt.logapi.starter.annotation.LogRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.restaurant.enums.LogRecordConstants.*;

@Slf4j
@Service
public class KdsServiceImpl implements KdsService {

    @Resource
    private RestaurantKitchenStationMapper stationMapper;

    @Resource
    private RestaurantDishSpuMapper dishSpuMapper;

    @Resource
    private RestaurantOrderItemMapper orderItemMapper;

    @Resource
    private RestaurantOrderMapper orderMapper;

    @Resource
    private RestaurantOrderLogMapper orderLogMapper;

    @Resource
    private KdsPushService kdsPushService;

    @Override
    public Map<Long, List<RestaurantOrderItemDO>> getStationOrderItems(Long stationId) {
        RestaurantKitchenStationDO station = stationMapper.selectById(stationId);
        if (station == null) {
            return Collections.emptyMap();
        }
        List<Long> categoryIds = JSONUtil.toList(station.getDishCategories(), Long.class);
        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 找到这些分类下的所有 SPU ID
        List<RestaurantDishSpuDO> spus = dishSpuMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RestaurantDishSpuDO>()
                        .in(RestaurantDishSpuDO::getCategoryId, categoryIds));
        Set<Long> spuIds = spus.stream().map(RestaurantDishSpuDO::getId).collect(Collectors.toSet());
        if (spuIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 查这些 SPU 的订单项，状态为待制作或制作中
        List<RestaurantOrderItemDO> items = orderItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RestaurantOrderItemDO>()
                        .in(RestaurantOrderItemDO::getSpuId, spuIds)
                        .in(RestaurantOrderItemDO::getKdsStatus, Arrays.asList(0, 1))
                        .orderByAsc(RestaurantOrderItemDO::getCreateTime));

        // 按 orderId 分组
        return items.stream().collect(Collectors.groupingBy(
                RestaurantOrderItemDO::getOrderId, LinkedHashMap::new, Collectors.toList()));
    }

    @Override
    @LogRecord(type = KDS_TYPE, subType = KDS_START_SUB_TYPE, bizNo = "{{#itemId}}",
            success = KDS_START_SUCCESS)
    @Transactional(rollbackFor = Exception.class)
    public void startItem(Long itemId) {
        RestaurantOrderItemDO item = orderItemMapper.selectById(itemId);
        if (item == null) {
            throw exception(ORDER_ITEM_NOT_EXISTS);
        }
        if (item.getKdsStatus() == null || item.getKdsStatus() != 0) {
            throw exception(ORDER_STATUS_ERROR);
        }
        item.setKdsStatus(1);
        orderItemMapper.updateById(item);

        // 检查主订单状态：若为已支付(1)，升级为备餐中(2)
        RestaurantOrderDO order = orderMapper.selectById(item.getOrderId());
        if (order != null && order.getStatus() == 1) {
            order.setStatus(2);
            orderMapper.updateById(order);
            // 记录操作日志
            cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderLogDO logDO =
                    cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderLogDO.builder()
                    .orderId(order.getId())
                    .fromStatus(1)
                    .toStatus(2)
                    .operatorType(2) // 商家
                    .operatorId(0L)
                    .remark("KDS开始制作，itemId=" + itemId)
                    .build();
            orderLogMapper.insert(logDO);
        }

        // 推送状态更新到档口
        pushStatusToStation(item, 1);
    }

    @Override
    @LogRecord(type = KDS_TYPE, subType = KDS_FINISH_SUB_TYPE, bizNo = "{{#itemId}}",
            success = KDS_FINISH_SUCCESS)
    @Transactional(rollbackFor = Exception.class)
    public void finishItem(Long itemId) {
        RestaurantOrderItemDO item = orderItemMapper.selectById(itemId);
        if (item == null) {
            throw exception(ORDER_ITEM_NOT_EXISTS);
        }
        if (item.getKdsStatus() == null || item.getKdsStatus() != 1) {
            throw exception(ORDER_STATUS_ERROR);
        }
        item.setKdsStatus(2);
        orderItemMapper.updateById(item);

        // 检查该订单的所有 item 是否都已出餐
        List<RestaurantOrderItemDO> allItems = orderItemMapper.selectListByOrderId(item.getOrderId());
        boolean allDone = allItems.stream().allMatch(i -> i.getKdsStatus() != null && i.getKdsStatus() == 2);
        if (allDone) {
            RestaurantOrderDO order = orderMapper.selectById(item.getOrderId());
            if (order != null && order.getStatus() == 2) {
                order.setStatus(3); // PREPARING → READY
                orderMapper.updateById(order);
                cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderLogDO logDO =
                        cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderLogDO.builder()
                        .orderId(order.getId())
                        .fromStatus(2)
                        .toStatus(3)
                        .operatorType(2)
                        .operatorId(0L)
                        .remark("KDS全部出餐完成")
                        .build();
                orderLogMapper.insert(logDO);
            }
        }

        // 推送状态更新到档口
        pushStatusToStation(item, 2);
    }

    private void pushStatusToStation(RestaurantOrderItemDO item, Integer newStatus) {
        // 找到该菜品所属的档口并推送
        RestaurantDishSpuDO spu = dishSpuMapper.selectById(item.getSpuId());
        if (spu == null || spu.getCategoryId() == null) return;

        List<RestaurantKitchenStationDO> stations = stationMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RestaurantKitchenStationDO>()
                        .eq(RestaurantKitchenStationDO::getStatus, 1));
        for (RestaurantKitchenStationDO station : stations) {
            List<Long> categoryIds = JSONUtil.toList(station.getDishCategories(), Long.class);
            if (categoryIds.contains(spu.getCategoryId())) {
                kdsPushService.pushStatusUpdate(station.getId(), item.getOrderId(), item.getId(), newStatus);
            }
        }
    }
}
