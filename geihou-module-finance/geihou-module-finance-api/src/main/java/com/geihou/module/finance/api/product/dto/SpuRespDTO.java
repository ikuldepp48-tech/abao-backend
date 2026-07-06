package com.geihou.module.finance.api.product.dto;

/**
 * SPU response DTO for the ProductApi contract.
 *
 * <p>Fields per PRD-G1-02 Section 3.3 DTO definitions.
 * Status uses ENUM_SPU_STATUS: NEW/ACTIVE/PAUSED/DEPRECATED.
 * spuType uses ENUM_PRODUCT_TYPE: FINISHED/SEMI_FINISHED/RAW_MATERIAL/COMBO/SERVICE.
 */
public class SpuRespDTO {

    private Long id;
    private Long tenantId;
    private String spuCode;
    private String spuName;
    private String spuShortName;
    private Long categoryId;
    private String spuType;
    private String primaryImageUrl;
    private String description;
    private Boolean isRecommended;
    private Boolean isNewArrival;
    private Integer sortOrder;
    private Integer totalSoldCount;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getSpuCode() { return spuCode; }
    public void setSpuCode(String spuCode) { this.spuCode = spuCode; }

    public String getSpuName() { return spuName; }
    public void setSpuName(String spuName) { this.spuName = spuName; }

    public String getSpuShortName() { return spuShortName; }
    public void setSpuShortName(String spuShortName) { this.spuShortName = spuShortName; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getSpuType() { return spuType; }
    public void setSpuType(String spuType) { this.spuType = spuType; }

    public String getPrimaryImageUrl() { return primaryImageUrl; }
    public void setPrimaryImageUrl(String primaryImageUrl) { this.primaryImageUrl = primaryImageUrl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getIsRecommended() { return isRecommended; }
    public void setIsRecommended(Boolean isRecommended) { this.isRecommended = isRecommended; }

    public Boolean getIsNewArrival() { return isNewArrival; }
    public void setIsNewArrival(Boolean isNewArrival) { this.isNewArrival = isNewArrival; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Integer getTotalSoldCount() { return totalSoldCount; }
    public void setTotalSoldCount(Integer totalSoldCount) { this.totalSoldCount = totalSoldCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
