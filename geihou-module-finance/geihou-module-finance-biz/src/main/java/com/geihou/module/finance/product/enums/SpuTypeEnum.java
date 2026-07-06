package com.geihou.module.finance.product.enums;

import java.util.Objects;

/**
 * SPU type enum (ENUM_PRODUCT_TYPE).
 *
 * <p>Source of truth: Global Enum Table V2, Section B, ENUM_PRODUCT_TYPE.
 * Uses FINISHED (not NORMAL) per G1-02C Codex ruling.
 *
 * <p>Values FINISHED/SEMI_FINISHED/RAW_MATERIAL are from the global enum table.
 * COMBO/SERVICE are referenced in PRD-G1-02 OpenAPI but not yet registered in the
 * global enum table (independent risk, handled in a future slice).
 */
public enum SpuTypeEnum {

    FINISHED("FINISHED", "Finished product (sellable)"),
    SEMI_FINISHED("SEMI_FINISHED", "Semi-finished product (type A central kitchen)"),
    RAW_MATERIAL("RAW_MATERIAL", "Raw material"),
    COMBO("COMBO", "Combo (future slice)"),
    SERVICE("SERVICE", "Service fee (future slice)");

    private final String code;
    private final String label;

    SpuTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static SpuTypeEnum fromCode(String code) {
        Objects.requireNonNull(code, "spu type code must not be null");
        for (SpuTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown SPU type code: " + code);
    }
}
