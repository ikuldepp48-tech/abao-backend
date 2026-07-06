package com.geihou.module.finance.order.enums;

import java.util.Objects;

/**
 * Refund type enum.
 *
 * <p>Source: PRD-组1-01 Section 2.2 order_refund DDL inline definition.
 * 3 values: FULL / PARTIAL / ITEM.
 *
 * <p>Used in order_refund.refund_type field.
 */
public enum RefundTypeEnum {

    FULL("FULL", "全退"),
    PARTIAL("PARTIAL", "部分退"),
    ITEM("ITEM", "按明细退");

    private final String code;
    private final String label;

    RefundTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static RefundTypeEnum fromCode(String code) {
        Objects.requireNonNull(code, "refund type code must not be null");
        for (RefundTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown refund type code: " + code);
    }
}
