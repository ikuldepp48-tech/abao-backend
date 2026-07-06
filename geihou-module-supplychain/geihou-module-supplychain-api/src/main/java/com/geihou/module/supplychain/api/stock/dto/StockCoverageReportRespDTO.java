package com.geihou.module.supplychain.api.stock.dto;

import com.geihou.module.supplychain.api.stock.enums.StockCoverageModeEnum;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Stock mapping coverage report (G2-01B3C).
 *
 * <p>Tenant-scoped read-only report containing:
 * <ul>
 *   <li>tenant ID and generated time</li>
 *   <li>current coverage mode (AUDIT_ONLY / ENFORCE)</li>
 *   <li>summary counts (total observations, total unresolved)</li>
 *   <li>per-coverage-type counts</li>
 *   <li>capped list of unresolved detail rows</li>
 * </ul>
 */
public class StockCoverageReportRespDTO {

    private Long tenantId;
    private LocalDateTime generatedTime;
    private StockCoverageModeEnum mode;
    private int totalObservations;
    private int totalUnresolved;
    private List<StockCoverageTypeSummaryRespDTO> typeSummaries;
    private List<StockCoverageDetailRespDTO> unresolvedDetails;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public LocalDateTime getGeneratedTime() { return generatedTime; }
    public void setGeneratedTime(LocalDateTime generatedTime) { this.generatedTime = generatedTime; }

    public StockCoverageModeEnum getMode() { return mode; }
    public void setMode(StockCoverageModeEnum mode) { this.mode = mode; }

    public int getTotalObservations() { return totalObservations; }
    public void setTotalObservations(int totalObservations) { this.totalObservations = totalObservations; }

    public int getTotalUnresolved() { return totalUnresolved; }
    public void setTotalUnresolved(int totalUnresolved) { this.totalUnresolved = totalUnresolved; }

    public List<StockCoverageTypeSummaryRespDTO> getTypeSummaries() { return typeSummaries; }
    public void setTypeSummaries(List<StockCoverageTypeSummaryRespDTO> typeSummaries) { this.typeSummaries = typeSummaries; }

    public List<StockCoverageDetailRespDTO> getUnresolvedDetails() { return unresolvedDetails; }
    public void setUnresolvedDetails(List<StockCoverageDetailRespDTO> unresolvedDetails) { this.unresolvedDetails = unresolvedDetails; }
}
