package com.geihou.module.supplychain.api.stock.dto;

import java.math.BigDecimal;

/**
 * Per-component restore row in {@link SalesReverseRestoreRespDTO}.
 *
 * <p>Represents one inbound restore event created by the sales reverse-restore flow,
 * paired with the original {@code CONSUME_OUT} event it reverses.
 */
public class SalesReverseRestoreItemRespDTO {

    /** Original CONSUME_OUT event ID that this restore reverses. */
    private Long originalEventId;

    /** Created restore event ID. */
    private Long restoreEventId;

    /** Stock item ID (copied from original). */
    private Long stockItemId;

    /** Location ID (copied from original). */
    private Long locationId;

    /** Restored quantity (copied from original). */
    private BigDecimal quantity;

    /** Unit (copied from original). */
    private String unit;

    /** Recipe ID snapshot (copied from original). */
    private Long recipeId;

    /** Recipe version snapshot (copied from original). */
    private Integer recipeVersion;

    // --- Getters and Setters ---

    public Long getOriginalEventId() { return originalEventId; }
    public void setOriginalEventId(Long originalEventId) { this.originalEventId = originalEventId; }

    public Long getRestoreEventId() { return restoreEventId; }
    public void setRestoreEventId(Long restoreEventId) { this.restoreEventId = restoreEventId; }

    public Long getStockItemId() { return stockItemId; }
    public void setStockItemId(Long stockItemId) { this.stockItemId = stockItemId; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public Integer getRecipeVersion() { return recipeVersion; }
    public void setRecipeVersion(Integer recipeVersion) { this.recipeVersion = recipeVersion; }
}
