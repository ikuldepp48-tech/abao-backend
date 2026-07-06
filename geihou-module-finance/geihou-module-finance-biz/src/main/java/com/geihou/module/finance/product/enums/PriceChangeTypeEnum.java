package com.geihou.module.finance.product.enums;

import java.util.Objects;

/**
 * Price change type enum.
 *
 * <p>Source: PRD-G1-02 Section 2.2 DDL (product_price_history.change_type).
 */
public enum PriceChangeTypeEnum {

    MANUAL("MANUAL", "Manual price change"),
    PROMOTION("PROMOTION", "Promotion-driven change"),
    COST_BASED("COST_BASED", "Cost-driven change"),
    MARKET_BASED("MARKET_BASED", "Market-driven adjustment");

    private final String code;
    private final String label;

    PriceChangeTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static PriceChangeTypeEnum fromCode(String code) {
        Objects.requireNonNull(code, "change type code must not be null");
        for (PriceChangeTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown price change type code: " + code);
    }
}
