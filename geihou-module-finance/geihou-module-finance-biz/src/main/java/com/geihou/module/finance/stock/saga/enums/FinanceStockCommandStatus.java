package com.geihou.module.finance.stock.saga.enums;

/**
 * Status of a durable finance stock command (G0-04H185-FIN-CONSISTENCY slice 1).
 *
 * <p>Frozen state machine per FREEZE protocol section 5:
 * <ul>
 *   <li>{@code PENDING} - never claimed, awaiting first dispatch (non-terminal)</li>
 *   <li>{@code IN_FLIGHT} - claimed, remote call may be executing (non-terminal)</li>
 *   <li>{@code RETRY_WAIT} - confirmed no remote effect, retry after backoff (non-terminal)</li>
 *   <li>{@code UNKNOWN} - remote success unknown, must be resolved (non-terminal)</li>
 *   <li>{@code SUCCEEDED} - remote success result durable (terminal)</li>
 *   <li>{@code NO_EFFECT} - confirmed remote did not commit, no auto retry (terminal)</li>
 *   <li>{@code CANCELLED} - aborted before first dispatch, never called remote (terminal)</li>
 *   <li>{@code STUCK} - auto-recovery halted, requires human evidence (terminal)</li>
 * </ul>
 *
 * <p>Generalized {@code FAILED} is intentionally absent. Every failure must be
 * classified into {@code RETRY_WAIT}, {@code UNKNOWN}, {@code NO_EFFECT}, or
 * {@code STUCK}. Adding {@code FAILED} or {@code RESOLVING} is prohibited.
 */
public enum FinanceStockCommandStatus {

    PENDING,
    IN_FLIGHT,
    RETRY_WAIT,
    UNKNOWN,
    SUCCEEDED,
    NO_EFFECT,
    CANCELLED,
    STUCK;

    /**
     * Whether this status is a terminal state (no further automatic transitions).
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == NO_EFFECT
                || this == CANCELLED || this == STUCK;
    }
}
