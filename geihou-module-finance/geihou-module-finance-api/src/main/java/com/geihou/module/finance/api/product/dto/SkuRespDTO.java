package com.geihou.module.finance.api.product.dto;

import java.math.BigDecimal;

/**
 * SKU response DTO for the ProductApi contract.
 *
 * <p>Fields per PRD-G1-02 Section 3.3 DTO definitions.
 * All price fields use BigDecimal (never double/float).
 * id is Long (BIGINT strict, per Cart root-cause 2 defense).
 * Status uses ENUM_SKU_STATUS: NEW/ACTIVE/SOLD_OUT/PAUSED/DEPRECATED.
 * stockStrategy uses ENUM_STOCK_STRATEGY: TRACK_STOCK/UNLIMITED.
 */
public class SkuRespDTO {

    private Long id;
    private Long tenantId;
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
    private String status;
    private String statusReason;
    private String primaryImageUrl;
    private Integer totalSoldCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }

    public String getPrimaryImageUrl() { return primaryImageUrl; }
    public void setPrimaryImageUrl(String primaryImageUrl) { this.primaryImageUrl = primaryImageUrl; }

    public Integer getTotalSoldCount() { return totalSoldCount; }
    public void setTotalSoldCount(Integer totalSoldCount) { this.totalSoldCount = totalSoldCount; }
}
