package com.geihou.module.finance.product.controller.app.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * Customer menu SPU detail VO (G1-02H).
 *
 * <p>Returned by GET /app-api/customer/menu/spu/{id}.
 * Contains full SPU detail with SKUs, addon groups, and combo items.
 * addonGroups are populated via product_spu_addon_group mapping table.
 * comboItems are populated only when spuType=COMBO and product_combo.status=ACTIVE.
 */
public class MenuSpuDetailVO {

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

    /** Product type (ENUM_PRODUCT_TYPE) */
    private String spuType;

    /** Category ID */
    private Long categoryId;

    /** Category name */
    private String categoryName;

    /** SKU list (ACTIVE + SOLD_OUT) */
    private List<MenuSkuVO> skus = new ArrayList<>();

    /** Addon groups (via product_spu_addon_group mapping) */
    private List<MenuAddonGroupVO> addonGroups = new ArrayList<>();

    /** Combo items (only when spuType=COMBO and combo.status=ACTIVE) */
    private List<MenuComboItemVO> comboItems = new ArrayList<>();

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

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public List<MenuSkuVO> getSkus() { return skus; }
    public void setSkus(List<MenuSkuVO> skus) { this.skus = skus; }

    public List<MenuAddonGroupVO> getAddonGroups() { return addonGroups; }
    public void setAddonGroups(List<MenuAddonGroupVO> addonGroups) { this.addonGroups = addonGroups; }

    public List<MenuComboItemVO> getComboItems() { return comboItems; }
    public void setComboItems(List<MenuComboItemVO> comboItems) { this.comboItems = comboItems; }
}
