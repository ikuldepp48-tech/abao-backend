package com.geihou.module.finance.product.controller.app.vo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Customer menu SPU summary VO (G1-02H).
 *
 * <p>Represents an SPU in the menu list. Price fields use BigDecimal (never double/float).
 * minSellingPrice/maxSellingPrice are computed from ACTIVE SKUs only.
 * skus list may be empty in list scenarios; populated in detail scenarios.
 */
public class MenuSpuVO {

    /** SPU ID */
    private Long id;

    /** SPU name */
    private String spuName;

    /** Short name */
    private String spuShortName;

    /** Primary image URL */
    private String primaryImageUrl;

    /** Description */
    private String description;

    /** Is recommended */
    private Boolean isRecommended;

    /** Is new arrival */
    private Boolean isNewArrival;

    /** Sort order */
    private Integer sortOrder;

    /** Product type (ENUM_PRODUCT_TYPE: FINISHED/COMBO/SERVICE visible to customers) */
    private String spuType;

    /** Minimum selling price among ACTIVE SKUs (BigDecimal) */
    private BigDecimal minSellingPrice;

    /** Maximum selling price among ACTIVE SKUs (BigDecimal) */
    private BigDecimal maxSellingPrice;

    /** SKU list (may be empty in list scenario; populated in detail scenario) */
    private List<MenuSkuVO> skus = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSpuName() { return spuName; }
    public void setSpuName(String spuName) { this.spuName = spuName; }

    public String getSpuShortName() { return spuShortName; }
    public void setSpuShortName(String spuShortName) { this.spuShortName = spuShortName; }

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

    public String getSpuType() { return spuType; }
    public void setSpuType(String spuType) { this.spuType = spuType; }

    public BigDecimal getMinSellingPrice() { return minSellingPrice; }
    public void setMinSellingPrice(BigDecimal minSellingPrice) { this.minSellingPrice = minSellingPrice; }

    public BigDecimal getMaxSellingPrice() { return maxSellingPrice; }
    public void setMaxSellingPrice(BigDecimal maxSellingPrice) { this.maxSellingPrice = maxSellingPrice; }

    public List<MenuSkuVO> getSkus() { return skus; }
    public void setSkus(List<MenuSkuVO> skus) { this.skus = skus; }
}
