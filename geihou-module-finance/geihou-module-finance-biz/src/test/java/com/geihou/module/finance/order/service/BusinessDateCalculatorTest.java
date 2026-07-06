package com.geihou.module.finance.order.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BusinessDateCalculator test.
 *
 * <p>Tests cross-day cutoff boundary cases per PRD-组1-01 Section 4.4.
 */
class BusinessDateCalculatorTest {

    private final BusinessDateCalculator calculator = new BusinessDateCalculator();

    @Test
    void orderAt2359Cutoff3BelongsToToday() {
        LocalDateTime time = LocalDateTime.of(2026, 6, 1, 23, 59);
        LocalDate businessDate = calculator.computeBusinessDate(time, 3);
        assertThat(businessDate).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void orderAt0101Cutoff3BelongsToYesterday() {
        LocalDateTime time = LocalDateTime.of(2026, 6, 2, 1, 1);
        LocalDate businessDate = calculator.computeBusinessDate(time, 3);
        assertThat(businessDate).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void orderAt0300Cutoff3BelongsToToday() {
        // Hour == cutoff → belongs to today (>= cutoff)
        LocalDateTime time = LocalDateTime.of(2026, 6, 2, 3, 0);
        LocalDate businessDate = calculator.computeBusinessDate(time, 3);
        assertThat(businessDate).isEqualTo(LocalDate.of(2026, 6, 2));
    }

    @Test
    void orderAt0259Cutoff3BelongsToYesterday() {
        // Hour < cutoff → belongs to yesterday
        LocalDateTime time = LocalDateTime.of(2026, 6, 2, 2, 59);
        LocalDate businessDate = calculator.computeBusinessDate(time, 3);
        assertThat(businessDate).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void orderAt0000Cutoff3BelongsToYesterday() {
        LocalDateTime time = LocalDateTime.of(2026, 6, 2, 0, 0);
        LocalDate businessDate = calculator.computeBusinessDate(time, 3);
        assertThat(businessDate).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void orderAt2359Cutoff0BelongsToToday() {
        // Cutoff 0 means all hours >= 0 belong to today
        LocalDateTime time = LocalDateTime.of(2026, 6, 1, 23, 59);
        LocalDate businessDate = calculator.computeBusinessDate(time, 0);
        assertThat(businessDate).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void orderAt0000Cutoff0BelongsToToday() {
        LocalDateTime time = LocalDateTime.of(2026, 6, 2, 0, 0);
        LocalDate businessDate = calculator.computeBusinessDate(time, 0);
        assertThat(businessDate).isEqualTo(LocalDate.of(2026, 6, 2));
    }

    @Test
    void defaultCutoffHourIs3WhenNull() {
        LocalDateTime time = LocalDateTime.of(2026, 6, 2, 1, 30);
        LocalDate businessDate = calculator.computeBusinessDate(time);
        assertThat(businessDate).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void invalidCutoffHourFallsBackToDefault3() {
        LocalDateTime time = LocalDateTime.of(2026, 6, 2, 1, 30);
        // cutoff -1 or 24 should fall back to 3
        LocalDate businessDate = calculator.computeBusinessDate(time, -1);
        assertThat(businessDate).isEqualTo(LocalDate.of(2026, 6, 1));

        LocalDate businessDate2 = calculator.computeBusinessDate(time, 24);
        assertThat(businessDate2).isEqualTo(LocalDate.of(2026, 6, 1));
    }
}
