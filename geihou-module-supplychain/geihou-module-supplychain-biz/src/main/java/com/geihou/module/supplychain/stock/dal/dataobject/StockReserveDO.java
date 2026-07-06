package com.geihou.module.supplychain.stock.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for stock_reserve table.
 *
 * <p>Records checkout_session/source_record_id level reservations with idempotency.
 * status is a table-internal status (RESERVED/RELEASED/COMMITTED), NOT a global ENUM_STOCK_EVENT_TYPE value.
 *
 * <p>Source: TASK-G2-01B1 Section 4.4 DDL.
 */
@TableName("stock_reserve")
public class StockReserveDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    // 预留关联
    private Long stockItemId;
    private Long locationId;
    private String skuCode;

    // 预留数量
    private BigDecimal quantity;
    private String unit;

    // 来源关联
    private String sourceModule;
    private Long sourceRecordId;
    private String referenceNo;

    // 幂等键
    private String idempotentKey;

    // 表内状态（非全局 ENUM_STOCK_EVENT_TYPE）
    private String status;

    // 关联事件（commit 时写入的 stock_event.id）
    private Long commitEventId;

    // 操作人
    private Long operatorUserId;

    // 审计
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

    public Long getStockItemId() { return stockItemId; }
    public void setStockItemId(Long stockItemId) { this.stockItemId = stockItemId; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getSourceModule() { return sourceModule; }
    public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }

    public Long getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(Long sourceRecordId) { this.sourceRecordId = sourceRecordId; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String idempotentKey) { this.idempotentKey = idempotentKey; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getCommitEventId() { return commitEventId; }
    public void setCommitEventId(Long commitEventId) { this.commitEventId = commitEventId; }

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
