package com.geihou.module.finance.api.cart.enums;

import java.util.Objects;

/**
 * Cart status enum (ENUM_CART_STATUS).
 *
 * <p>Source of truth: Global Enum Table V2, Section B, ENUM_CART_STATUS.
 * 4 values: ACTIVE / CHECKOUT / CONVERTED / ABANDONED.
 *
 * <p>G1-04A: All 5 cart endpoints operate in ACTIVE status only.
 * CHECKOUT/CONVERTED/ABANDONED are reserved for G1-04B.
 */
public enum CartStatusEnum {

    ACTIVE("ACTIVE", "活动中"),
    CHECKOUT("CHECKOUT", "结算中"),
    CONVERTED("CONVERTED", "已转单"),
    ABANDONED("ABANDONED", "已放弃");

    private final String code;
    private final String label;

    CartStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static CartStatusEnum fromCode(String code) {
        Objects.requireNonNull(code, "cart status code must not be null");
        for (CartStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown cart status code: " + code);
    }
}
