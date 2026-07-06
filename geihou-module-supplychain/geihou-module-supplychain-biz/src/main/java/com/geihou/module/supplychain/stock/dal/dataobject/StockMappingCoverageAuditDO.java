package com.geihou.module.supplychain.stock.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Data object for stock_mapping_coverage_audit.
 *
 * <p>This table records missing mapping observations only. It is not a
 * stock_item / stock_location mapping source of truth.
 */
@TableName("stock_mapping_coverage_audit")
public class StockMappingCoverageAuditDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
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
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;

    @TableLogic
    private Boolean deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
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
