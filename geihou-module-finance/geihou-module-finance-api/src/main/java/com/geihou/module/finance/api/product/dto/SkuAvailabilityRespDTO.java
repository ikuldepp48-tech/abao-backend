package com.geihou.module.finance.api.product.dto;

/**
 * SKU availability response DTO for the ProductApi.checkAvailability contract.
 *
 * <p>The {@code available} field indicates whether the SKU is purchasable based on
 * status (SOLD_OUT/PAUSED/DEPRECATED → false) and per-order limit.
 * It does NOT verify stock quantity — consumers must call StockApi for stock checks.
 * stockStrategy=UNLIMITED means no stock verification at all.
 */
public class SkuAvailabilityRespDTO {

    private Long skuId;
    private Boolean available;
    private String reason;
    private Integer requestedQuantity;
    private Integer maxAllowedQuantity;

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Integer getRequestedQuantity() { return requestedQuantity; }
    public void setRequestedQuantity(Integer requestedQuantity) { this.requestedQuantity = requestedQuantity; }

    public Integer getMaxAllowedQuantity() { return maxAllowedQuantity; }
    public void setMaxAllowedQuantity(Integer maxAllowedQuantity) { this.maxAllowedQuantity = maxAllowedQuantity; }
}
