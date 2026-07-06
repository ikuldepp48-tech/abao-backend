package com.geihou.module.supplychain.api.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for a single reconciliation item (one stockItem + location dimension).
 *
 * <p>Source: TASK-G2-02J §7.1.
 *
 * <p>Review note: includes explicit {@code inferredAdjustmentSign} (Integer nullable)
 * so that COUNT_ADJUST sign inference is transparent in the report output.
 * When no COUNT_ADJUST event is present in the dimension, this field is null.
 * When a COUNT_ADJUST event is present, this field holds the last inferred sign
 * (+1 surplus / -1 loss / 0 ambiguous). This is an <b>inferred</b> value derived
 * from {@code balance_after}, NOT a persisted field (see task package §4).
 *
 * <p>All quantity fields use BigDecimal (never double/float, H5 禁 9).
 * All time fields use LocalDateTime (never String, H5 禁 10).
 */
public class ReconcileItemDTO {

    private Long stockItemId;
    private String skuCode;
    private Long locationId;

    /** MATCH / MISMATCH / EVENT_WITHOUT_BALANCE / BALANCE_WITHOUT_EVENT */
    private String status;

    private BigDecimal expectedTotalQty;
    private BigDecimal expectedAvailableQty;
    private BigDecimal actualTotalQty;
    private BigDecimal actualAvailableQty;
    private BigDecimal actualReservedQty;

    /** expected - actual */
    private BigDecimal totalQtyDiff;
    /** expected - actual */
    private BigDecimal availableQtyDiff;

    private int eventCount;
    private Long lastEventId;
    private LocalDateTime lastEventTime;

    /**
     * Inferred adjustment sign for the last COUNT_ADJUST event in this dimension.
     * <p>+1 = surplus (盘盈), -1 = loss (盘亏), 0 = ambiguous, null = no COUNT_ADJUST.
     * <p>When persistedAdjustmentSign is non-null, this field equals the persisted value.
     * When persistedAdjustmentSign is null (legacy), this field holds the
     * balance_after diff inference result.
     */
    private Integer inferredAdjustmentSign;

    /**
     * Persisted adjustment sign from stock_event.adjustment_sign column (G2-02J-1).
     * <p>+1 = surplus, -1 = loss, null = legacy row (pre-G2-02J-1, not backfilled)
     * or no COUNT_ADJUST event in this dimension.
     * <p>When non-null, this is the authoritative value. When null,
     * {@code inferredAdjustmentSign} holds the fallback inference result.
     */
    private Integer persistedAdjustmentSign;

    private boolean hasAmbiguousSign;
    private List<String> inferenceWarnings;

    /**
     * BOM line trace audit fields (G2-02W-30).
     *
     * <p>These fields are diagnostic only: they do not change balance math or
     * MATCH/MISMATCH status. They surface whether sales BOM consume/restore
     * events in this stock dimension can be audited at source_order_item_id
     * granularity.
     */
    private int lineTraceableEventCount;
    private int lineTraceMissingEventCount;
    private int lineRestoreEventCount;
    private int historicalNullRestoreEventCount;
    private boolean hasLineTraceGap;
    private List<String> lineTraceWarnings;

    // --- Getters and Setters ---

    public Long getStockItemId() { return stockItemId; }
    public void setStockItemId(Long stockItemId) { this.stockItemId = stockItemId; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getExpectedTotalQty() { return expectedTotalQty; }
    public void setExpectedTotalQty(BigDecimal expectedTotalQty) { this.expectedTotalQty = expectedTotalQty; }

    public BigDecimal getExpectedAvailableQty() { return expectedAvailableQty; }
    public void setExpectedAvailableQty(BigDecimal expectedAvailableQty) { this.expectedAvailableQty = expectedAvailableQty; }

    public BigDecimal getActualTotalQty() { return actualTotalQty; }
    public void setActualTotalQty(BigDecimal actualTotalQty) { this.actualTotalQty = actualTotalQty; }

    public BigDecimal getActualAvailableQty() { return actualAvailableQty; }
    public void setActualAvailableQty(BigDecimal actualAvailableQty) { this.actualAvailableQty = actualAvailableQty; }

    public BigDecimal getActualReservedQty() { return actualReservedQty; }
    public void setActualReservedQty(BigDecimal actualReservedQty) { this.actualReservedQty = actualReservedQty; }

    public BigDecimal getTotalQtyDiff() { return totalQtyDiff; }
    public void setTotalQtyDiff(BigDecimal totalQtyDiff) { this.totalQtyDiff = totalQtyDiff; }

    public BigDecimal getAvailableQtyDiff() { return availableQtyDiff; }
    public void setAvailableQtyDiff(BigDecimal availableQtyDiff) { this.availableQtyDiff = availableQtyDiff; }

    public int getEventCount() { return eventCount; }
    public void setEventCount(int eventCount) { this.eventCount = eventCount; }

    public Long getLastEventId() { return lastEventId; }
    public void setLastEventId(Long lastEventId) { this.lastEventId = lastEventId; }

    public LocalDateTime getLastEventTime() { return lastEventTime; }
    public void setLastEventTime(LocalDateTime lastEventTime) { this.lastEventTime = lastEventTime; }

    public Integer getInferredAdjustmentSign() { return inferredAdjustmentSign; }
    public void setInferredAdjustmentSign(Integer inferredAdjustmentSign) { this.inferredAdjustmentSign = inferredAdjustmentSign; }

    public Integer getPersistedAdjustmentSign() { return persistedAdjustmentSign; }
    public void setPersistedAdjustmentSign(Integer persistedAdjustmentSign) { this.persistedAdjustmentSign = persistedAdjustmentSign; }

    public boolean isHasAmbiguousSign() { return hasAmbiguousSign; }
    public void setHasAmbiguousSign(boolean hasAmbiguousSign) { this.hasAmbiguousSign = hasAmbiguousSign; }

    public List<String> getInferenceWarnings() { return inferenceWarnings; }
    public void setInferenceWarnings(List<String> inferenceWarnings) { this.inferenceWarnings = inferenceWarnings; }

    public int getLineTraceableEventCount() { return lineTraceableEventCount; }
    public void setLineTraceableEventCount(int lineTraceableEventCount) { this.lineTraceableEventCount = lineTraceableEventCount; }

    public int getLineTraceMissingEventCount() { return lineTraceMissingEventCount; }
    public void setLineTraceMissingEventCount(int lineTraceMissingEventCount) { this.lineTraceMissingEventCount = lineTraceMissingEventCount; }

    public int getLineRestoreEventCount() { return lineRestoreEventCount; }
    public void setLineRestoreEventCount(int lineRestoreEventCount) { this.lineRestoreEventCount = lineRestoreEventCount; }

    public int getHistoricalNullRestoreEventCount() { return historicalNullRestoreEventCount; }
    public void setHistoricalNullRestoreEventCount(int historicalNullRestoreEventCount) { this.historicalNullRestoreEventCount = historicalNullRestoreEventCount; }

    public boolean isHasLineTraceGap() { return hasLineTraceGap; }
    public void setHasLineTraceGap(boolean hasLineTraceGap) { this.hasLineTraceGap = hasLineTraceGap; }

    public List<String> getLineTraceWarnings() { return lineTraceWarnings; }
    public void setLineTraceWarnings(List<String> lineTraceWarnings) { this.lineTraceWarnings = lineTraceWarnings; }
}
