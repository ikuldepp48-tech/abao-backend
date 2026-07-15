package com.geihou.module.supplychain.stock.command;

import java.time.LocalDateTime;

/**
 * Clock abstraction for sourcing {@code executed_at} timestamps written to the
 * command journal.
 *
 * <p>Single responsibility: provide a testable seam for
 * {@code supplychain_command_journal.executed_at}. Does not participate in
 * serialization or transaction management.
 */
public interface CommandClock {
    LocalDateTime now();
}
