package com.geihou.module.finance.order.enums;

import java.util.Objects;

/**
 * Order item type enum (line-level kitchen status).
 *
 * <p>Values: PENDING / KITCHEN / READY / SERVED.
 * Used in order_items.item_status for KDS linkage.
 */
public enum OrderItemTypeEnum {

    PENDING("PENDING", "待制作"),
    KITCHEN("KITCHEN", "制作中"),
    READY("READY", "已出餐"),
    SERVED("SERVED", "已上桌/已取餐");

    private final String code;
    private final String label;

    OrderItemTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static OrderItemTypeEnum fromCode(String code) {
        Objects.requireNonNull(code, "status code must not be null");
        for (OrderItemTypeEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order item status code: " + code);
    }
}
