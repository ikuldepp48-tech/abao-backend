package com.geihou.module.supplychain.api.stock.enums;

import java.util.Objects;
import java.util.Set;

/**
 * Stock event type enum (ENUM_STOCK_EVENT_TYPE).
 *
 * <p>Source of truth: Global Enum Table V2, ENUM_STOCK_EVENT_TYPE.
 * CG-12-A: Uses the 12 values from the global enum table. PRD 7 values are void.
 * CG-12-B: Does NOT include RESERVE_OUT / RESERVE_RELEASE / RESERVE_COMMIT (rejected).
 * Reserve/release/commit deferred to G2-01B.
 *
 * <p>12 values: PURCHASE_IN / PRODUCTION_IN / TRANSFER_IN / RETURN_IN / CONSUME_OUT /
 * PRODUCTION_OUT / TRANSFER_OUT / COUNT_ADJUST / LOSS_OUT / SCRAP_OUT /
 * EXPIRY_OUT / RETURN_OUT
 */
public enum StockEventTypeEnum {

    PURCHASE_IN("PURCHASE_IN", "采购入库", "IN"),
    PRODUCTION_IN("PRODUCTION_IN", "制作产出入库", "IN"),
    TRANSFER_IN("TRANSFER_IN", "调拨入库", "IN"),
    RETURN_IN("RETURN_IN", "退料入库", "IN"),
    CONSUME_OUT("CONSUME_OUT", "BOM消耗出库", "OUT"),
    PRODUCTION_OUT("PRODUCTION_OUT", "制作原料出库", "OUT"),
    TRANSFER_OUT("TRANSFER_OUT", "调拨出库", "OUT"),
    COUNT_ADJUST("COUNT_ADJUST", "盘点调整", "INTERNAL"),
    LOSS_OUT("LOSS_OUT", "损耗出库", "OUT"),
    SCRAP_OUT("SCRAP_OUT", "报废出库", "OUT"),
    EXPIRY_OUT("EXPIRY_OUT", "过期出库", "OUT"),
    RETURN_OUT("RETURN_OUT", "退货出库", "OUT");

    private final String code;
    private final String label;
    private final String direction;

    StockEventTypeEnum(String code, String label, String direction) {
        this.code = code;
        this.label = label;
        this.direction = direction;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getDirection() {
        return direction;
    }

    public static StockEventTypeEnum fromCode(String code) {
        Objects.requireNonNull(code, "event type code must not be null");
        for (StockEventTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown stock event type code: " + code);
    }

    /**
     * All IN-direction event types (balance increases).
     */
    public static final Set<StockEventTypeEnum> IN_TYPES = Set.of(
            PURCHASE_IN, PRODUCTION_IN, TRANSFER_IN, RETURN_IN
    );

    /**
     * All OUT-direction event types (balance decreases).
     */
    public static final Set<StockEventTypeEnum> OUT_TYPES = Set.of(
            CONSUME_OUT, PRODUCTION_OUT, TRANSFER_OUT, LOSS_OUT, SCRAP_OUT, EXPIRY_OUT, RETURN_OUT
    );

    /**
     * All INTERNAL-direction event types (count adjustment).
     */
    public static final Set<StockEventTypeEnum> INTERNAL_TYPES = Set.of(
            COUNT_ADJUST
    );
}
