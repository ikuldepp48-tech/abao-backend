package com.geihou.module.supplychain.stock.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for stock_balance table.
 *
 * <p>Balance is computed from events (不可裸改). The "no raw change" rule is
 * enforced at the service layer (StockBalanceService has no setAvailableQty/setTotalQty).
 * The DO setters exist for internal initialization and MyBatis-Plus mapping only.
 * All quantity/amount fields use BigDecimal (never double/float, H5 禁 9).
 * Optimistic locking via @Version field.
 *
 * <p>Source: TASK-G2-01A Section 4.2 DDL.
 */
@TableName("stock_balance")
public class StockBalanceDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long stockItemId;
    private Long locationId;

    // Balance (BigDecimal, never double/float)
    private BigDecimal availableQty;
    private BigDecimal totalQty;
    private BigDecimal reservedQty;
    private BigDecimal avgUnitCost;

    // Optimistic lock
    private Long lastEventId;
    private LocalDateTime lastEventTime;
    @Version
    private Integer version;

    // Threshold (G2-01B, fields reserved)
    private BigDecimal minThreshold;
    private BigDecimal maxThreshold;

    // Audit
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

    public BigDecimal getAvailableQty() { return availableQty; }
    public void setAvailableQty(BigDecimal availableQty) { this.availableQty = availableQty; }

    public BigDecimal getTotalQty() { return totalQty; }
    public void setTotalQty(BigDecimal totalQty) { this.totalQty = totalQty; }

    public BigDecimal getReservedQty() { return reservedQty; }
    public void setReservedQty(BigDecimal reservedQty) { this.reservedQty = reservedQty; }

    public BigDecimal getAvgUnitCost() { return avgUnitCost; }
    public void setAvgUnitCost(BigDecimal avgUnitCost) { this.avgUnitCost = avgUnitCost; }

    public Long getLastEventId() { return lastEventId; }
    public void setLastEventId(Long lastEventId) { this.lastEventId = lastEventId; }

    public LocalDateTime getLastEventTime() { return lastEventTime; }
    public void setLastEventTime(LocalDateTime lastEventTime) { this.lastEventTime = lastEventTime; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public BigDecimal getMinThreshold() { return minThreshold; }
    public void setMinThreshold(BigDecimal minThreshold) { this.minThreshold = minThreshold; }

    public BigDecimal getMaxThreshold() { return maxThreshold; }
    public void setMaxThreshold(BigDecimal maxThreshold) { this.maxThreshold = maxThreshold; }

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
