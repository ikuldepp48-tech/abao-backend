package com.geihou.module.finance.order.enums;

import java.util.Objects;

/**
 * Refund reason type enum (ENUM_REFUND_REASON).
 *
 * <p>Source of truth: Global Enum Table V2, ENUM_REFUND_REASON.
 * PRD-组1-01 Section 2.2 order_refund DDL inline definition.
 * 5 values: CUSTOMER_REQUEST / QUALITY_ISSUE / WRONG_ORDER / OUT_OF_STOCK / OTHER.
 *
 * <p>Used in order_refund.reason_type field.
 */
public enum RefundReasonTypeEnum {

    CUSTOMER_REQUEST("CUSTOMER_REQUEST", "顾客申请"),
    QUALITY_ISSUE("QUALITY_ISSUE", "质量问题"),
    WRONG_ORDER("WRONG_ORDER", "下错单"),
    OUT_OF_STOCK("OUT_OF_STOCK", "缺货"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String label;

    RefundReasonTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static RefundReasonTypeEnum fromCode(String code) {
        Objects.requireNonNull(code, "refund reason type code must not be null");
        for (RefundReasonTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown refund reason type code: " + code);
    }
}
