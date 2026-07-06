package com.geihou.module.finance.product.controller.admin.vo;

import java.math.BigDecimal;

/**
 * SKU price change request VO.
 *
 * <p>Field names match PRD-G1-02 Section 3.2 OpenAPI: newListPrice, newSellingPrice, reason, changeType.
 * reason must be >= 5 characters (validated in service).
 * changeType: MANUAL / COST_BASED / MARKET_BASED.
 */
public class SkuPriceChangeReqVO {

    private BigDecimal newListPrice;
    private BigDecimal newSellingPrice;
    private String reason;
    private String changeType;

    public BigDecimal getNewListPrice() { return newListPrice; }
    public void setNewListPrice(BigDecimal newListPrice) { this.newListPrice = newListPrice; }

    public BigDecimal getNewSellingPrice() { return newSellingPrice; }
    public void setNewSellingPrice(BigDecimal newSellingPrice) { this.newSellingPrice = newSellingPrice; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
}
