package com.geihou.module.finance.product.controller.app.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * Customer menu addon group VO (G1-02H).
 *
 * <p>Represents an addon group attached to an SPU (via product_spu_addon_group mapping).
 * Contains addon options visible to customers (ACTIVE/SOLD_OUT only; DISABLED not returned).
 */
public class MenuAddonGroupVO {

    /** Addon group ID */
    private Long groupId;

    /** Addon group name */
    private String groupName;

    /** Minimum selection count */
    private Integer selectMin;

    /** Maximum selection count */
    private Integer selectMax;

    /** Is required */
    private Boolean isRequired;

    /** Sort order */
    private Integer sortOrder;

    /** Options list (ACTIVE/SOLD_OUT only) */
    private List<MenuAddonOptionVO> options = new ArrayList<>();

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

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

    public List<MenuAddonOptionVO> getOptions() { return options; }
    public void setOptions(List<MenuAddonOptionVO> options) { this.options = options; }
}
