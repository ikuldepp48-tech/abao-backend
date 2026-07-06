package com.geihou.module.finance.product.controller.app.vo;

import java.math.BigDecimal;

/**
 * Customer menu SKU VO (G1-02H).
 *
 * <p>All price fields use BigDecimal (never double/float).
 * Customer-facing VO does not expose internal fields: tenantId, totalSoldCount,
 * costPrice, stockStrategy, statusReason, skuCode.
 * Only ACTIVE and SOLD_OUT SKUs are returned to customers.
 */
public class MenuSkuVO {

    /** SKU ID (BIGINT strict) */
    private Long id;

    /** SKU name */
    private String skuName;

    /** Spec attributes JSON string */
    private String specAttributes;

    /** List price (BigDecimal, never double/float) */
    private BigDecimal listPrice;

    /** Current selling price (BigDecimal) */
    private BigDecimal sellingPrice;

    /** Member price (nullable, BigDecimal) */
    private BigDecimal memberPrice;

    /** SKU primary image URL */
    private String primaryImageUrl;

    /** SKU status (customer-facing: ACTIVE or SOLD_OUT only) */
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }

    public String getSpecAttributes() { return specAttributes; }
    public void setSpecAttributes(String specAttributes) { this.specAttributes = specAttributes; }

    public BigDecimal getListPrice() { return listPrice; }
    public void setListPrice(BigDecimal listPrice) { this.listPrice = listPrice; }

    public BigDecimal getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }

    public BigDecimal getMemberPrice() { return memberPrice; }
    public void setMemberPrice(BigDecimal memberPrice) { this.memberPrice = memberPrice; }

    public String getPrimaryImageUrl() { return primaryImageUrl; }
    public void setPrimaryImageUrl(String primaryImageUrl) { this.primaryImageUrl = primaryImageUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
