package com.geihou.module.finance.product.controller.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Price history response VO.
 */
public class PriceHistoryRespVO {

    private Long id;
    private Long skuId;
    private BigDecimal oldListPrice;
    private BigDecimal newListPrice;
    private BigDecimal oldSellingPrice;
    private BigDecimal newSellingPrice;
    private String changeReason;
    private String changeType;
    private Long changedByUserId;
    private LocalDateTime changeTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
}
