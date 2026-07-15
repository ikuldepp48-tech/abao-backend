package com.geihou.module.supplychain.stock.command;

/**
 * Sleeper abstraction for T2 loser-recovery backoff pauses.
 *
 * <p>Single responsibility: provide a testable seam for backoff delays between
 * journal polls. Does not declare {@code throws InterruptedException};
 * interrupt restoration and {@link IllegalStateException} conversion are the
 * implementation's responsibility.
 */
public interface CommandSleeper {
    void sleepMillis(long ms);
}
