package com.geihou.module.finance.stock.saga;

import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockCommandDO;

import java.time.LocalDateTime;

/**
 * Durable command store for finance->supplychain write commands
 * (G0-04H185-FIN-CONSISTENCY slice 1).
 *
 * <p>Single-table CAS store backed by {@code finance_stock_command}. Provides
 * state-machine transitions per FREEZE protocol section 5: claim fencing with
 * random tokens, lease expiry to UNKNOWN, UNKNOWN resolution, retry scheduling,
 * and abort handling.
 *
 * <p>All methods are tenant-scoped: every SQL includes {@code tenant_id}.
 * Write methods are single conditional UPDATEs; stale/missing claim tokens
 * return {@code false} rather than falling through to unconditional updates.
 *
 * <p>Public API surface is frozen by the task spec. Methods that expect
 * {@code IN_FLIGHT + claimToken} are the dispatch-result writes. Package-private
 * {@code *FromUnknown} variants are the UNKNOWN resolution-result writes
 * (expected status = UNKNOWN) and are not part of the public API.
 */
public interface FinanceStockCommandStore {

    /**
     * Create a new PENDING command or return the existing one if the same
     * immutable identity already exists.
     *
     * <p>Identity is checked on both remote identity
     * (tenantId, operation, businessCommandId) and local step
     * (tenantId, sagaType, sagaId, stepKey, operation). If either exists,
     * all immutable fields must match exactly - otherwise
     * {@link IllegalStateException} is thrown. If both exist but point to
     * different records, {@link IllegalStateException} is thrown.
     *
     * <p>Validates that {@code requestBodySha256} is 64-char lowercase hex
     * and matches SHA-256 of {@code requestBody}. Rejects
     * {@code LOCAL_API_V1 + c0JournalAvailable=true}.
     */
    FinanceStockCommandDO createOrGet(FinanceStockCommandCreate command);

    /**
     * CAS-claim a PENDING or due RETRY_WAIT command for dispatch.
     * Transitions to IN_FLIGHT, increments dispatchAttempts, sets claim token
     * and lease. Returns {@code false} if the row is not claimable or the
     * claim token is stale.
     */
    boolean claimDispatch(long tenantId, long id, String claimToken,
                          LocalDateTime now, LocalDateTime leaseUntil);

    /**
     * Record successful remote execution. Expects IN_FLIGHT + claimToken.
     * Transitions to SUCCEEDED, stores result, clears lease/error.
     */
    boolean completeSuccess(long tenantId, long id, String claimToken,
                            byte[] resultBody, Integer resultSchemaVersion,
                            LocalDateTime remoteExecutedAt, LocalDateTime now);

    /**
     * Schedule a retry. Expects IN_FLIGHT + claimToken. Transitions to
     * RETRY_WAIT, sets nextAttemptAt, records error, clears claim/lease.
     */
    boolean scheduleRetry(long tenantId, long id, String claimToken,
                          LocalDateTime nextAttemptAt, Integer errorCode,
                          String errorClass, String errorMessage, LocalDateTime now);

    /**
     * Mark the result as UNKNOWN (e.g. timeout, connection lost). Expects
     * IN_FLIGHT + claimToken. Transitions to UNKNOWN, sets nextAttemptAt,
     * records error, clears claim/lease.
     */
    boolean markUnknown(long tenantId, long id, String claimToken,
                        LocalDateTime nextAttemptAt, Integer errorCode,
                        String errorClass, String errorMessage, LocalDateTime now);

    /**
     * Batch-expire all IN_FLIGHT commands whose lease has passed. Transitions
     * to UNKNOWN, clears token/lease. Never transitions back to PENDING.
     *
     * @return number of rows expired
     */
    int expireLeasesToUnknown(long tenantId, LocalDateTime now,
                              LocalDateTime nextAttemptAt);

    /**
     * CAS-claim an UNKNOWN command for resolution. Status stays UNKNOWN;
     * replaces claim token/lease, increments resolutionAttempts.
     *
     * <p>Only claims UNKNOWN commands where {@code next_attempt_at <= now}
     * and either {@code claim_token IS NULL} or {@code lease_until < now}.
     */
    boolean claimResolution(long tenantId, long id, String claimToken,
                            LocalDateTime now, LocalDateTime leaseUntil);

    /**
     * Record confirmed no-effect (remote did not commit). Expects
     * IN_FLIGHT + claimToken. Transitions to NO_EFFECT.
     */
    boolean completeNoEffect(long tenantId, long id, String claimToken,
                             Integer errorCode, String errorClass,
                             String errorMessage, LocalDateTime now);

    /**
     * Mark as STUCK (auto-recovery halted, needs human evidence). Expects
     * IN_FLIGHT + claimToken. Transitions to STUCK.
     */
    boolean markStuck(long tenantId, long id, String claimToken,
                      Integer errorCode, String errorClass,
                      String errorMessage, LocalDateTime now);

    /**
     * Request abort for a command.
     *
     * <ul>
     *   <li>PENDING/RETRY_WAIT with no claim -> CANCELLED + abortRequested=true.</li>
     *   <li>IN_FLIGHT/UNKNOWN -> status unchanged, only abortRequested=true.</li>
     *   <li>Terminal -> idempotent {@code true}, no business result change.</li>
     * </ul>
     */
    boolean requestAbort(long tenantId, long id, LocalDateTime now);
}
