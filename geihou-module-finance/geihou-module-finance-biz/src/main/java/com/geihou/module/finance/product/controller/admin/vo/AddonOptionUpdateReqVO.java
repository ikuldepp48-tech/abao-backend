package com.geihou.module.finance.product.controller.admin.vo;

import java.math.BigDecimal;

/**
 * Addon option update request VO.
 */
public class AddonOptionUpdateReqVO {

    private String optionName;
    private BigDecimal extraPrice;
    private Integer sortOrder;

    public String getOptionName() { return optionName; }
    public void setOptionName(String optionName) { this.optionName = optionName; }

    public BigDecimal getExtraPrice() { return extraPrice; }
    public void setExtraPrice(BigDecimal extraPrice) { this.extraPrice = extraPrice; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
