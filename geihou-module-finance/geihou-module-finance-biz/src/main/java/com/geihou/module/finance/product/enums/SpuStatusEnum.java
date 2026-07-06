package com.geihou.module.finance.product.enums;

import java.util.Objects;

/**
 * SPU status enum (ENUM_SPU_STATUS).
 *
 * <p>Source of truth: Global Enum Table V2, Section B, ENUM_SPU_STATUS.
 * Values must match PRD-G1-02 Section 2.2 DDL (product_spu.status) and Section 4.1 state machine.
 *
 * <p>State machine:
 * <pre>
 *   NEW → ACTIVE → PAUSED ↔ ACTIVE
 *                  → DEPRECATED (terminal)
 *   PAUSED → DEPRECATED (terminal)
 * </pre>
 */
public enum SpuStatusEnum {

    NEW("NEW", "Draft, not yet on sale"),
    ACTIVE("ACTIVE", "On sale, visible to customers"),
    PAUSED("PAUSED", "Temporarily off sale, can resume"),
    DEPRECATED("DEPRECATED", "Permanently retired, terminal state");

    private final String code;
    private final String label;

    SpuStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static SpuStatusEnum fromCode(String code) {
        Objects.requireNonNull(code, "status code must not be null");
        for (SpuStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown SPU status code: " + code);
    }

    public boolean canTransitionTo(SpuStatusEnum target) {
        return switch (this) {
            case NEW -> target == ACTIVE || target == DEPRECATED;
            case ACTIVE -> target == PAUSED || target == DEPRECATED;
            case PAUSED -> target == ACTIVE || target == DEPRECATED;
            case DEPRECATED -> false; // terminal state
        };
    }
}
