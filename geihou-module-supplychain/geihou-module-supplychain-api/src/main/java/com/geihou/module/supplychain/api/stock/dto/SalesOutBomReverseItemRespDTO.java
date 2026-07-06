package com.geihou.module.supplychain.api.stock.dto;

import java.math.BigDecimal;

/**
 * Per-component deduction row in {@link SalesOutBomReverseRespDTO}.
 *
 * <p>Represents one raw-material CONSUME_OUT event created by the BOM reverse flow.
 */
public class SalesOutBomReverseItemRespDTO {

    /** Component product ID (raw-material leaf). */
    private Long componentProductId;

    /** Component SKU code. */
    private String skuCode;

    /** Component unit. */
    private String unit;

    /** Stock item ID used for the deduction. */
    private Long stockItemId;

    /** Deducted quantity (exploded). */
    private BigDecimal quantity;

    /** Created stock_event ID. */
    private Long eventId;

    /** Per-component idempotency key used (clientRequestId + "::" + componentProductId). */
    private String clientRequestId;

    /** Recipe ID snapshot. */
    private Long recipeId;

    /** Recipe version snapshot. */
    private Integer recipeVersion;

    // --- Getters and Setters ---

    public Long getComponentProductId() { return componentProductId; }
    public void setComponentProductId(Long componentProductId) { this.componentProductId = componentProductId; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Long getStockItemId() { return stockItemId; }
    public void setStockItemId(Long stockItemId) { this.stockItemId = stockItemId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public Integer getRecipeVersion() { return recipeVersion; }
    public void setRecipeVersion(Integer recipeVersion) { this.recipeVersion = recipeVersion; }
}
