package com.geihou.module.finance.product.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for product_price_history table.
 *
 * <p>INSERT-only table. No update/delete operations are allowed.
 * This DO intentionally has no 'deleted' field and no @TableLogic annotation.
 * All price fields use BigDecimal (never double/float).
 */
@TableName("product_price_history")
public class ProductPriceHistoryDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long skuId;

    private BigDecimal oldListPrice;
    private BigDecimal newListPrice;
    private BigDecimal oldSellingPrice;
    private BigDecimal newSellingPrice;

    private String changeReason;
    private String changeType;
    private Long changedByUserId;

    private LocalDateTime changeTime;
    private LocalDateTime createTime;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public BigDecimal getOldListPrice() { return oldListPrice; }
    public void setOldListPrice(BigDecimal oldListPrice) { this.oldListPrice = oldListPrice; }

    public BigDecimal getNewListPrice() { return newListPrice; }
    public void setNewListPrice(BigDecimal newListPrice) { this.newListPrice = newListPrice; }

    public BigDecimal getOldSellingPrice() { return oldSellingPrice; }
    public void setOldSellingPrice(BigDecimal oldSellingPrice) { this.oldSellingPrice = oldSellingPrice; }

    public BigDecimal getNewSellingPrice() { return newSellingPrice; }
    public void setNewSellingPrice(BigDecimal newSellingPrice) { this.newSellingPrice = newSellingPrice; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }

    public Long getChangedByUserId() { return changedByUserId; }
    public void setChangedByUserId(Long changedByUserId) { this.changedByUserId = changedByUserId; }

    public LocalDateTime getChangeTime() { return changeTime; }
    public void setChangeTime(LocalDateTime changeTime) { this.changeTime = changeTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
