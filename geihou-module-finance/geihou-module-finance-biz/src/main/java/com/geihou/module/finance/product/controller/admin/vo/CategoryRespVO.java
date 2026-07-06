package com.geihou.module.finance.product.controller.admin.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * Category response VO with tree structure support.
 */
public class CategoryRespVO {

    private Long id;
    private String categoryCode;
    private String categoryName;
    private Long parentCategoryId;
    private String categoryPath;
    private Integer level;
    private String icon;
    private Integer sortOrder;
    private String status;
    private List<CategoryRespVO> children = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public Long getParentCategoryId() { return parentCategoryId; }
    public void setParentCategoryId(Long parentCategoryId) { this.parentCategoryId = parentCategoryId; }

    public String getCategoryPath() { return categoryPath; }
    public void setCategoryPath(String categoryPath) { this.categoryPath = categoryPath; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<CategoryRespVO> getChildren() { return children; }
    public void setChildren(List<CategoryRespVO> children) { this.children = children; }
}
