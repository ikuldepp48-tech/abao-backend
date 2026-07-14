package com.geihou.module.supplychain.api.stock.enums;

import java.util.Objects;

/**
 * Supplychain command operation enum for the 6 finance->supplychain write endpoints.
 *
 * <p>C0 (G0-04H185-FIN-CONTRACT-REVISION-C0) introduced the command-status endpoint;
 * FIN-CONSISTENCY implements the journal infrastructure. Used by
 * supplychain_command_journal.operation column.
 * Exactly 6 values - no abbreviations or additions allowed.
 */
public enum SupplychainCommandOperationEnum {

    /** 锁定库存 */
    RESERVE("RESERVE", "RESERVE"),
    /** 释放锁定 */
    RELEASE("RELEASE", "RELEASE"),
    /** 确认扣减 */
    COMMIT("COMMIT", "COMMIT"),
    /** BOM 反推扣料 */
    SALES_OUT_BOM_REVERSE("SALES_OUT_BOM_REVERSE", "SALES_OUT_BOM_REVERSE"),
    /** 销售退货还原 */
    SALES_REVERSE_RESTORE("SALES_REVERSE_RESTORE", "SALES_REVERSE_RESTORE"),
    /** 观察缺失映射 */
    OBSERVE_MISSING_MAPPING("OBSERVE_MISSING_MAPPING", "OBSERVE_MISSING_MAPPING");

    private final String code;
    private final String label;

    SupplychainCommandOperationEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static SupplychainCommandOperationEnum fromCode(String code) {
        Objects.requireNonNull(code, "operation code must not be null");
        for (SupplychainCommandOperationEnum op : values()) {
            if (op.code.equals(code)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown supplychain command operation code: " + code);
    }
}
