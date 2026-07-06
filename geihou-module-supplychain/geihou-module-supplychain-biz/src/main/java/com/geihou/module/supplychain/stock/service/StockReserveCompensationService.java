package com.geihou.module.supplychain.stock.service;

import java.util.List;

/**
 * Compensation service for timed-out orphan stock reservations.
 *
 * <p>Scans for stock_reserve records that are still RESERVED beyond a configurable
 * timeout threshold and releases them by delegating to {@link StockReserveService#releaseStock}.
 *
 * <p>Conservative semantics:
 * <ul>
 *   <li>Only releases records with status = RESERVED and create_time &lt; cutoff</li>
 *   <li>Skips COMMITTED / RELEASED records (handled by releaseStock CAS)</li>
 *   <li>Idempotent: repeated scans do not double-release (releaseStock is idempotent)</li>
 *   <li>Single record failure does not abort the batch</li>
 * </ul>
 *
 * <p>Source: TASK-G2-01B4.
 */
public interface StockReserveCompensationService {

    /**
     * Result of a compensation scan batch.
     */
    class CompensationResult {
        private final int scanned;
        private final int released;
        private final int skipped;
        private final int failed;
        private final List<String> failureDetails;

        public CompensationResult(int scanned, int released, int skipped, int failed,
                                   List<String> failureDetails) {
            this.scanned = scanned;
            this.released = released;
            this.skipped = skipped;
            this.failed = failed;
            this.failureDetails = failureDetails != null ? failureDetails : List.of();
        }

        public int getScanned() { return scanned; }
        public int getReleased() { return released; }
        public int getSkipped() { return skipped; }
        public int getFailed() { return failed; }
        public List<String> getFailureDetails() { return failureDetails; }
    }

    /**
     * Execute one compensation scan batch.
     *
     * <p>Scans for timed-out RESERVED records and releases them via
     * {@link StockReserveService#releaseStock}. Each release is independent;
     * failure on one record does not abort the batch.
     *
     * @return compensation result summary
     */
    CompensationResult executeCompensation();
}
