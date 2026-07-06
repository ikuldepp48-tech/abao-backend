package com.geihou.module.finance.product.controller.admin.vo;

/**
 * SPU update request VO.
 */
public class SpuUpdateReqVO {

    private Long id;
    private String spuName;
    private String spuShortName;
    private Long categoryId;
    private String primaryImageUrl;
    private String description;
    private Boolean isRecommended;
    private Boolean isNewArrival;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSpuName() { return spuName; }
    public void setSpuName(String spuName) { this.spuName = spuName; }

    public String getSpuShortName() { return spuShortName; }
    public void setSpuShortName(String spuShortName) { this.spuShortName = spuShortName; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

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
