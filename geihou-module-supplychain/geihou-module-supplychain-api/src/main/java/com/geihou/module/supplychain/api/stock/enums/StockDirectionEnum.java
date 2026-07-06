package com.geihou.module.supplychain.api.stock.enums;

import java.util.Objects;

/**
 * Stock direction enum: IN (入) / OUT (出) / INTERNAL (内部).
 *
 * <p>IN: balance increases (e.g. PURCHASE_IN, PRODUCTION_IN, TRANSFER_IN)
 * OUT: balance decreases (e.g. CONSUME_OUT, LOSS_OUT, SCRAP_OUT)
 * INTERNAL: count adjustment (COUNT_ADJUST) — balance update deferred to G2-01C
 */
public enum StockDirectionEnum {

    IN("IN", "入库"),
    OUT("OUT", "出库"),
    INTERNAL("INTERNAL", "内部调整");

    private final String code;
    private final String label;

    StockDirectionEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static StockDirectionEnum fromCode(String code) {
        Objects.requireNonNull(code, "direction code must not be null");
        for (StockDirectionEnum dir : values()) {
            if (dir.code.equals(code)) {
                return dir;
            }
        }
        throw new IllegalArgumentException("Unknown stock direction code: " + code);
    }
}
