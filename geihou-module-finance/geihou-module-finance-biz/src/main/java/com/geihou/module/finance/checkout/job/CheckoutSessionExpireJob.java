package com.geihou.module.finance.checkout.job;

import com.geihou.module.finance.checkout.service.CheckoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Checkout session expire job.
 *
 * <p>G1-04E: Scans for INITIATED checkout sessions past their expire_time (5 min per PRD §4.1/§4.2)
 * and transitions them to EXPIRED. Unlocks the cart (CHECKOUT → ACTIVE) and writes a
 * CHECKOUT_ABANDONED event log with operator_role = SYSTEM.
 *
 * <p>This replaces the lazy-only expiry (which only triggers on query/pay) with proactive cleanup.
 * The job delegates to {@link CheckoutService#expireOverdueSessions()} which handles cross-tenant
 * iteration and per-row transaction safety.
 *
 * <p>Run rate: every 60 seconds (matching OrderExpireJob pattern).
 * Operator: SYSTEM (operatorUserId = 0L, operatorRole = "SYSTEM").
 */
@Component
public class CheckoutSessionExpireJob {

    private static final Logger log = LoggerFactory.getLogger(CheckoutSessionExpireJob.class);

    private final CheckoutService checkoutService;

    public CheckoutSessionExpireJob(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    /**
     * Run every 60 seconds to expire overdue INITIATED checkout sessions.
     */
    @Scheduled(fixedRate = 60000)
    public void execute() {
        try {
            checkoutService.expireOverdueSessions();
        } catch (Exception e) {
            log.error("CheckoutSessionExpireJob failed", e);
        }
    }
}
