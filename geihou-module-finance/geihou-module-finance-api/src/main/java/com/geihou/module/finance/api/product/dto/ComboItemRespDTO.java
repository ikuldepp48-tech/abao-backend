package com.geihou.module.finance.api.product.dto;

import java.math.BigDecimal;

/**
 * Combo item response DTO for the ProductApi.expandCombo contract.
 *
 * <p>Represents one internal SKU within a combo meal.
 * itemSkuSellingPrice uses BigDecimal (never double/float).
 */
public class ComboItemRespDTO {

    private Long comboId;
    private Long comboSkuId;
    private Long itemSkuId;
    private String itemSkuName;
    private Integer quantity;
    private Boolean isOptional;
    private Integer alternativeGroup;
    private BigDecimal itemSkuSellingPrice;

    public Long getComboId() { return comboId; }
    public void setComboId(Long comboId) { this.comboId = comboId; }

    public Long getComboSkuId() { return comboSkuId; }
    public void setComboSkuId(Long comboSkuId) { this.comboSkuId = comboSkuId; }

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

    public BigDecimal getItemSkuSellingPrice() { return itemSkuSellingPrice; }
    public void setItemSkuSellingPrice(BigDecimal itemSkuSellingPrice) { this.itemSkuSellingPrice = itemSkuSellingPrice; }
}
