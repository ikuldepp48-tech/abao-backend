package com.geihou.module.finance.product.mq.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when a SKU price changes.
 *
 * <p>Topic: product.price.changed
 * <p>Producer: PriceService.changePrice (after-commit)
 * <p>Consumers (not in this slice): marketing module (PRD-G9-01), data platform (PRD-G8-01)
 *
 * <p>All price fields use BigDecimal (never double/float).
 * changeType uses ENUM_PRICE_CHANGE_TYPE: MANUAL/PROMOTION/COST_BASED/MARKET_BASED.
 *
 * <p>This is a transitional in-process event (Spring ApplicationEvent).
 * When real MQ infrastructure (geihou-spring-boot-starter-mq) is ready,
 * the ProductEventPublisherImpl will be replaced to publish to the real MQ transport.
 * The event class and publisher interface remain unchanged.
 */
public class ProductPriceChangedEvent {

    private Long tenantId;
    private Long skuId;
    private Long spuId;
    private BigDecimal oldSellingPrice;
    private BigDecimal newSellingPrice;
    private BigDecimal oldListPrice;
    private BigDecimal newListPrice;
    private String changeType;
    private String changeReason;
    private Long changedByUserId;
    private LocalDateTime changeTime;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public Long getSpuId() { return spuId; }
    public void setSpuId(Long spuId) { this.spuId = spuId; }

    public BigDecimal getOldSellingPrice() { return oldSellingPrice; }
    public void setOldSellingPrice(BigDecimal oldSellingPrice) { this.oldSellingPrice = oldSellingPrice; }

    public BigDecimal getNewSellingPrice() { return newSellingPrice; }
    public void setNewSellingPrice(BigDecimal newSellingPrice) { this.newSellingPrice = newSellingPrice; }

    public BigDecimal getOldListPrice() { return oldListPrice; }
    public void setOldListPrice(BigDecimal oldListPrice) { this.oldListPrice = oldListPrice; }

    public BigDecimal getNewListPrice() { return newListPrice; }
    public void setNewListPrice(BigDecimal newListPrice) { this.newListPrice = newListPrice; }

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    public Long getChangedByUserId() { return changedByUserId; }
    public void setChangedByUserId(Long changedByUserId) { this.changedByUserId = changedByUserId; }

    public LocalDateTime getChangeTime() { return changeTime; }
    public void setChangeTime(LocalDateTime changeTime) { this.changeTime = changeTime; }
}
