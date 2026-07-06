package com.geihou.module.finance.product.controller.admin.vo;

import java.math.BigDecimal;

/**
 * SPU creation request VO.
 *
 * <p>Field names match PRD-G1-02 Section 3.2 OpenAPI SpuCreateReq schema.
 * spuType uses FINISHED (not NORMAL) per G1-02C Codex ruling.
 */
public class SpuCreateReqVO {

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
}
