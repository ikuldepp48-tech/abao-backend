package com.geihou.module.supplychain.api.stock.dto;

import com.geihou.module.supplychain.api.stock.enums.StockCoverageTypeEnum;

/**
 * Request DTO for stock mapping coverage observations.
 */
public class StockCoverageObserveReqDTO {

    private Long tenantId;
    private StockCoverageTypeEnum coverageType;
    private Long skuId;
    private String skuCode;
    private Long storeId;
    private String locationType;
    private String sourceModule;
    private Long sourceRecordId;
    private String idempotentKey;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public StockCoverageTypeEnum getCoverageType() { return coverageType; }
    public void setCoverageType(StockCoverageTypeEnum coverageType) { this.coverageType = coverageType; }

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
}
