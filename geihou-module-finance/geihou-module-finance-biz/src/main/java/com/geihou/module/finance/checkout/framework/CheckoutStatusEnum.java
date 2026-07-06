package com.geihou.module.finance.checkout.framework;

import java.util.Objects;

/**
 * Checkout session status enum (ENUM_CHECKOUT_STATUS).
 *
 * <p>Source of truth: Global Enum Table V2, Section B, ENUM_CHECKOUT_STATUS.
 * 5 values: INITIATED / PAID / ABANDONED / EXPIRED / FAILED.
 *
 * <p>G1-04B: Full lifecycle implemented.
 * - INITIATED: checkout session created, cart locked as CHECKOUT
 * - PAID: simulated payment success (CG-9), order_id remains null (CG-7 Option C)
 * - ABANDONED: customer cancelled checkout
 * - EXPIRED: lazy expiry detected on query/pay (session expiry strategy)
 * - FAILED: simulated payment failure (CG-9)
 */
public enum CheckoutStatusEnum {

    INITIATED("INITIATED", "已发起"),
    PAID("PAID", "已支付"),
    ABANDONED("ABANDONED", "已放弃"),
    EXPIRED("EXPIRED", "已过期"),
    FAILED("FAILED", "支付失败");

    private final String code;
    private final String label;

    CheckoutStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static CheckoutStatusEnum fromCode(String code) {
        Objects.requireNonNull(code, "checkout status code must not be null");
        for (CheckoutStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown checkout status code: " + code);
    }
}
