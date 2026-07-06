package com.geihou.module.finance.order.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Business date calculator for cross-day cutoff.
 *
 * <p>Rule (PRD-组1-01 Section 4.4):
 * 1. Get tenant's business_day_cutoff_hour (default 3, i.e., 3 AM).
 * 2. If order create time's hour < cutoff_hour → business_date = previous day.
 * 3. If order create time's hour >= cutoff_hour → business_date = same day.
 *
 * <p>business_date is determined once at CREATE time and never changed.
 * TenantApi.getBusinessDayCutoffHour() may not be available yet (R-8),
 * so cutoff_hour defaults to 3 if TenantApi is unavailable.
 */
@Component
public class BusinessDateCalculator {

    /**
     * Default cutoff hour when TenantApi is unavailable.
     */
    public static final int DEFAULT_CUTOFF_HOUR = 3;

    /**
     * Compute business date based on physical create time and cutoff hour.
     *
     * @param createTime  physical order creation time
     * @param cutoffHour  business day cutoff hour (0-23), defaults to 3 if null
     * @return computed business date
     */
    public LocalDate computeBusinessDate(LocalDateTime createTime, Integer cutoffHour) {
        Objects.requireNonNull(createTime, "createTime must not be null");
        int cutoff = (cutoffHour == null) ? DEFAULT_CUTOFF_HOUR : cutoffHour;
        if (cutoff < 0 || cutoff > 23) {
            cutoff = DEFAULT_CUTOFF_HOUR;
        }

        LocalDate physicalDate = createTime.toLocalDate();
        if (createTime.getHour() < cutoff) {
            return physicalDate.minusDays(1);
        } else {
            return physicalDate;
        }
    }

    /**
     * Compute business date with default cutoff hour (3).
     * Used when TenantApi.getBusinessDayCutoffHour() is not available.
     *
     * @param createTime physical order creation time
     * @return computed business date
     */
    public LocalDate computeBusinessDate(LocalDateTime createTime) {
        return computeBusinessDate(createTime, DEFAULT_CUTOFF_HOUR);
    }
}
