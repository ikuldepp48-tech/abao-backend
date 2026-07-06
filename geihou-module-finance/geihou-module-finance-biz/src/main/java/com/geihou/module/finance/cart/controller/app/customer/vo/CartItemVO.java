package com.geihou.module.finance.cart.controller.app.customer.vo;

import java.math.BigDecimal;

/**
 * Cart item VO for customer-facing responses.
 *
 * <p>All money fields use BigDecimal (never double/float).
 * skuId is Long (BIGINT strict, per Cart root-cause 2 defense).
 */
public class CartItemVO {

    private Long id;
    private Long skuId;
    private Long spuId;
    private String skuNameSnapshot;
    private String skuImageSnapshot;
    private BigDecimal unitPriceSnapshot;
    private Integer quantity;
    private String options;
    private BigDecimal optionsExtraPrice;
    private BigDecimal itemSubtotal;
    private BigDecimal itemDiscount;
    private BigDecimal itemTotal;
    private String itemState;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public Long getSpuId() { return spuId; }
    public void setSpuId(Long spuId) { this.spuId = spuId; }

    public String getSkuNameSnapshot() { return skuNameSnapshot; }
    public void setSkuNameSnapshot(String skuNameSnapshot) { this.skuNameSnapshot = skuNameSnapshot; }

    public String getSkuImageSnapshot() { return skuImageSnapshot; }
    public void setSkuImageSnapshot(String skuImageSnapshot) { this.skuImageSnapshot = skuImageSnapshot; }

    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot; }
    public void setUnitPriceSnapshot(BigDecimal unitPriceSnapshot) { this.unitPriceSnapshot = unitPriceSnapshot; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }

    public BigDecimal getOptionsExtraPrice() { return optionsExtraPrice; }
    public void setOptionsExtraPrice(BigDecimal optionsExtraPrice) { this.optionsExtraPrice = optionsExtraPrice; }

    public BigDecimal getItemSubtotal() { return itemSubtotal; }
    public void setItemSubtotal(BigDecimal itemSubtotal) { this.itemSubtotal = itemSubtotal; }

    public BigDecimal getItemDiscount() { return itemDiscount; }
    public void setItemDiscount(BigDecimal itemDiscount) { this.itemDiscount = itemDiscount; }

    public BigDecimal getItemTotal() { return itemTotal; }
    public void setItemTotal(BigDecimal itemTotal) { this.itemTotal = itemTotal; }

    public String getItemState() { return itemState; }
    public void setItemState(String itemState) { this.itemState = itemState; }
}
