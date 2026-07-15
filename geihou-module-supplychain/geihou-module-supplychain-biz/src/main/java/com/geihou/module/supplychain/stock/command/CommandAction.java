package com.geihou.module.supplychain.stock.command;

/**
 * Functional interface for the business action executed by {@link CommandExecutor}.
 *
 * <p>The action returns a real business result type {@code R} (e.g. {@code Long}
 * for reserve/commit event IDs, {@code null} for release, or a response DTO for
 * sales-out-bom-reverse / sales-reverse-restore / observe-missing-mapping).
 * The executor's codec adapts {@code R} to a {@link SnapshotV1.Root} for
 * journal persistence; {@code R} itself never crosses the persistence boundary.
 *
 * @param <R> the business result type returned by the action
 */
@FunctionalInterface
public interface CommandAction<R> {
    R run();
}
