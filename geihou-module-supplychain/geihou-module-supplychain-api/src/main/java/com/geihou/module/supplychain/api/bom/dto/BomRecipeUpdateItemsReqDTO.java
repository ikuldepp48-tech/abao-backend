package com.geihou.module.supplychain.api.bom.dto;

import java.util.List;

/**
 * Request DTO for updating BOM recipe items (batch replace).
 *
 * <p>Only DRAFT recipes can have items updated. The full item list is replaced.
 *
 * <p>Source: TASK-G2-02A.
 */
public class BomRecipeUpdateItemsReqDTO {

    private Long tenantId;
    private Long recipeId;
    private List<BomRecipeItemReqDTO> items;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public List<BomRecipeItemReqDTO> getItems() { return items; }
    public void setItems(List<BomRecipeItemReqDTO> items) { this.items = items; }
}
