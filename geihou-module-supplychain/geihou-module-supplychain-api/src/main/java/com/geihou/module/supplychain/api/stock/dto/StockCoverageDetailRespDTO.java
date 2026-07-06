package com.geihou.module.supplychain.api.stock.dto;

import java.time.LocalDateTime;

/**
 * Individual unresolved coverage observation detail (G2-01B3C).
 *
 * <p>Mirrors the key fields of {@code stock_mapping_coverage_audit} for a single
 * observation that is still unresolved (no matching canonical mapping exists).
 */
public class StockCoverageDetailRespDTO {

    private String coverageType;
    private Long skuId;
    private String skuCode;
    private Long storeId;
    private String locationType;
    private String sourceModule;
    private Long sourceRecordId;
    private String idempotentKey;
    private String mode;
    private LocalDateTime firstSeenTime;
    private LocalDateTime lastSeenTime;
    private Integer seenCount;

    public String getCoverageType() { return coverageType; }
    public void setCoverageType(String coverageType) { this.coverageType = coverageType; }

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public String getLocationType() { return locationType; }
    public void setLocationType(String locationType) { this.locationType = locationType; }

    public String getSourceModule() { return sourceModule; }
    public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }

    public Long getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(Long sourceRecordId) { this.sourceRecordId = sourceRecordId; }

    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String idempotentKey) { this.idempotentKey = idempotentKey; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public LocalDateTime getFirstSeenTime() { return firstSeenTime; }
    public void setFirstSeenTime(LocalDateTime firstSeenTime) { this.firstSeenTime = firstSeenTime; }

    public LocalDateTime getLastSeenTime() { return lastSeenTime; }
    public void setLastSeenTime(LocalDateTime lastSeenTime) { this.lastSeenTime = lastSeenTime; }

    public Integer getSeenCount() { return seenCount; }
    public void setSeenCount(Integer seenCount) { this.seenCount = seenCount; }
}
