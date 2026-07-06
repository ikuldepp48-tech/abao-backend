package com.geihou.module.supplychain.api.stock.enums;

/**
 * Stock reserve status (table-internal status, NOT a global ENUM_STOCK_EVENT_TYPE value).
 *
 * <p>Codex 裁决 #5: status 是表内状态，不是全局枚举。
 * This enum is NOT registered in the global enum table.
 */
public enum StockReserveStatusEnum {

    RESERVED("RESERVED", "已预留"),
    RELEASED("RELEASED", "已释放"),
    COMMITTED("COMMITTED", "已提交");

    private final String code;
    private final String label;

    StockReserveStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static StockReserveStatusEnum fromCode(String code) {
        for (StockReserveStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        throw new IllegalArgumentException("Unknown stock reserve status: " + code);
    }
}
