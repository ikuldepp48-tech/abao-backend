package com.geihou.module.supplychain.stock.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data object for stock_event table.
 *
 * <p>INSERT-only (不可篡改). No update_time / updater / deleted columns.
 * No @Version annotation (immutable after insert).
 * All quantity/amount fields use BigDecimal (never double/float, H5 禁 9).
 * All time fields use LocalDateTime / LocalDate (never String, H5 禁 10).
 *
 * <p>Source: TASK-G2-01A Section 4.1 DDL.
 */
@TableName("stock_event")
public class StockEventDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private LocalDateTime eventTime;
    private LocalDate businessDate;

    // Event core
    private String eventType;
    private String direction;

    // Stock item + location
    private Long stockItemId;
    private String skuCode;
    private Long locationId;

    // Quantity (BigDecimal, always positive)
    private BigDecimal quantity;
    private String unit;

    // Cost (optional in G2-01A)
    private BigDecimal unitCost;
    private BigDecimal totalCost;

    // Source / reference
    private String sourceModule;
    private Long sourceRecordId;
    private Long sourceOrderItemId;
    private String referenceNo;

    // Idempotency
    private String clientRequestId;

    // Operator
    private Long operatorUserId;

    // Balance snapshot (redundant)
    private BigDecimal balanceAfter;

    // INSERT-only: only create_time, no update_time / updater / deleted
    private LocalDateTime createTime;

    // BOM reverse consumption fields (G2-02D-pre, nullable, no business logic yet)
    private Long parentEventId;
    private Long recipeId;
    private Integer recipeVersion;

    // Adjustment reason for COUNT_ADJUST/ADJUSTMENT events (G2-02I-2, PRD §5)
    // VARCHAR(512), required when event_type is ADJUSTMENT or COUNT_ADJUST (>=30 chars)
    private String adjustmentReason;

    /**
     * Adjustment sign for COUNT_ADJUST events (G2-02J-1).
     * <p>+1 = 盘盈 (surplus, balance increases);
     * -1 = 盘亏 (loss, balance decreases).
     * Null for IN/OUT direction events and legacy COUNT_ADJUST rows
     * (pre-G2-02J-1, before backfill).
     */
    private Integer adjustmentSign;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public Long getParentEventId() { return parentEventId; }
    public void setParentEventId(Long parentEventId) { this.parentEventId = parentEventId; }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public Integer getRecipeVersion() { return recipeVersion; }
    public void setRecipeVersion(Integer recipeVersion) { this.recipeVersion = recipeVersion; }

    public String getAdjustmentReason() { return adjustmentReason; }
    public void setAdjustmentReason(String adjustmentReason) { this.adjustmentReason = adjustmentReason; }

    public Integer getAdjustmentSign() { return adjustmentSign; }
    public void setAdjustmentSign(Integer adjustmentSign) { this.adjustmentSign = adjustmentSign; }
}
