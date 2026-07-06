package com.geihou.module.supplychain.api.stock.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for the reconciliation report response.
 *
 * <p>Source: TASK-G2-02J §7.1.
 *
 * <p>Contains aggregate counts and per-dimension items. The report is produced
 * by recomputing expected balance from stock_event and comparing against
 * stock_balance. The entire operation is read-only.
 *
 * <p>All time fields use LocalDateTime (never String, H5 禁 10).
 */
public class ReconcileReportRespDTO {

    private Long tenantId;
    private LocalDateTime reconcileTime;

    /** Total (stockItem, location) dimensions checked */
    private int totalDimensions;
    /** Dimensions where expected == actual */
    private int matchedCount;
    /** Dimensions where expected != actual */
    private int mismatchedCount;
    /** Dimensions with stock_event rows but no stock_balance row */
    private int eventsWithoutBalanceCount;
    /** Dimensions with stock_balance row but no stock_event rows */
    private int balancesWithoutEventsCount;
    /** COUNT_ADJUST events where sign inference was ambiguous */
    private int ambiguousSignCount;
    /** Dimensions containing BOM events that cannot be audited by source_order_item_id */
    private int lineTraceGapCount;

    private List<ReconcileItemDTO> items;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public LocalDateTime getReconcileTime() { return reconcileTime; }
    public void setReconcileTime(LocalDateTime reconcileTime) { this.reconcileTime = reconcileTime; }

    public int getTotalDimensions() { return totalDimensions; }
    public void setTotalDimensions(int totalDimensions) { this.totalDimensions = totalDimensions; }

    public int getMatchedCount() { return matchedCount; }
    public void setMatchedCount(int matchedCount) { this.matchedCount = matchedCount; }

    public int getMismatchedCount() { return mismatchedCount; }
    public void setMismatchedCount(int mismatchedCount) { this.mismatchedCount = mismatchedCount; }

    public int getEventsWithoutBalanceCount() { return eventsWithoutBalanceCount; }
    public void setEventsWithoutBalanceCount(int eventsWithoutBalanceCount) { this.eventsWithoutBalanceCount = eventsWithoutBalanceCount; }

    public int getBalancesWithoutEventsCount() { return balancesWithoutEventsCount; }
    public void setBalancesWithoutEventsCount(int balancesWithoutEventsCount) { this.balancesWithoutEventsCount = balancesWithoutEventsCount; }

    public int getAmbiguousSignCount() { return ambiguousSignCount; }
    public void setAmbiguousSignCount(int ambiguousSignCount) { this.ambiguousSignCount = ambiguousSignCount; }

    public int getLineTraceGapCount() { return lineTraceGapCount; }
    public void setLineTraceGapCount(int lineTraceGapCount) { this.lineTraceGapCount = lineTraceGapCount; }

    public List<ReconcileItemDTO> getItems() { return items; }
    public void setItems(List<ReconcileItemDTO> items) { this.items = items; }
}
