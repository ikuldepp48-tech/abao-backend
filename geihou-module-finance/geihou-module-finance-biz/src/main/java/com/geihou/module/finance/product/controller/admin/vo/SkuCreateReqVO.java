package com.geihou.module.finance.product.controller.admin.vo;

import java.math.BigDecimal;

/**
 * SKU creation request VO.
 *
 * <p>All price fields use BigDecimal (never double/float).
 */
public class SkuCreateReqVO {

    private Long spuId;
    private String skuCode;
    private String skuName;
    private String specAttributes;
    private BigDecimal listPrice;
    private BigDecimal sellingPrice;
    private BigDecimal costPrice;
    private BigDecimal memberPrice;
    private Integer dailyLimit;
    private Integer perOrderLimit;
    private Integer minOrderQuantity;
    private String stockStrategy;
    private String primaryImageUrl;

    public Long getSpuId() { return spuId; }
    public void setSpuId(Long spuId) { this.spuId = spuId; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }

    public String getSpecAttributes() { return specAttributes; }
    public void setSpecAttributes(String specAttributes) { this.specAttributes = specAttributes; }

    public BigDecimal getListPrice() { return listPrice; }
    public void setListPrice(BigDecimal listPrice) { this.listPrice = listPrice; }

    public BigDecimal getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }

    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }

    public BigDecimal getMemberPrice() { return memberPrice; }
    public void setMemberPrice(BigDecimal memberPrice) { this.memberPrice = memberPrice; }

    public Integer getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(Integer dailyLimit) { this.dailyLimit = dailyLimit; }

    public Integer getPerOrderLimit() { return perOrderLimit; }
    public void setPerOrderLimit(Integer perOrderLimit) { this.perOrderLimit = perOrderLimit; }

    public Integer getMinOrderQuantity() { return minOrderQuantity; }
    public void setMinOrderQuantity(Integer minOrderQuantity) { this.minOrderQuantity = minOrderQuantity; }

    public String getStockStrategy() { return stockStrategy; }
    public void setStockStrategy(String stockStrategy) { this.stockStrategy = stockStrategy; }

    public String getPrimaryImageUrl() { return primaryImageUrl; }
    public void setPrimaryImageUrl(String primaryImageUrl) { this.primaryImageUrl = primaryImageUrl; }
}
