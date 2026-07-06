package com.geihou.module.supplychain.stock.enums;

import java.util.Objects;

/**
 * Stock count type enum (G2-02I-2).
 *
 * <p>FULL: 全盘 (full count)
 * CYCLE: 循环盘 (cycle count)
 * SPOT: 抽盘 (spot count)
 *
 * <p>Source: PRD-组2-01 §2.2, TASK-G2-02I-2.
 */
public enum StockCountTypeEnum {

    FULL("FULL", "全盘"),
    CYCLE("CYCLE", "循环盘"),
    SPOT("SPOT", "抽盘");

    private final String code;
    private final String label;

    StockCountTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static StockCountTypeEnum fromCode(String code) {
        Objects.requireNonNull(code, "count type code must not be null");
        for (StockCountTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown stock count type code: " + code);
    }

    public static boolean isValidCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (StockCountTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
