package com.geihou.module.finance.cart.controller.app.customer.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Cart add item request VO.
 *
 * <p>skuId is Long with @Min(1) — defends against Cart root-cause 2 (skuId undefined).
 * Jackson will reject string values for Long fields (type mismatch).
 * quantity is Integer with @Min(1).
 *
 * <p>CG-5 degradation: No stock check, only ProductApi.getSku() for existence/status.
 */
public class CartAddItemReqVO {

    /** SKU ID (required, Long type — prevents Cart root-cause "skuId undefined") */
    @NotNull(message = "SKU ID must not be null")
    @Min(value = 1, message = "SKU ID must be at least 1")
    private Long skuId;

    /** Quantity (required, minimum 1) */
    @NotNull(message = "Quantity must not be null")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    /** Options JSON string (optional, for addons/combo components) */
    private String options;

    /** Extra price from options (optional, BigDecimal) */
    private java.math.BigDecimal optionsExtraPrice;

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }

    public java.math.BigDecimal getOptionsExtraPrice() { return optionsExtraPrice; }
    public void setOptionsExtraPrice(java.math.BigDecimal optionsExtraPrice) { this.optionsExtraPrice = optionsExtraPrice; }
}
