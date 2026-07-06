package com.geihou.module.finance.api.cart.enums;

import java.util.Objects;

/**
 * Cart item state enum (ENUM_CART_ITEM_STATE).
 *
 * <p>Source of truth: Global Enum Table V2, Section B, ENUM_CART_ITEM_STATE.
 * 4 values: NORMAL / SOLD_OUT / PRICE_CHANGED / UNAVAILABLE.
 *
 * <p>G1-04A degradation (CG-5): item_state defaults to NORMAL.
 * SOLD_OUT is not auto-set (StockApi not implemented, deferred to G1-04B).
 * PRICE_CHANGED detection is optional in first slice (snapshot price locked at add time).
 */
public enum CartItemStateEnum {

    NORMAL("NORMAL", "正常可购买"),
    SOLD_OUT("SOLD_OUT", "已售罄"),
    PRICE_CHANGED("PRICE_CHANGED", "价格已变更"),
    UNAVAILABLE("UNAVAILABLE", "不可用");

    private final String code;
    private final String label;

    CartItemStateEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static CartItemStateEnum fromCode(String code) {
        Objects.requireNonNull(code, "cart item state code must not be null");
        for (CartItemStateEnum state : values()) {
            if (state.code.equals(code)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown cart item state code: " + code);
    }
}
