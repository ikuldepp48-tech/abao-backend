package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.StockCoverageGateResultRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageReportRespDTO;

/**
 * Read-only stock mapping coverage report and readiness gate service (G2-01B3C).
 *
 * <p>Turns B3B audit observations into a tenant-scoped report and gate decision.
 * All methods are strictly read-only: no audit rows or mapping rows are
 * inserted, updated, or deleted.
 */
public interface StockMappingCoverageReportService {

    /**
     * Generate a tenant-scoped coverage report.
     *
     * @param tenantId tenant scope; must not be null
     * @return report with summary counts, per-type counts, and capped unresolved details
     */
    StockCoverageReportRespDTO generateReport(Long tenantId);

    /**
     * Evaluate the readiness gate for a tenant based on current audit observations
     * and the configured coverage mode.
     *
     * @param tenantId tenant scope; must not be null
     * @return gate result with status and block reasons
     */
    StockCoverageGateResultRespDTO evaluateGate(Long tenantId);
}
