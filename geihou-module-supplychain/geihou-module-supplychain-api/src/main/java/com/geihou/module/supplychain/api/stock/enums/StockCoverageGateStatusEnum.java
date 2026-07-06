package com.geihou.module.supplychain.api.stock.enums;

/**
 * Readiness gate status for stock mapping coverage (G2-01B3C).
 *
 * <p>READY: zero unresolved observations.
 * READY_WITH_WARNINGS: unresolved observations exist but mode is AUDIT_ONLY,
 *   or only SKU_CODE_MISSING remains unresolved in ENFORCE.
 * NOT_READY: ENFORCE mode with unresolved LOCATION_MISSING or STOCK_ITEM_MISSING.
 */
public enum StockCoverageGateStatusEnum {

    READY("READY", "就绪"),
    READY_WITH_WARNINGS("READY_WITH_WARNINGS", "就绪但有告警"),
    NOT_READY("NOT_READY", "未就绪");

    private final String code;
    private final String label;

    StockCoverageGateStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }
}