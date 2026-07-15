package com.geihou.module.supplychain.stock.command;

/**
 * Result container for {@link CommandExecutor#execute}.
 *
 * <p>Carries the adapted business value {@code R} (not a persistence snapshot).
 * {@code SnapshotV1.Root} only exists at the journal persistence boundary
 * inside the executor; this public type is decoupled from it.
 *
 * <p>{@code value} may be {@code null} (e.g. RELEASE operation returns
 * {@code void/null}).
 *
 * <p>{@link #isReplay()} distinguishes a newly executed first win
 * ({@code false}) from a journal replay ({@code true}), whether the replay
 * is found during the T1 pre-check or T2 loser recovery.
 *
 * @param <R> the business result type
 */
public final class CommandResult<R> {
    private final R value;
    private final boolean replay;

    private CommandResult(R value, boolean replay) {
        this.value = value;
        this.replay = replay;
    }

    public static <R> CommandResult<R> firstWin(R value) {
        return new CommandResult<>(value, false);
    }

    public static <R> CommandResult<R> replay(R value) {
        return new CommandResult<>(value, true);
    }

    public R value() {
        return value;
    }

    public boolean isReplay() {
        return replay;
    }
}
