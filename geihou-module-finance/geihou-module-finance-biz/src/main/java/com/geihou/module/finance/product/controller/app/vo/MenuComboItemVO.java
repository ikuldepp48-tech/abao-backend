package com.geihou.module.finance.product.controller.app.vo;

/**
 * Customer menu combo item VO (G1-02H).
 *
 * <p>Represents a single item within a combo meal, for customer display.
 * Only populated when spuType=COMBO and product_combo.status=ACTIVE.
 * Combo items referencing DEPRECATED SKUs are not returned.
 */
public class MenuComboItemVO {

    /** Internal SKU ID */
    private Long itemSkuId;

    /** Internal SKU name */
    private String itemSkuName;

    /** Quantity in combo */
    private Integer quantity;

    /** Is optional (N-choose-M) */
    private Boolean isOptional;

    /** Alternative group number (same group = N-choose-1) */
    private Integer alternativeGroup;

    public Long getItemSkuId() { return itemSkuId; }
    public void setItemSkuId(Long itemSkuId) { this.itemSkuId = itemSkuId; }

    public String getItemSkuName() { return itemSkuName; }
    public void setItemSkuName(String itemSkuName) { this.itemSkuName = itemSkuName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Boolean getIsOptional() { return isOptional; }
    public void setIsOptional(Boolean isOptional) { this.isOptional = isOptional; }

    public Integer getAlternativeGroup() { return alternativeGroup; }
    public void setAlternativeGroup(Integer alternativeGroup) { this.alternativeGroup = alternativeGroup; }
}
