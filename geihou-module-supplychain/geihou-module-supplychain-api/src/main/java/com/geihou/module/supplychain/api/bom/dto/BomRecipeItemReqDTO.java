package com.geihou.module.supplychain.api.bom.dto;

import java.math.BigDecimal;

/**
 * Request DTO for a single BOM recipe item.
 *
 * <p>Used in batch item update operations.
 *
 * <p>Source: TASK-G2-02A.
 */
public class BomRecipeItemReqDTO {

    private Long componentProductId;
    private BigDecimal quantity;
    /** Waste rate, must be >= 0 and < 1 */
    private BigDecimal wasteRate;

    // --- Getters and Setters ---

    public Long getComponentProductId() { return componentProductId; }
    public void setComponentProductId(Long componentProductId) { this.componentProductId = componentProductId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getWasteRate() { return wasteRate; }
    public void setWasteRate(BigDecimal wasteRate) { this.wasteRate = wasteRate; }
}
