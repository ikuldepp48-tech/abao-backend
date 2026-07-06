package com.geihou.module.finance.api.cart.enums;

import java.util.Objects;

/**
 * Cart event type enum (ENUM_CART_EVENT_TYPE).
 *
 * <p>Source of truth: Global Enum Table V2, Section B, ENUM_CART_EVENT_TYPE.
 * 7 values: ITEM_ADDED / ITEM_QUANTITY_CHANGED / ITEM_REMOVED / CART_CLEARED /
 * CHECKOUT_STARTED / CHECKOUT_ABANDONED / CART_EXPIRED.
 *
 * <p>G1-04A uses first 4 values only (ITEM_ADDED / ITEM_QUANTITY_CHANGED /
 * ITEM_REMOVED / CART_CLEARED). Last 3 values are reserved for G1-04B.
 */
public enum CartEventTypeEnum {

    ITEM_ADDED("ITEM_ADDED", "顾客加购"),
    ITEM_QUANTITY_CHANGED("ITEM_QUANTITY_CHANGED", "顾客修改数量"),
    ITEM_REMOVED("ITEM_REMOVED", "顾客删除购物车项"),
    CART_CLEARED("CART_CLEARED", "顾客清空购物车"),
    CHECKOUT_STARTED("CHECKOUT_STARTED", "顾客发起结算"),
    CHECKOUT_ABANDONED("CHECKOUT_ABANDONED", "结算放弃"),
    CART_EXPIRED("CART_EXPIRED", "购物车过期");

    private final String code;
    private final String label;

    CartEventTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static CartEventTypeEnum fromCode(String code) {
        Objects.requireNonNull(code, "cart event type code must not be null");
        for (CartEventTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown cart event type code: " + code);
    }
}
