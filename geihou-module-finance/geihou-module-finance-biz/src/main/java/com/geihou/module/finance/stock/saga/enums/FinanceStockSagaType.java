package com.geihou.module.finance.stock.saga.enums;

/**
 * Type of saga that owns a durable finance stock command (FREEZE protocol section 4).
 *
 * <ul>
 *   <li>{@code CHECKOUT} - checkout flow (RESERVE / RELEASE / COMMIT /
 *       SALES_OUT_BOM_REVERSE / OBSERVE_MISSING_MAPPING).</li>
 *   <li>{@code REFUND} - refund flow (SALES_REVERSE_RESTORE).</li>
 * </ul>
 */
public enum FinanceStockSagaType {

    CHECKOUT,
    REFUND
}
