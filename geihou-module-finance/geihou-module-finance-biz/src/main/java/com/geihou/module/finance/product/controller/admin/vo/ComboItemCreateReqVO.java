package com.geihou.module.finance.product.controller.admin.vo;

/**
 * Combo item creation request VO.
 */
public class ComboItemCreateReqVO {

    private Long itemSkuId;
    private Integer quantity;
    private Boolean isOptional;
    private Integer alternativeGroup;

    public Long getItemSkuId() { return itemSkuId; }
    public void setItemSkuId(Long itemSkuId) { this.itemSkuId = itemSkuId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Boolean getIsOptional() { return isOptional; }
    public void setIsOptional(Boolean isOptional) { this.isOptional = isOptional; }

    public Integer getAlternativeGroup() { return alternativeGroup; }
    public void setAlternativeGroup(Integer alternativeGroup) { this.alternativeGroup = alternativeGroup; }
}
