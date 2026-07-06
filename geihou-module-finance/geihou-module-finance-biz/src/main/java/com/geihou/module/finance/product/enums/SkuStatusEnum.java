package com.geihou.module.finance.product.enums;

import java.util.Objects;

/**
 * SKU status enum (ENUM_SKU_STATUS).
 *
 * <p>Source of truth: Global Enum Table V2, Section B, ENUM_SKU_STATUS.
 * Values must match PRD-G1-02 Section 2.2 DDL (product_sku.status) and Section 4.1 state machine.
 *
 * <p>State machine:
 * <pre>
 *   NEW → ACTIVE → SOLD_OUT ↔ ACTIVE
 *                → PAUSED ↔ ACTIVE
 *                → DEPRECATED (terminal)
 *   SOLD_OUT → DEPRECATED (terminal)
 *   PAUSED → DEPRECATED (terminal)
 * </pre>
 */
public enum SkuStatusEnum {

    NEW("NEW", "Draft, not yet on sale"),
    ACTIVE("ACTIVE", "On sale, orderable"),
    SOLD_OUT("SOLD_OUT", "Sold out, UI greyed out, can resume when restocked"),
    PAUSED("PAUSED", "Temporarily off sale, can resume"),
    DEPRECATED("DEPRECATED", "Permanently retired, terminal state");

    private final String code;
    private final String label;

    SkuStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static SkuStatusEnum fromCode(String code) {
        Objects.requireNonNull(code, "status code must not be null");
        for (SkuStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown SKU status code: " + code);
    }

    public boolean canTransitionTo(SkuStatusEnum target) {
        return switch (this) {
            case NEW -> target == ACTIVE || target == DEPRECATED;
            case ACTIVE -> target == SOLD_OUT || target == PAUSED || target == DEPRECATED;
            case SOLD_OUT -> target == ACTIVE || target == DEPRECATED;
            case PAUSED -> target == ACTIVE || target == DEPRECATED;
            case DEPRECATED -> false; // terminal state
        };
    }
}
