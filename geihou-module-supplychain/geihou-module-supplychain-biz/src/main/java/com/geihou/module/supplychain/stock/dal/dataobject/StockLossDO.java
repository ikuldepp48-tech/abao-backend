package com.geihou.module.supplychain.stock.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for stock_loss table.
 *
 * <p>Represents a loss/scrap record with approval workflow.
 * Low amount (≤ 1000) is auto-approved; high amount (> 1000) requires manual approval.
 * Stock is deducted through {@link com.geihou.module.supplychain.stock.service.StockEventService#recordEvent}
 * — never by directly modifying stock_balance.
 *
 * <p>Source: TASK-G2-02I-3, PRD-组2-02 节 2.1.
 */
@TableName("stock_loss")
public class StockLossDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 损耗单号 (租户内唯一) */
    private String lossNo;

    /** LOSS(损耗) / SCRAP(报废) */
    private String lossType;

    private Long stockItemId;
    private String skuCode;
    private Long locationId;

    /** 损耗数量 (正数) */
    private BigDecimal quantity;
    private String unit;

    /** 单位成本 (创建时从 stock_balance 读取) */
    private BigDecimal unitCost;
    /** 总金额 = quantity × unit_cost */
    private BigDecimal totalAmount;

    /** ENUM_LOSS_REASON */
    private String lossReason;
    /** 备注 (OTHER 原因时必填) */
    private String remark;

    /** PENDING_APPROVE / APPROVED / REJECTED / CANCELLED */
    private String status;

    private Long approverUserId;
    private LocalDateTime approveTime;
    private String rejectReason;

    /** 审批通过后写入的 stock_event ID */
    private Long stockEventId;

    private Long operatorUserId;

    // --- Audit ---
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;

    @TableLogic
    private Boolean deleted;

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

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getUpdater() { return updater; }
    public void setUpdater(String updater) { this.updater = updater; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
