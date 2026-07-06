package com.geihou.module.finance.product.controller.admin.vo;

/**
 * Addon group update request VO.
 */
public class AddonGroupUpdateReqVO {

    private String groupName;
    private Integer selectMin;
    private Integer selectMax;
    private Boolean isRequired;
    private Integer sortOrder;

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public Integer getSelectMin() { return selectMin; }
    public void setSelectMin(Integer selectMin) { this.selectMin = selectMin; }

    public Integer getSelectMax() { return selectMax; }
    public void setSelectMax(Integer selectMax) { this.selectMax = selectMax; }

    public Boolean getIsRequired() { return isRequired; }
    public void setIsRequired(Boolean isRequired) { this.isRequired = isRequired; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
