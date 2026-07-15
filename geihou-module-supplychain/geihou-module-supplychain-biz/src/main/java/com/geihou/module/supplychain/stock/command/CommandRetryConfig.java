package com.geihou.module.supplychain.stock.command;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Retry configuration for T2 loser-recovery polling.
 *
 * <p>Bound to prefix {@code geihou.supplychain.command.retry}. Defaults:
 * {@code maxAttempts=5}, {@code backoffMs={0, 20, 40, 80, 160}}.
 *
 * <p>Both the array getter and setter perform defensive copies. The
 * {@link #validate()} method runs at bean initialization and re-checks all
 * boundary constraints including length equality between {@code maxAttempts}
 * and {@code backoffMs}.
 */
@Component
@ConfigurationProperties(prefix = "geihou.supplychain.command.retry")
public class CommandRetryConfig {

    private int maxAttempts = 5;
    private long[] backoffMs = {0L, 20L, 40L, 80L, 160L};

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalStateException(
                    "maxAttempts must be >= 1 (got " + maxAttempts + ")");
        }
        this.maxAttempts = maxAttempts;
    }

    public long[] getBackoffMs() {
        return backoffMs.clone();
    }

    public void setBackoffMs(long[] backoffMs) {
        if (backoffMs == null || backoffMs.length == 0) {
            throw new IllegalStateException(
                    "backoffMs must be non-null and non-empty");
        }
        if (backoffMs[0] != 0L) {
            throw new IllegalStateException(
                    "backoffMs[0] must be 0 (first attempt no backoff)");
        }
        for (int i = 0; i < backoffMs.length; i++) {
            if (backoffMs[i] < 0) {
                throw new IllegalStateException(
                        "backoffMs[" + i + "] must be non-negative (got "
                                + backoffMs[i] + ")");
            }
        }
        this.backoffMs = backoffMs.clone();
    }

    public long getBackoffMs(int i) {
        if (i < 0 || i >= backoffMs.length) {
            throw new IllegalArgumentException(
                    "Attempt index out of range: " + i);
        }
        return backoffMs[i];
    }

    @PostConstruct
    public void validate() {
        if (maxAttempts < 1) {
            throw new IllegalStateException(
                    "maxAttempts must be >= 1 (got " + maxAttempts + ")");
        }
        if (backoffMs == null || backoffMs.length == 0) {
            throw new IllegalStateException(
                    "backoffMs must be non-null and non-empty");
        }
        if (maxAttempts != backoffMs.length) {
            throw new IllegalStateException(
                    "maxAttempts (" + maxAttempts
                            + ") must equal backoffMs.length ("
                            + backoffMs.length + ")");
        }
        if (backoffMs[0] != 0L) {
            throw new IllegalStateException(
                    "backoffMs[0] must be 0 (first attempt no backoff)");
        }
        for (int i = 0; i < backoffMs.length; i++) {
            if (backoffMs[i] < 0) {
                throw new IllegalStateException(
                        "backoffMs[" + i + "] must be non-negative (got "
                                + backoffMs[i] + ")");
            }
        }
    }
}
