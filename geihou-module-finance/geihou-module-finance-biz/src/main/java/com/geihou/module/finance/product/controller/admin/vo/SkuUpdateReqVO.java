package com.geihou.module.finance.product.controller.admin.vo;

/**
 * SKU update request VO.
 * Note: price changes must go through the dedicated /sku/{id}/price endpoint.
 */
public class SkuUpdateReqVO {

    private Long id;
    private String skuName;
    private String specAttributes;
    private Integer dailyLimit;
    private Integer perOrderLimit;
    private Integer minOrderQuantity;
    private String primaryImageUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }

    public String getSpecAttributes() { return specAttributes; }
    public void setSpecAttributes(String specAttributes) { this.specAttributes = specAttributes; }

    public Integer getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(Integer dailyLimit) { this.dailyLimit = dailyLimit; }

    public Integer getPerOrderLimit() { return perOrderLimit; }
    public void setPerOrderLimit(Integer perOrderLimit) { this.perOrderLimit = perOrderLimit; }

    public Integer getMinOrderQuantity() { return minOrderQuantity; }
    public void setMinOrderQuantity(Integer minOrderQuantity) { this.minOrderQuantity = minOrderQuantity; }

    public String getPrimaryImageUrl() { return primaryImageUrl; }
    public void setPrimaryImageUrl(String primaryImageUrl) { this.primaryImageUrl = primaryImageUrl; }
}
