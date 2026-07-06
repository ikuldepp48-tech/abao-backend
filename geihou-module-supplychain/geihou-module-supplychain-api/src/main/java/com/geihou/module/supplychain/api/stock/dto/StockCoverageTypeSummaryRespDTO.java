package com.geihou.module.supplychain.api.stock.dto;

/**
 * Per-coverage-type summary counts for the coverage report (G2-01B3C).
 */
public class StockCoverageTypeSummaryRespDTO {

    private String coverageType;
    private int total;
    private int unresolved;

    public StockCoverageTypeSummaryRespDTO() {
    }

    public StockCoverageTypeSummaryRespDTO(String coverageType, int total, int unresolved) {
        this.coverageType = coverageType;
        this.total = total;
        this.unresolved = unresolved;
    }

    public String getCoverageType() { return coverageType; }
    public void setCoverageType(String coverageType) { this.coverageType = coverageType; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getUnresolved() { return unresolved; }
    public void setUnresolved(int unresolved) { this.unresolved = unresolved; }
}
