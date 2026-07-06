package com.geihou.module.supplychain.stock.enums;

import java.util.Objects;

/**
 * Loss reason enum (ENUM_LOSS_REASON).
 *
 * <p>Source of truth: Global Enum Table V2, ENUM_LOSS_REASON.
 * 8 values: EXPIRY / SPOILAGE / ACCIDENTAL_DAMAGE / EQUIPMENT_FAILURE /
 * PROCESS_LOSS / EMPLOYEE_ERROR / THEFT / OTHER.
 *
 * <p>This is a biz-layer local enum to avoid hardcoding strings.
 * It does NOT modify supplychain-api.
 */
public enum LossReasonEnum {

    EXPIRY("EXPIRY", "过期"),
    SPOILAGE("SPOILAGE", "变质"),
    ACCIDENTAL_DAMAGE("ACCIDENTAL_DAMAGE", "意外损坏"),
    EQUIPMENT_FAILURE("EQUIPMENT_FAILURE", "设备故障"),
    PROCESS_LOSS("PROCESS_LOSS", "工艺损耗"),
    EMPLOYEE_ERROR("EMPLOYEE_ERROR", "员工操作失误"),
    THEFT("THEFT", "失窃"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String label;

    LossReasonEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static LossReasonEnum fromCode(String code) {
        Objects.requireNonNull(code, "loss reason code must not be null");
        for (LossReasonEnum reason : values()) {
            if (reason.code.equals(code)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unknown loss reason code: " + code);
    }

    public static boolean isValidCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (LossReasonEnum reason : values()) {
            if (reason.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
