package com.geihou.module.finance.cart.controller.app.customer.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Cart update quantity request VO.
 *
 * <p>quantity is Integer with @Min(1) — must be at least 1.
 * Setting quantity to 0 should use the remove item endpoint instead.
 */
public class CartUpdateQuantityReqVO {

    @NotNull(message = "Quantity must not be null")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
