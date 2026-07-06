package com.geihou.module.supplychain.api.bom.enums;

import java.util.Objects;

/**
 * BOM recipe status enum.
 *
 * <p>Three values: DRAFT (草稿), ACTIVE (已激活), ARCHIVED (已归档).
 *
 * <p>Source: TASK-G2-02A.
 */
public enum BomRecipeStatusEnum {

    DRAFT("DRAFT", "草稿"),
    ACTIVE("ACTIVE", "已激活"),
    ARCHIVED("ARCHIVED", "已归档");

    private final String code;
    private final String label;

    BomRecipeStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static BomRecipeStatusEnum fromCode(String code) {
        Objects.requireNonNull(code, "recipe status code must not be null");
        for (BomRecipeStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown recipe status code: " + code);
    }
}
