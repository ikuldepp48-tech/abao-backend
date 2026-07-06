package com.geihou.module.supplychain.stock.controller.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response VO for stock loss/scrap record.
 *
 * <p>Source: TASK-G2-02I-3.
 */
public class StockLossRespVO {

    private Long id;
    private Long tenantId;
    private String lossNo;
    private String lossType;
    private Long stockItemId;
    private String skuCode;
    private Long locationId;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitCost;
    private BigDecimal totalAmount;
    private String lossReason;
    private String remark;
    private String status;
    private Long approverUserId;
    private LocalDateTime approveTime;
    private String rejectReason;
    private Long stockEventId;
    private Long operatorUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getLossNo() { return lossNo; }
    public void setLossNo(String lossNo) { this.lossNo = lossNo; }

    public String getLossType() { return lossType; }
    public void setLossType(String lossType) { this.lossType = lossType; }

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

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getLossReason() { return lossReason; }
    public void setLossReason(String lossReason) { this.lossReason = lossReason; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getApproverUserId() { return approverUserId; }
    public void setApproverUserId(Long approverUserId) { this.approverUserId = approverUserId; }

    public LocalDateTime getApproveTime() { return approveTime; }
    public void setApproveTime(LocalDateTime approveTime) { this.approveTime = approveTime; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public Long getStockEventId() { return stockEventId; }
    public void setStockEventId(Long stockEventId) { this.stockEventId = stockEventId; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
