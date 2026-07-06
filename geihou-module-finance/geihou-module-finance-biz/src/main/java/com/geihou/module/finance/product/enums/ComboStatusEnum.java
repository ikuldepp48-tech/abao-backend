package com.geihou.module.finance.product.enums;

import java.util.Objects;

/**
 * 套餐状态 enum (ENUM_COMBO_STATUS).
 *
 * <p>Source of truth: Global Enum Table V2, Section B, ENUM_COMBO_STATUS.
 * Values must match PRD-G1-02 Section 2.2 DDL (product_combo.status) and
 * Section 4.3 exception handling ("套餐内某 SKU 已下架 → 套餐状态自动转 PAUSED + 通知老板").
 *
 * <p>Values:
 * <ul>
 *   <li>ACTIVE - 套餐正常可售,顾客可下单</li>
 *   <li>PAUSED - 套餐暂停销售(限时套餐到期 或 套餐内某 SKU 下架自动转 PAUSED)</li>
 *   <li>DEPRECATED - 套餐永久停用,记录保留供历史订单查询</li>
 * </ul>
 *
 * <p>State transitions:
 * <pre>
 *   ACTIVE → PAUSED (手动 或 SKU 下架联动)
 *   ACTIVE → DEPRECATED
 *   PAUSED → ACTIVE (手动恢复;PRD 未定义自动恢复路径,见 G1-02E R-2 / G1-02F R-4)
 *   PAUSED → DEPRECATED
 *   DEPRECATED - 终态
 * </pre>
 */
public enum ComboStatusEnum {

    ACTIVE("ACTIVE", "在售"),
    PAUSED("PAUSED", "暂停"),
    DEPRECATED("DEPRECATED", "永久下架");

    private final String code;
    private final String label;

    ComboStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static ComboStatusEnum fromCode(String code) {
        Objects.requireNonNull(code, "combo status code must not be null");
        for (ComboStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown combo status code: " + code);
    }

    public boolean canTransitionTo(ComboStatusEnum target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case ACTIVE -> target == PAUSED || target == DEPRECATED;
            case PAUSED -> target == ACTIVE || target == DEPRECATED;
            case DEPRECATED -> false; // terminal state
        };
    }
}
