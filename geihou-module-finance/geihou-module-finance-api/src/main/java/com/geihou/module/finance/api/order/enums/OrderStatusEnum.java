package com.geihou.module.finance.api.order.enums;

import java.util.Objects;

/**
 * Order status enum (ENUM_ORDER_STATUS).
 *
 * <p>Source of truth: Global Enum Table V2, Section B, ENUM_ORDER_STATUS.
 * 12 values: PENDING / PAID / ACCEPTED / PREPARING / READY / DELIVERING /
 * DELIVERED / COMPLETED / REFUNDING / REFUNDED / CANCELLED / EXPIRED.
 *
 * <p>G1-01A first slice only implements CREATE → PENDING initial state.
 * Full state machine transitions are deferred to subsequent slices.
 */
public enum OrderStatusEnum {

    PENDING("PENDING", "待支付"),
    PAID("PAID", "已支付"),
    ACCEPTED("ACCEPTED", "已接单"),
    PREPARING("PREPARING", "制作中"),
    READY("READY", "待取餐/待出餐"),
    DELIVERING("DELIVERING", "配送中"),
    DELIVERED("DELIVERED", "已送达/已取餐"),
    COMPLETED("COMPLETED", "已完成"),
    REFUNDING("REFUNDING", "退款中"),
    REFUNDED("REFUNDED", "已退款"),
    CANCELLED("CANCELLED", "已取消"),
    EXPIRED("EXPIRED", "已超时");

    private final String code;
    private final String label;

    OrderStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static OrderStatusEnum fromCode(String code) {
        Objects.requireNonNull(code, "status code must not be null");
        for (OrderStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order status code: " + code);
    }
}
