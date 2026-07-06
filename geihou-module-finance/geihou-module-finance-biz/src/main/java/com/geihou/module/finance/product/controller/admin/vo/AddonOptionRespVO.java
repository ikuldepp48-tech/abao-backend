package com.geihou.module.finance.product.controller.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Addon option response VO.
 */
public class AddonOptionRespVO {

    private Long id;
    private Long addonGroupId;
    private Long optionSkuId;
    private String optionName;
    private BigDecimal extraPrice;
    private Integer sortOrder;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAddonGroupId() { return addonGroupId; }
    public void setAddonGroupId(Long addonGroupId) { this.addonGroupId = addonGroupId; }

    public Long getOptionSkuId() { return optionSkuId; }
    public void setOptionSkuId(Long optionSkuId) { this.optionSkuId = optionSkuId; }

    public String getOptionName() { return optionName; }
    public void setOptionName(String optionName) { this.optionName = optionName; }

    public BigDecimal getExtraPrice() { return extraPrice; }
    public void setExtraPrice(BigDecimal extraPrice) { this.extraPrice = extraPrice; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
