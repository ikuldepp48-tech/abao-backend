package com.geihou.module.supplychain.api.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for stock balance queries.
 *
 * <p>Used by {@link com.geihou.module.supplychain.api.stock.StockEventApi#getAvailableQty}.
 * All quantity fields use BigDecimal (never double/float, H5 禁 9).
 */
public class StockBalanceRespDTO {

    private Long tenantId;
    private Long stockItemId;
    private Long locationId;
    private BigDecimal availableQty;
    private BigDecimal totalQty;
    private BigDecimal reservedQty;
    private BigDecimal avgUnitCost;
    private Long lastEventId;
    private LocalDateTime lastEventTime;

    // --- Getters and Setters ---

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
}
