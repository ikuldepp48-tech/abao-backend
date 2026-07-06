package com.geihou.module.supplychain.stock.enums;

import java.util.Objects;

/**
 * Stock count session status enum (G2-02I-2).
 *
 * <p>Persisted states: PLANNING / IN_PROGRESS / DIFF_REVIEW / ADJUSTED.
 * APPROVED is a conceptual intermediate state (not persisted in this slice).
 * No CANCELLED state (PRD §4.2 state machine does not define it).
 *
 * <p>State machine:
 * <pre>
 * PLANNING → IN_PROGRESS → DIFF_REVIEW → ADJUSTED
 * </pre>
 *
 * <p>Source: PRD-组2-01 §4.2, TASK-G2-02I-2.
 */
public enum StockCountStatusEnum {

    PLANNING("PLANNING", "计划中"),
    IN_PROGRESS("IN_PROGRESS", "盘点中"),
    DIFF_REVIEW("DIFF_REVIEW", "差异复核"),
    APPROVED("APPROVED", "审批通过(概念中间态,不持久化)"),
    ADJUSTED("ADJUSTED", "已调整");

    private final String code;
    private final String label;

    StockCountStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static StockCountStatusEnum fromCode(String code) {
        Objects.requireNonNull(code, "status code must not be null");
        for (StockCountStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown stock count status code: " + code);
    }
}
