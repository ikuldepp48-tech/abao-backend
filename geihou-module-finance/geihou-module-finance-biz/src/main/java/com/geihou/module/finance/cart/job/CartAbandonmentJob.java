package com.geihou.module.finance.cart.job;

import com.geihou.module.finance.cart.service.CartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cart abandonment job.
 *
 * <p>G1-04E: Scans for ACTIVE carts with last_activity_time older than 24 hours (PRD §4.1)
 * and transitions them to ABANDONED. Writes a CART_EXPIRED event log with operator_role = SYSTEM.
 * Cart items are NOT modified — they remain for historical reference.
 *
 * <p>The job delegates to {@link CartService#abandonOldCarts()} which handles cross-tenant
 * iteration and per-row transaction safety.
 *
 * <p>Run rate: every 10 minutes (24h threshold does not need minute-level granularity).
 * Operator: SYSTEM (operatorUserId = 0L, operatorRole = "SYSTEM").
 */
@Component
public class CartAbandonmentJob {

    private static final Logger log = LoggerFactory.getLogger(CartAbandonmentJob.class);

    private final CartService cartService;

    public CartAbandonmentJob(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * Run every 10 minutes to abandon old ACTIVE carts.
     */
    @Scheduled(fixedRate = 600000)
    public void execute() {
        try {
            cartService.abandonOldCarts();
        } catch (Exception e) {
            log.error("CartAbandonmentJob failed", e);
        }
    }
}
