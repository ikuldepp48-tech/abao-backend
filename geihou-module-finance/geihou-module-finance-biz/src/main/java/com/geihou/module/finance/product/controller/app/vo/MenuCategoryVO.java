package com.geihou.module.finance.product.controller.app.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * Customer menu category VO (G1-02H).
 *
 * <p>Represents a category in the menu tree. Supports multi-level hierarchy
 * via children. Contains SPUs belonging to this category.
 */
public class MenuCategoryVO {

    /** Category ID */
    private Long id;

    /** Category name */
    private String categoryName;

    /** Sort order */
    private Integer sortOrder;

    /** Icon URL */
    private String icon;

    /** SPUs in this category (ACTIVE only, excluding RAW_MATERIAL/SEMI_FINISHED) */
    private List<MenuSpuVO> spus = new ArrayList<>();

    /** Child categories (multi-level tree) */
    private List<MenuCategoryVO> children = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public List<MenuSpuVO> getSpus() { return spus; }
    public void setSpus(List<MenuSpuVO> spus) { this.spus = spus; }

    public List<MenuCategoryVO> getChildren() { return children; }
    public void setChildren(List<MenuCategoryVO> children) { this.children = children; }
}
