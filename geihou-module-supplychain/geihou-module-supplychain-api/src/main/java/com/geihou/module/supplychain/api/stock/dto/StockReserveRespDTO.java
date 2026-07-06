package com.geihou.module.supplychain.api.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for stock reservation queries.
 *
 * <p>All quantity fields use BigDecimal (never double/float, H5 禁 9).
 */
public class StockReserveRespDTO {
    private Long reserveId;
    private Long tenantId;
    private Long stockItemId;
    private Long locationId;
    private BigDecimal quantity;
    private String status;               // RESERVED / RELEASED / COMMITTED
    private String idempotentKey;
    private Long commitEventId;          // commit 后的 stock_event.id（commit 前为 null）
    private LocalDateTime createTime;

    // --- Getters and Setters ---

    public Long getReserveId() { return reserveId; }
    public void setReserveId(Long reserveId) { this.reserveId = reserveId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getStockItemId() { return stockItemId; }
    public void setStockItemId(Long stockItemId) { this.stockItemId = stockItemId; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String idempotentKey) { this.idempotentKey = idempotentKey; }

    public Long getCommitEventId() { return commitEventId; }
    public void setCommitEventId(Long commitEventId) { this.commitEventId = commitEventId; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
