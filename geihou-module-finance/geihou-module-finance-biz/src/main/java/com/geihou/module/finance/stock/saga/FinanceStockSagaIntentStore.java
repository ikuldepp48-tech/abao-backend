package com.geihou.module.finance.stock.saga;

import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockSagaIntentDO;
import com.geihou.module.finance.stock.saga.enums.FinanceStockSagaType;

import java.time.LocalDateTime;

/**
 * Durable saga intent store for checkout terminalization
 * (G0-04H185 FIN-CONSISTENCY slice 2C-2D).
 *
 * <p>One {@code finance_stock_saga_intent} row per saga identity
 * {@code (tenantId, sagaType, sagaId)}. The intent freezes all
 * terminalization parameters; the finalizer consumes it.
 *
 * <p>All methods are tenant-scoped: every SQL includes {@code tenant_id},
 * and every public method first verifies that
 * {@link com.geihou.framework.tenant.core.context.TenantContextHolder#getTenantId()}
 * matches the {@code tenantId} parameter (fail-closed before any SQL).
 *
 * <p>{@code finalizationStatus} is a two-value string state machine:
 * {@link #FINALIZATION_STATUS_PENDING} -> {@link #FINALIZATION_STATUS_FINALIZED}.
 */
public interface FinanceStockSagaIntentStore {

    /** Intent not yet terminalized (awaiting the finalizer). */
    String FINALIZATION_STATUS_PENDING = FinanceStockSagaIntentDO.FINALIZATION_STATUS_PENDING;

    /** Intent already terminalized (finalizer completed). */
    String FINALIZATION_STATUS_FINALIZED = FinanceStockSagaIntentDO.FINALIZATION_STATUS_FINALIZED;

    /**
     * Create a new PENDING intent or return the existing one for the same
     * {@code (tenantId, sagaType, sagaId)} identity.
     *
     * <p>If a row already exists, its frozen fields must match exactly -
     * otherwise {@link IllegalStateException} is thrown. Uses the unique-key
     * constraint {@code uk_fsi_identity} as the race synchronization point
     * and re-selects on {@code DuplicateKeyException}.
     *
     * <p>The returned intent (existing or freshly created) is always
     * re-validated against {@link FinanceStockSagaIntentCreate#validate}
     * before returning.
     */
    FinanceStockSagaIntentDO createOrGet(FinanceStockSagaIntentCreate create);

    /**
     * Select the intent for a saga identity, returning {@code null} when
     * none exists. The returned intent is always re-validated before
     * returning (guards against dirty rows).
     */
    FinanceStockSagaIntentDO selectByIdentity(long tenantId,
                                              FinanceStockSagaType sagaType,
                                              long sagaId);

    /**
     * Select the latest committed intent and lock it for the current
     * transaction. Used only by finalizer loser reconciliation after a failed
     * session CAS; callers must already be in a transaction when lock lifetime
     * matters. Duplicate-insert reconciliation deliberately uses a shared
     * current read inside the store implementation to avoid lock-upgrade
     * deadlocks between concurrent losers.
     */
    FinanceStockSagaIntentDO selectByIdentityForUpdate(long tenantId,
                                                       FinanceStockSagaType sagaType,
                                                       long sagaId);

    /**
     * CAS the intent from PENDING to FINALIZED.
     *
     * <p>Returns {@code true} only when the mapper affected exactly one row.
     * A {@code false} return means the intent was already finalized (or does
     * not exist) - it never updates an already-finalized intent.
     */
    boolean finalizePending(long tenantId, FinanceStockSagaType sagaType,
                            long sagaId, LocalDateTime now);
}
