package com.geihou.module.finance.product.controller.admin.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Addon group response VO (includes options list).
 */
public class AddonGroupRespVO {

    private Long id;
    private String groupCode;
    private String groupName;
    private Integer selectMin;
    private Integer selectMax;
    private Boolean isRequired;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<AddonOptionRespVO> options;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public List<AddonOptionRespVO> getOptions() { return options; }
    public void setOptions(List<AddonOptionRespVO> options) { this.options = options; }
}
