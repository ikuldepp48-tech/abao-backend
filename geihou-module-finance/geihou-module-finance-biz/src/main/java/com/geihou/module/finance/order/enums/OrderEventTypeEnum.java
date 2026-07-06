package com.geihou.module.finance.order.enums;

import java.util.Objects;

/**
 * Order event type enum for order_event_log.
 *
 * <p>Values: CREATE / PAY / KITCHEN_IN / READY / DELIVER / COMPLETE / CANCEL /
 * REFUND_INITIATE / REFUND_COMPLETE.
 * Used in order_event_log.event_type.
 */
public enum OrderEventTypeEnum {

    CREATE("CREATE", "创建订单"),
    PAY("PAY", "支付成功"),
    KITCHEN_IN("KITCHEN_IN", "进入厨房"),
    READY("READY", "出餐完成"),
    DELIVER("DELIVER", "已送达/已取餐"),
    COMPLETE("COMPLETE", "订单完成"),
    CANCEL("CANCEL", "取消订单"),
    EXPIRE("EXPIRE", "订单超时"),
    REFUND_INITIATE("REFUND_INITIATE", "发起退款"),
    REFUND_COMPLETE("REFUND_COMPLETE", "退款完成"),
    REFUND_REJECT("REFUND_REJECT", "退款拒绝");

    private final String code;
    private final String label;

    OrderEventTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static OrderEventTypeEnum fromCode(String code) {
        Objects.requireNonNull(code, "status code must not be null");
        for (OrderEventTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown order event type code: " + code);
    }
}
