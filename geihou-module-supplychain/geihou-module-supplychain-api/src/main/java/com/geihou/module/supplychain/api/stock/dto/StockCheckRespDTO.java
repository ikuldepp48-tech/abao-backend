package com.geihou.module.supplychain.api.stock.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for stock preflight check (G2-02C).
 *
 * <p>Contains an overall sufficiency flag and a per-component breakdown.
 *
 * <p>Source: TASK-G2-02C.
 */
public class StockCheckRespDTO {

    /** Tenant ID used for the check. */
    private Long tenantId;

    /** True if every component is SUFFICIENT; false if any is INSUFFICIENT or UNMAPPED. */
    private boolean allSufficient;

    /** Per-component results (one row per aggregated RAW_MATERIAL leaf). */
    private List<StockCheckItemResultDTO> items = new ArrayList<>();

    /** Subset of items whose status is UNMAPPED (no stock_item mapping found). */
    private List<StockCheckItemResultDTO> unmappedItems = new ArrayList<>();

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public boolean isAllSufficient() { return allSufficient; }
    public void setAllSufficient(boolean allSufficient) { this.allSufficient = allSufficient; }

    public List<StockCheckItemResultDTO> getItems() { return items; }
    public void setItems(List<StockCheckItemResultDTO> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public List<StockCheckItemResultDTO> getUnmappedItems() { return unmappedItems; }
    public void setUnmappedItems(List<StockCheckItemResultDTO> unmappedItems) {
        this.unmappedItems = unmappedItems != null ? unmappedItems : new ArrayList<>();
    }
}
