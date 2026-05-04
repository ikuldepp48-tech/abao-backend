package cn.iocoder.yudao.module.restaurant.service.order.listener;

import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderItemDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderMapper;
import cn.iocoder.yudao.module.restaurant.service.kitchen.KdsPushMessage;
import cn.iocoder.yudao.module.restaurant.service.kitchen.KdsPushService;
import cn.iocoder.yudao.module.restaurant.service.kitchen.KitchenStationRouter;
import cn.iocoder.yudao.module.restaurant.service.order.event.OrderPaidEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单支付成功事件监听 — 路由菜品并推送到厨房档口
 */
@Slf4j
@Component
public class OrderPaidEventListener {

    @Resource
    private RestaurantOrderMapper orderMapper;

    @Resource
    private KitchenStationRouter stationRouter;

    @Resource
    private KdsPushService kdsPushService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        // 查订单
        RestaurantOrderDO order = orderMapper.selectById(event.getOrderId());
        if (order == null) {
            log.warn("[onOrderPaid][订单不存在 orderId({})]", event.getOrderId());
            return;
        }

        // 路由菜品到档口
        Map<Long, List<RestaurantOrderItemDO>> stationItems = stationRouter.route(order.getId());
        if (stationItems.isEmpty()) {
            log.info("[onOrderPaid][订单({})没有匹配到任何档口]", order.getOrderNo());
            return;
        }

        // 推送到各档口
        for (Map.Entry<Long, List<RestaurantOrderItemDO>> entry : stationItems.entrySet()) {
            Long stationId = entry.getKey();
            List<RestaurantOrderItemDO> items = entry.getValue();

            List<KdsPushMessage.OrderItemData> itemDataList = items.stream()
                    .map(i -> KdsPushMessage.OrderItemData.builder()
                            .spuName(i.getSpuName())
                            .skuName(i.getSkuName())
                            .quantity(i.getQuantity())
                            .addonsDesc(i.getAddonsJson())
                            .customerRemark(i.getCustomerRemark())
                            .unitPrice(i.getUnitPrice())
                            .subtotal(i.getSubtotal())
                            .build())
                    .collect(Collectors.toList());

            KdsPushMessage msg = KdsPushMessage.builder()
                    .type("NEW_ORDER")
                    .data(KdsPushMessage.OrderData.builder()
                            .orderId(order.getId())
                            .orderNo(order.getOrderNo())
                            .createTime(order.getCreateTime())
                            .items(itemDataList)
                            .build())
                    .build();

            kdsPushService.pushToStation(stationId, msg);
        }

        log.info("[onOrderPaid][订单({})已推送到 {} 个档口]", order.getOrderNo(), stationItems.size());
    }

}
