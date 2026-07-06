package com.geihou.module.finance.product.controller.admin.vo;

/**
 * Response VO for SPU-addon group mapping.
 */
public class SpuAddonGroupRespVO {

    private Long id;
    private Long spuId;
    private Long addonGroupId;
    private String addonGroupCode;
    private String addonGroupName;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSpuId() { return spuId; }
    public void setSpuId(Long spuId) { this.spuId = spuId; }

    public Long getAddonGroupId() { return addonGroupId; }
    public void setAddonGroupId(Long addonGroupId) { this.addonGroupId = addonGroupId; }

    public String getAddonGroupCode() { return addonGroupCode; }
    public void setAddonGroupCode(String addonGroupCode) { this.addonGroupCode = addonGroupCode; }

    public String getAddonGroupName() { return addonGroupName; }
    public void setAddonGroupName(String addonGroupName) { this.addonGroupName = addonGroupName; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
