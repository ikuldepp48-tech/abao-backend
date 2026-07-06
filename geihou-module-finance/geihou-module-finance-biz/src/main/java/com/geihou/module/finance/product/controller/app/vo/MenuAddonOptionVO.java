package com.geihou.module.finance.product.controller.app.vo;

import java.math.BigDecimal;

/**
 * Customer menu addon option VO (G1-02H).
 *
 * <p>extraPrice uses BigDecimal (never double/float).
 * Customer-facing VO does not expose optionSkuId (internal field).
 * Only ACTIVE and SOLD_OUT options are returned; DISABLED is never returned.
 */
public class MenuAddonOptionVO {

    /** Option ID */
    private Long optionId;

    /** Option name */
    private String optionName;

    /** Extra price (BigDecimal, never double/float) */
    private BigDecimal extraPrice;

    /** Sort order */
    private Integer sortOrder;

    /** Option status (customer-facing: ACTIVE or SOLD_OUT only) */
    private String status;

    public Long getOptionId() { return optionId; }
    public void setOptionId(Long optionId) { this.optionId = optionId; }

    public String getOptionName() { return optionName; }
    public void setOptionName(String optionName) { this.optionName = optionName; }

    public BigDecimal getExtraPrice() { return extraPrice; }
    public void setExtraPrice(BigDecimal extraPrice) { this.extraPrice = extraPrice; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
