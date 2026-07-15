package com.geihou.module.supplychain.stock.command;

import org.springframework.stereotype.Component;

/**
 * Default {@link CommandSleeper} implementation backed by {@link Thread#sleep}.
 *
 * <p>Non-positive arguments return immediately. On interrupt, the interrupt
 * status is restored and an {@link IllegalStateException} is thrown.
 * This interruption path is never converted to
 * {@code CONSISTENCY_INTERNAL_ERROR} (2002065).
 */
@Component
public class SystemCommandSleeper implements CommandSleeper {

    @Override
    public void sleepMillis(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Command sleeper interrupted", e);
        }
    }
}
