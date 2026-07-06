package com.geihou.module.supplychain.api.stock.dto;

import java.math.BigDecimal;

/**
 * Input DTO for stock preflight check: a SKU code + requested quantity.
 *
 * <p>The {@code skuCode} is used to resolve the finished-product in
 * {@code product_master} (matched against {@code product_code} first,
 * then {@code sku_code}).
 *
 * <p>Source: TASK-G2-02C.
 */
public class SkuQuantityDTO {

    /** SKU or product code identifying the finished product. */
    private String skuCode;

    /** Requested quantity (default 1 if null or <= 0). */
    private BigDecimal quantity;

    // --- Getters and Setters ---

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
}
