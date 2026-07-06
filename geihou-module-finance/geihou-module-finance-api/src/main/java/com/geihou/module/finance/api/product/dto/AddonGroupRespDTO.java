package com.geihou.module.finance.api.product.dto;

import java.util.List;

/**
 * Addon group response DTO for the ProductApi.getAddonGroupsBySpu contract.
 *
 * <p>Represents an addon group (e.g. "Toppings") with its selectable options.
 * Returned by getAddonGroupsBySpu via the product_spu_addon_group mapping table.
 */
public class AddonGroupRespDTO {

    private Long groupId;
    private String groupCode;
    private String groupName;
    private Integer selectMin;
    private Integer selectMax;
    private Boolean isRequired;
    private Integer sortOrder;
    private List<AddonOptionRespDTO> options;

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }

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

    public List<AddonOptionRespDTO> getOptions() { return options; }
    public void setOptions(List<AddonOptionRespDTO> options) { this.options = options; }
}
