package com.geihou.module.finance.api.product.dto;

import java.math.BigDecimal;

/**
 * Addon option response DTO for the ProductApi.getAddonGroupsBySpu contract.
 *
 * <p>Represents one selectable option within an addon group.
 * extraPrice uses BigDecimal (never double/float).
 * Status uses ENUM_ADDON_OPTION_STATUS: ACTIVE/SOLD_OUT/DISABLED.
 * Only options with status=ACTIVE are returned by getAddonGroupsBySpu.
 */
public class AddonOptionRespDTO {

    private Long optionId;
    private Long optionSkuId;
    private String optionName;
    private BigDecimal extraPrice;
    private Integer sortOrder;
    private String status;

    public Long getOptionId() { return optionId; }
    public void setOptionId(Long optionId) { this.optionId = optionId; }

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
}
