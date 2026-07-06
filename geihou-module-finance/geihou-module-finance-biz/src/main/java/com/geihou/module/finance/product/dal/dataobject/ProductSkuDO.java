package com.geihou.module.finance.product.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for product_sku table.
 *
 * <p>SKU (specific specification under an SPU) with tenant isolation and soft delete.
 * All price fields use BigDecimal (never double/float).
 * Status field stores ENUM_SKU_STATUS code values.
 */
@TableName("product_sku")
public class ProductSkuDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long spuId;

    private String skuCode;
    private String skuName;
    private String specAttributes;

    // Prices - BigDecimal only, never double/float
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

    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;

    @TableLogic
    private Boolean deleted;

    // --- Getters and Setters ---

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
