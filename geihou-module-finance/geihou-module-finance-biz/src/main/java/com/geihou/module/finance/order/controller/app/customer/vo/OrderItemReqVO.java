package com.geihou.module.finance.order.controller.app.customer.vo;

import java.math.BigDecimal;

/**
 * Order item request VO (customer order creation).
 *
 * <p>skuId is Long (not null) — defends against Cart root-cause "skuId undefined".
 * quantity is BigDecimal — supports fractional quantities (e.g., 0.5 份).
 * modifiers is optional JSON string for addons/combo components.
 */
public class OrderItemReqVO {

    /** SKU ID (required, Long type — prevents Cart root-cause "skuId undefined") */
    private Long skuId;

    /** Quantity (required, BigDecimal, minimum 0.01) */
    private BigDecimal quantity;

    /** Modifiers JSON string (optional, for addons/combo components) */
    private String modifiers;

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getModifiers() { return modifiers; }
    public void setModifiers(String modifiers) { this.modifiers = modifiers; }
}
