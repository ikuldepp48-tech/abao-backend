package com.geihou.module.supplychain.api.stock.enums;

/**
 * Stock location type.
 * Matches ENUM_STOCK_WAREHOUSE_TYPE from the global enum table.
 */
public enum StockLocationTypeEnum {

    CENTRAL_KITCHEN("CENTRAL_KITCHEN", "中央厨房仓"),
    STORE("STORE", "门店仓"),
    TRANSIT("TRANSIT", "在途"),
    VIRTUAL("VIRTUAL", "虚拟");

    private final String code;
    private final String label;

    StockLocationTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static StockLocationTypeEnum fromCode(String code) {
        for (StockLocationTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        throw new IllegalArgumentException("Unknown stock location type: " + code);
    }
}
