package com.geihou.module.finance.order.enums;

import java.util.Objects;

/**
 * Table session status enum (ENUM_TABLE_SESSION_STATUS).
 *
 * <p>Source of truth: Global Enum Table V2, ENUM_TABLE_SESSION_STATUS.
 * PRD-组1-01 Section 2.2 order_table_session DDL inline definition.
 * 5 values: OPEN / ORDERING / SERVING / SETTLING / CLOSED.
 *
 * <p>State flow: OPEN → ORDERING → {SERVING} → SETTLING → CLOSED
 * (SERVING is optional; ORDERING may transition directly to SETTLING per CG-TS2)
 *
 * <p>Used in order_table_session.status field.
 */
public enum TableSessionStatusEnum {

    OPEN("OPEN", "已开台"),
    ORDERING("ORDERING", "下单中"),
    SERVING("SERVING", "上菜中"),
    SETTLING("SETTLING", "结账中"),
    CLOSED("CLOSED", "已清台");

    private final String code;
    private final String label;

    TableSessionStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static TableSessionStatusEnum fromCode(String code) {
        Objects.requireNonNull(code, "table session status code must not be null");
        for (TableSessionStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown table session status code: " + code);
    }
}
