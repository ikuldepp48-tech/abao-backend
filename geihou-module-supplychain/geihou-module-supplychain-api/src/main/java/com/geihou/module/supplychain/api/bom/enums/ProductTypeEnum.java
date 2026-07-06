package com.geihou.module.supplychain.api.bom.enums;

import java.util.Objects;

/**
 * Product type enum for product_master.
 *
 * <p>Three values: FINISHED (成品), SEMI_FINISHED (半成品), RAW_MATERIAL (原料).
 *
 * <p>Source: TASK-G2-02A.
 */
public enum ProductTypeEnum {

    FINISHED("FINISHED", "成品"),
    SEMI_FINISHED("SEMI_FINISHED", "半成品"),
    RAW_MATERIAL("RAW_MATERIAL", "原料");

    private final String code;
    private final String label;

    ProductTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static ProductTypeEnum fromCode(String code) {
        Objects.requireNonNull(code, "product type code must not be null");
        for (ProductTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown product type code: " + code);
    }
}
