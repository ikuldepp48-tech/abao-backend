package com.geihou.module.finance.product.enums;

import java.util.Objects;

/**
 * 加料选项状态 enum (ENUM_ADDON_OPTION_STATUS).
 *
 * <p>Source of truth: Global Enum Table V2, Section B, ENUM_ADDON_OPTION_STATUS.
 * Values must match PRD-G1-02 Section 2.2 DDL (product_addon_option.status) and
 * Section 4.3 exception handling ("加料 SKU 库存不足 → 该加料项 SOLD_OUT,主商品仍可购买").
 *
 * <p>Values:
 * <ul>
 *   <li>ACTIVE - 加料选项正常可选,可加入购物车</li>
 *   <li>SOLD_OUT - 加料 SKU 库存不足,UI 灰显;主商品仍可购买(不加该加料)</li>
 *   <li>DISABLED - 加料选项永久停用,不展示给顾客</li>
 * </ul>
 *
 * <p>State transitions:
 * <pre>
 *   ACTIVE ↔ SOLD_OUT (库存驱动)
 *   ACTIVE → DISABLED (运营主动停用)
 *   SOLD_OUT → DISABLED
 *   DISABLED → ACTIVE (运营恢复)
 * </pre>
 */
public enum AddonOptionStatusEnum {

    ACTIVE("ACTIVE", "可选"),
    SOLD_OUT("SOLD_OUT", "售罄"),
    DISABLED("DISABLED", "停用");

    private final String code;
    private final String label;

    AddonOptionStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static AddonOptionStatusEnum fromCode(String code) {
        Objects.requireNonNull(code, "addon option status code must not be null");
        for (AddonOptionStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown addon option status code: " + code);
    }

    public boolean canTransitionTo(AddonOptionStatusEnum target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case ACTIVE -> target == SOLD_OUT || target == DISABLED;
            case SOLD_OUT -> target == ACTIVE || target == DISABLED;
            case DISABLED -> target == ACTIVE;
        };
    }
}
