package com.geihou.module.supplychain.stock.command;

import com.geihou.module.supplychain.api.stock.enums.SupplychainCommandOperationEnum;

/**
 * Executor for finance&rarr;supplychain write commands with journal-backed
 * idempotency.
 *
 * <p>The executor guarantees that each {@code (tenant, operation, businessCommandId)}
 * triple is executed at most once. On T1, the executor first checks the journal.
 * Only when no record exists does it run the action and insert the result in the
 * same transaction. If a concurrent writer wins the unique-key insert, T2 loser
 * recovery polls the journal with bounded retry and either replays the winner's
 * snapshot (same request hash) or signals an idempotent conflict (different hash).
 */
public interface CommandExecutor {

    /**
     * Execute a write command with journal-backed idempotency.
     *
     * @param operation           the operation type (one of 6 finance&rarr;supplychain writes)
     * @param businessCommandId   client-supplied idempotency key (maxLength 128)
     * @param trustedBodySha256Hex trusted lowercase SHA-256 body hash obtained
     *                            from the verified HMAC context (64 hex chars)
     * @param action              the business action; called at most once in T1,
     *                            never called in T2
     * @param <R>                 the business result type
     * @return command result carrying the first-win or replayed business value
     */
    <R> CommandResult<R> execute(
            SupplychainCommandOperationEnum operation,
            String businessCommandId,
            String trustedBodySha256Hex,
            CommandAction<R> action);
}
