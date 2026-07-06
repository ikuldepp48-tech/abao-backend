package com.geihou.module.supplychain.api.stock.dto;

import java.math.BigDecimal;

/**
 * Request DTO for reserving stock.
 *
 * <p>Used by {@link com.geihou.module.supplychain.api.stock.StockEventApi#reserveStock}.
 * All quantity fields use BigDecimal (never double/float, H5 禁 9).
 */
public class StockReserveReqDTO {
    private Long tenantId;
    private Long stockItemId;
    private Long locationId;
    private String skuCode;
    private BigDecimal quantity;          // 预留数量（正数）
    private String unit;
    private String sourceModule;          // "checkout" / "order" / "manual"
    private Long sourceRecordId;          // checkout_session.id 等
    private String referenceNo;
    private String idempotentKey;         // 幂等键（必填）
    private Long operatorUserId;

    // --- Getters and Setters ---

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

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
}
