package cn.iocoder.yudao.module.restaurant.service.order.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单支付成功事件 — 用于触发 KDS 推送等后续动作
 */
@Getter
@RequiredArgsConstructor
public class OrderPaidEvent {

    private final Long orderId;

}
