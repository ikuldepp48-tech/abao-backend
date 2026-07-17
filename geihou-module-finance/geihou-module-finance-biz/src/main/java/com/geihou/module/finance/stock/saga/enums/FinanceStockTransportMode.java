package com.geihou.module.finance.stock.saga.enums;

/**
 * Transport mode of a durable finance stock command (FREEZE protocol section 3).
 *
 * <ul>
 *   <li>{@code LOCAL_API_V1} - current same-JVM API direct call.
 *       Must NOT be combined with {@code c0_journal_available=true}.</li>
 *   <li>{@code HMAC_RPC_V1} - future real HTTP HMAC RPC.
 *       May be combined with {@code c0_journal_available=false} for T_c0
 *       pre-deployment rehearsal.</li>
 * </ul>
 */
public enum FinanceStockTransportMode {

    LOCAL_API_V1,
    HMAC_RPC_V1
}
