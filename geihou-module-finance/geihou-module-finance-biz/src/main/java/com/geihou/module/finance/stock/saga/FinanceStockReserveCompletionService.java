package com.geihou.module.finance.stock.saga;

import java.time.LocalDateTime;

/**
 * Atomic completion of a RESERVE command with optional same-transaction
 * RELEASE child creation when {@code abort_requested} was persisted before
 * success.
 *
 * <p>G0-04H185-FIN-CONSISTENCY slice 2A: atomic reserve/release primitive.
 *
 * <p>Both methods execute within a single database transaction:
 * <ol>
 *   <li>CAS the parent RESERVE to SUCCEEDED (dispatch or resolution variant).</li>
 *   <li>Re-read the parent to inspect {@code abort_requested}.</li>
 *   <li>If {@code abort_requested=false}, return {@code true} without creating
 *       a RELEASE.</li>
 *   <li>If {@code abort_requested=true}, validate and create the RELEASE child
 *       via {@link FinanceStockCommandStore#createOrGet} in the same
 *       transaction. Any exception from createOrGet propagates and rolls back
 *       the parent's SUCCEEDED transition.</li>
 * </ol>
 *
 * <p>Returns {@code false} when the state or claim token is stale; no RELEASE
 * is created in that case. Returns {@code true} when the CAS succeeded and
 * the transaction completes (with or without a RELEASE child).
 *
 * <p>This slice handles only the "abort requested before success" branch.
 * It does NOT handle post-success cancel races, checkout finalizer, or
 * reconciliation.
 */
public interface FinanceStockReserveCompletionService {

    /**
     * Complete a RESERVE from the dispatch path. Requires the parent to be
     * IN_FLIGHT with the given dispatch claim token.
     */
    boolean completeFromDispatch(
            long tenantId,
            long reserveCommandId,
            String claimToken,
            byte[] resultBody,
            Integer resultSchemaVersion,
            LocalDateTime remoteExecutedAt,
            LocalDateTime now,
            FinanceStockCommandCreate releaseCommand);

    /**
     * Complete a RESERVE from the UNKNOWN resolution path. Requires the parent
     * to be UNKNOWN with the given resolution claim token.
     */
    boolean completeFromResolution(
            long tenantId,
            long reserveCommandId,
            String claimToken,
            byte[] resultBody,
            Integer resultSchemaVersion,
            LocalDateTime remoteExecutedAt,
            LocalDateTime now,
            FinanceStockCommandCreate releaseCommand);
}
