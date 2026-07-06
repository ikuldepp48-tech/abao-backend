package com.geihou.module.finance.product.enums;

import java.util.Objects;

/**
 * Stock strategy enum.
 *
 * <p>Source: PRD-G1-02 Section 2.2 DDL (product_sku.stock_strategy).
 */
public enum StockStrategyEnum {

    TRACK_STOCK("TRACK_STOCK", "Track stock via BOM reverse"),
    UNLIMITED("UNLIMITED", "Virtual product / made-to-order, no stock tracking");

    private final String code;
    private final String label;

    StockStrategyEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static StockStrategyEnum fromCode(String code) {
        Objects.requireNonNull(code, "stock strategy code must not be null");
        for (StockStrategyEnum strategy : values()) {
            if (strategy.code.equals(code)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("Unknown stock strategy code: " + code);
    }
}
