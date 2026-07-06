package com.geihou.module.finance.product.controller.admin.vo;

import java.math.BigDecimal;

/**
 * Addon option creation request VO.
 *
 * <p>extraPrice uses BigDecimal (never double/float).
 */
public class AddonOptionCreateReqVO {

    private Long optionSkuId;
    private String optionName;
    private BigDecimal extraPrice;
    private Integer sortOrder;

    public Long getOptionSkuId() { return optionSkuId; }
    public void setOptionSkuId(Long optionSkuId) { this.optionSkuId = optionSkuId; }

    public String getOptionName() { return optionName; }
    public void setOptionName(String optionName) { this.optionName = optionName; }

    public BigDecimal getExtraPrice() { return extraPrice; }
    public void setExtraPrice(BigDecimal extraPrice) { this.extraPrice = extraPrice; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
