package com.geihou.module.supplychain.api.bom;

import com.geihou.module.supplychain.api.bom.dto.BomRecipeRespDTO;
import com.geihou.module.supplychain.api.bom.explode.dto.BomExplosionRespDTO;
import com.geihou.module.supplychain.api.bom.preview.dto.BomCostPreviewRespDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only BOM API for cross-module consumption.
 *
 * <p>Exposes only explosion and cost-preview queries.
 * No write operations are provided.
 *
 * <p>Source: TASK-G2-02B.
 */
public interface BomApi {

    /**
     * Explode a product's BOM tree (read-only, recursive DFS, max depth 3).
     *
     * @param productId        product ID to explode
     * @param recipeId         optional specific recipe; null = use ACTIVE recipe
     * @param requestedQuantity requested output quantity (default 1 if null)
     * @param tenantId         tenant ID for isolation
     * @return list of top-level explosion nodes (tree structure)
     */
    List<BomExplosionRespDTO> explode(Long productId, Long recipeId,
                                       BigDecimal requestedQuantity, Long tenantId);

    /**
     * Preview cost (read-only): explode then aggregate RAW_MATERIAL leaves.
     *
     * <p>Cost fields are null/zero placeholders; actual cost calculation
     * is deferred to G2-02C.
     *
     * @param productId        product ID to preview
     * @param recipeId         optional specific recipe; null = use ACTIVE recipe
     * @param requestedQuantity requested output quantity (default 1 if null)
     * @param tenantId         tenant ID for isolation
     * @return aggregated raw-material cost preview rows
     */
    List<BomCostPreviewRespDTO> previewCost(Long productId, Long recipeId,
                                             BigDecimal requestedQuantity, Long tenantId);

    /**
     * Get the active BOM recipe (header + items) for a product (read-only, tenant-isolated).
     *
     * <p>If no ACTIVE recipe exists for the given product, returns a {@link BomRecipeRespDTO}
     * with {@code id = null} and an empty items list (explicit empty state).
     *
     * @param tenantId  tenant ID for isolation
     * @param productId product ID to look up
     * @return recipe header + items, or an empty-state DTO if no active recipe
     */
    BomRecipeRespDTO getActiveRecipe(Long tenantId, Long productId);

    /**
     * Get the active BOM recipe (header + items) for a product identified by SKU code
     * (read-only, tenant-isolated).
     *
     * <p>Resolves skuCode → productId via {@code product_master} (tenant_id + sku_code +
     * deleted=false + is_active=true + product_type=FINISHED), then delegates to
     * {@link #getActiveRecipe(Long, Long)}.
     *
     * <p>Handling for 0 / 1 / multiple product matches:
     * <ul>
     *   <li>0 matches (skuCode not found) → empty-state DTO</li>
     *   <li>1 match → delegates to {@link #getActiveRecipe(Long, Long)}</li>
     *   <li>≥2 matches → returns empty-state DTO and logs WARN (does NOT pick the first)</li>
     * </ul>
     *
     * <p>If {@code skuCode} is null or blank, returns an empty-state DTO without throwing.
     *
     * <p>Source: TASK-G2-02H-2A.
     *
     * @param tenantId tenant ID for isolation
     * @param skuCode  SKU code to look up (may be null/blank → empty-state)
     * @return recipe header + items, or an empty-state DTO if no active recipe / ambiguous match
     */
    BomRecipeRespDTO getActiveRecipeBySkuCode(Long tenantId, String skuCode);
}
