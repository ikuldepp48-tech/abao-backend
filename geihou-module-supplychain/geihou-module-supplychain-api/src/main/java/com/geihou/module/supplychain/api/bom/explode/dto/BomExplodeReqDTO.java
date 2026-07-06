package com.geihou.module.supplychain.api.bom.explode.dto;

import java.math.BigDecimal;

/**
 * Request DTO for BOM explosion.
 *
 * <p>Source: TASK-G2-02B.
 */
public class BomExplodeReqDTO {

    /** Product ID to explode (required). */
    private Long productId;

    /** Optional specific recipe ID; if null, the ACTIVE recipe is used. */
    private Long recipeId;

    /** Requested quantity (default 1). */
    private BigDecimal requestedQuantity;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public BigDecimal getRequestedQuantity() { return requestedQuantity; }
    public void setRequestedQuantity(BigDecimal requestedQuantity) { this.requestedQuantity = requestedQuantity; }
}
