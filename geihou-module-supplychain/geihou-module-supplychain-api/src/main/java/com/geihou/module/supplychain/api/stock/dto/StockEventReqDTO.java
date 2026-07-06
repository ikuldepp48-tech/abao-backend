package com.geihou.module.supplychain.api.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Request DTO for recording a stock event.
 *
 * <p>Used by {@link com.geihou.module.supplychain.api.stock.StockEventApi#recordEvent(StockEventReqDTO)}.
 * All quantity/amount fields use BigDecimal (never double/float, H5 禁 9).
 */
public class StockEventReqDTO {

    private Long tenantId;
    private LocalDateTime eventTime;
    private LocalDate businessDate;

    /** ENUM_STOCK_EVENT_TYPE (global enum table 12 values, CG-12-A) */
    private String eventType;
    /** IN / OUT / INTERNAL */
    private String direction;

    private Long stockItemId;
    private String skuCode;
    private Long locationId;

    /** Quantity (always positive, direction determines add/subtract) */
    private BigDecimal quantity;
    private String unit;

    /** Unit cost (optional in G2-01A, deferred to G2-01B) */
    private BigDecimal unitCost;
    private BigDecimal totalCost;

    private String sourceModule;
    private Long sourceRecordId;
    private Long sourceOrderItemId;
    private String referenceNo;

    /** Client request ID for idempotency (nullable) */
    private String clientRequestId;

    private Long operatorUserId;

    // BOM reverse consumption fields (G2-02D-pre, nullable, no validation)
    private Long parentEventId;
    private Long recipeId;
    private Integer recipeVersion;

    /**
     * Adjustment sign for INTERNAL direction (G2-02I-2A).
     * <p>+1 = 盘盈 (surplus, balance increases);
     * -1 = 盘亏 (loss, balance decreases).
     * Null defaults to +1. Only meaningful for INTERNAL direction;
     * ignored for IN / OUT.
     */
    private Integer adjustmentSign;

    /**
     * Adjustment reason for COUNT_ADJUST events (G2-02I-2, Review Fix #3).
     * <p>PRD §5 requires ADJUSTMENT events to carry an adjustment_reason >= 30 chars.
     * Set by StockCountServiceImpl.approveCount from stock_count_record.diff_reason.
     * Minimal field addition to supplychain-api per task package allowance.
     */
    private String adjustmentReason;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public Long getStockItemId() { return stockItemId; }
    public void setStockItemId(Long stockItemId) { this.stockItemId = stockItemId; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

    public String getSourceModule() { return sourceModule; }
    public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }

    public Long getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(Long sourceRecordId) { this.sourceRecordId = sourceRecordId; }

    public Long getSourceOrderItemId() { return sourceOrderItemId; }
    public void setSourceOrderItemId(Long sourceOrderItemId) { this.sourceOrderItemId = sourceOrderItemId; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    public Long getParentEventId() { return parentEventId; }
    public void setParentEventId(Long parentEventId) { this.parentEventId = parentEventId; }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public Integer getRecipeVersion() { return recipeVersion; }
    public void setRecipeVersion(Integer recipeVersion) { this.recipeVersion = recipeVersion; }

    public Integer getAdjustmentSign() { return adjustmentSign; }
    public void setAdjustmentSign(Integer adjustmentSign) { this.adjustmentSign = adjustmentSign; }

    public String getAdjustmentReason() { return adjustmentReason; }
    public void setAdjustmentReason(String adjustmentReason) { this.adjustmentReason = adjustmentReason; }
}
