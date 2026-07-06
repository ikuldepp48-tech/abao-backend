package com.geihou.module.supplychain.api.stock.enums;

/**
 * Coverage gap type for stock mapping coverage audit (G2-01B3B).
 *
 * <p>Identifies which kind of mapping was missing when finance stock integration
 * hit a SKIP decision during checkout reserve.
 */
public enum StockCoverageTypeEnum {

    LOCATION_MISSING("LOCATION_MISSING", "门店库存位置映射缺失"),
    SKU_CODE_MISSING("SKU_CODE_MISSING", "SKU 编码缺失"),
    STOCK_ITEM_MISSING("STOCK_ITEM_MISSING", "库存品项映射缺失");

    private final String code;
    private final String label;

    StockCoverageTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static StockCoverageTypeEnum fromCode(String code) {
        for (StockCoverageTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        throw new IllegalArgumentException("Unknown stock coverage type: " + code);
    }
}
