package com.geihou.module.finance.api.order.enums;

import java.util.Objects;

/**
 * Refund status enum (ENUM_REFUND_STATUS).
 *
 * <p>Source of truth: Global Enum Table V2, ENUM_REFUND_STATUS.
 * 6 values: PENDING_REVIEW / APPROVED / REJECTED / REFUNDING / REFUNDED / REFUND_FAILED.
 *
 * <p>Used in order_refund.status field.
 */
public enum RefundStatusEnum {

    PENDING_REVIEW("PENDING_REVIEW", "待审核"),
    APPROVED("APPROVED", "已批准"),
    REJECTED("REJECTED", "已拒绝"),
    REFUNDING("REFUNDING", "退款中"),
    REFUNDED("REFUNDED", "已退款"),
    REFUND_FAILED("REFUND_FAILED", "退款失败");

    private final String code;
    private final String label;

    RefundStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static RefundStatusEnum fromCode(String code) {
        Objects.requireNonNull(code, "refund status code must not be null");
        for (RefundStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown refund status code: " + code);
    }
}
