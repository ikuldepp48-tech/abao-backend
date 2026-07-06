package com.geihou.module.finance.api.order.enums;

import java.util.Objects;

/**
 * Payment status enum for order_payment table.
 *
 * <p>Source of truth: PRD-G1-01 Section 2.2 DDL order_payment.payment_status.
 * 3 values: INITIATED / SUCCESS / FAILED.
 */
public enum PaymentStatusEnum {

    INITIATED("INITIATED", "已发起"),
    SUCCESS("SUCCESS", "支付成功"),
    FAILED("FAILED", "支付失败");

    private final String code;
    private final String label;

    PaymentStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static PaymentStatusEnum fromCode(String code) {
        Objects.requireNonNull(code, "payment status code must not be null");
        for (PaymentStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown payment status code: " + code);
    }
}
