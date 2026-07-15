package com.geihou.module.supplychain.stock.command;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Default {@link CommandClock} implementation backed by the system clock.
 *
 * <p>Returns {@link LocalDateTime#now()} without timezone conversion.
 * The journal {@code executedAt} field is represented as {@link LocalDateTime}.
 */
@Component
public class SystemCommandClock implements CommandClock {

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
