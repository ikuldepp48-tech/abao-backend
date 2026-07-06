package com.geihou.module.supplychain.api.stock.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for sales-out BOM reverse consumption.
 *
 * <p>Returns the list of raw-material CONSUME_OUT events created,
 * along with recipe snapshot information.
 *
 * <p>Key rules (TASK-G2-02D):
 * <ul>
 *   <li>No parent stock_event is created — only CONSUME_OUT events for raw-material leaves.</li>
 *   <li>parent_event_id stays null in every created event.</li>
 * </ul>
 */
public class SalesOutBomReverseRespDTO {

    /** Tenant ID. */
    private Long tenantId;

    /** Finished-product product ID that was exploded. */
    private Long productId;

    /** Finished-product SKU code (if resolved). */
    private String skuCode;

    /** Sold quantity of the finished product. */
    private BigDecimal quantity;

    /** Active recipe ID snapshot used for explosion. */
    private Long recipeId;

    /** Active recipe version snapshot used for explosion. */
    private Integer recipeVersion;

    /** Per-component deduction rows. */
    private List<SalesOutBomReverseItemRespDTO> items = new ArrayList<>();

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public Integer getRecipeVersion() { return recipeVersion; }
    public void setRecipeVersion(Integer recipeVersion) { this.recipeVersion = recipeVersion; }

    public List<SalesOutBomReverseItemRespDTO> getItems() { return items; }
    public void setItems(List<SalesOutBomReverseItemRespDTO> items) {
        this.items = items != null ? items : new ArrayList<>();
    }
}
