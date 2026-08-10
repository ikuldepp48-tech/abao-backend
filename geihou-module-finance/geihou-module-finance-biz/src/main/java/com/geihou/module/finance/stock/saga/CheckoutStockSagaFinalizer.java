package com.geihou.module.finance.stock.saga;

import com.geihou.module.finance.stock.saga.enums.FinanceStockSagaType;

/**
 * Terminalizes one abandoned/expired/failed checkout saga in a single
 * transaction (G0-04H185 FIN-CONSISTENCY slice 2C-2D, section 7).
 *
 * <p>Same-transaction sequence performed by {@code finalizeSaga}:
 * <ol>
 *   <li>CAS the {@code checkout_session} to its terminal status
 *       ({@code expected -> target}, e.g. {@code INITIATED -> ABANDONED}).</li>
 *   <li>Unlock the customer cart ({@code CHECKOUT -> ACTIVE}).</li>
 *   <li>Insert a {@code cart_event_log} row (cart event audit).</li>
 *   <li>CAS the saga intent {@code PENDING -> FINALIZED}.</li>
 * </ol>
 *
 * <p>Race semantics (two workers terminalizing the same saga):
 * <ul>
 *   <li>The session CAS is the synchronization point. After a {@code 0}-row
 *       CAS, the finalizer performs current reads and returns {@code false}
 *       only when a committed winner left both a FINALIZED intent and the
 *       requested session target; any other state is an inconsistency.</li>
 *   <li>The single winner performs unlock + event insert + intent finalize.
 *       If its own intent CAS returns {@code 0} (concurrent finalizer already
 *       marked FINALIZED), it throws and the whole transaction rolls back
 *       (event + unlock are undone).</li>
 *   <li>An already-FINALIZED intent returns {@code false} without touching
 *       the session.</li>
 * </ul>
 *
 * <p>Fail-closed: non-{@code CHECKOUT} saga type, missing session, cart/session
 * mismatch, or an unknown {@code finalizationStatus} all throw before any
 * side effect is applied.
 */
public interface CheckoutStockSagaFinalizer {

    /**
     * Terminalize the checkout saga in one transaction.
     *
     * @param tenantId the tenant scope (must match the tenant context)
     * @param sagaType must be {@link FinanceStockSagaType#CHECKOUT}
     * @param sagaId   the {@code checkout_session.id}
     * @return {@code true} if this worker performed the terminalization,
     *         {@code false} if another worker already committed the matching
     *         finalization, or the intent was already FINALIZED
     */
    boolean finalizeSaga(long tenantId, FinanceStockSagaType sagaType, long sagaId);
}
