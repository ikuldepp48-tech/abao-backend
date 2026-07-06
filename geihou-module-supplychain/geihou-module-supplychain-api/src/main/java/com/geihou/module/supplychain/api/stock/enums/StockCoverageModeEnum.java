package com.geihou.module.supplychain.api.stock.enums;

/**
 * Coverage audit enforcement mode (G2-01B3B).
 *
 * <p>AUDIT_ONLY (default): record observation, preserve SKIP behavior.
 * ENFORCE: record observation, fail before reserve for unmapped mappings.
 */
public enum StockCoverageModeEnum {

    AUDIT_ONLY("AUDIT_ONLY", "仅审计（保留 SKIP 行为）"),
    ENFORCE("ENFORCE", "强制（未映射时阻止预留）");

    private final String code;
    private final String label;

    StockCoverageModeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static StockCoverageModeEnum fromCode(String code) {
        for (StockCoverageModeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        throw new IllegalArgumentException("Unknown stock coverage mode: " + code);
    }
}
