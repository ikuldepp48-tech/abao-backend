package com.geihou.module.supplychain.bom.service;

import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeItemDO;

import java.util.List;

/**
 * BOM recipe service interface.
 *
 * <p>Recipe lifecycle: create draft → update items → activate → archive.
 * All operations are tenant-isolated.
 *
 * <p>Source: TASK-G2-02A.
 */
public interface BomRecipeService {

    /**
     * Create a draft recipe for a product.
     *
     * <p>Version number is auto-assigned (incremented per product).
     * Status defaults to DRAFT.
     *
     * @param recipe recipe DO (tenantId, productId required)
     * @return created recipe ID
     */
    Long createDraftRecipe(BomRecipeDO recipe);

    /**
     * Update items for a DRAFT recipe (batch replace).
     *
     * <p>Throws IllegalStateException if recipe is not DRAFT.
     * Validates: quantity > 0, 0 <= wasteRate < 1, component exists in same tenant.
     * Validates: no circular reference (self loop, direct loop, multi-level).
     *
     * @param tenantId tenant ID
     * @param recipeId recipe ID
     * @param items    list of item DOs
     */
    void updateDraftItems(Long tenantId, Long recipeId, List<BomRecipeItemDO> items);

    /**
     * Activate a DRAFT recipe.
     *
     * <p>Archives any existing ACTIVE recipe for the same product.
     * Throws IllegalStateException if recipe is not DRAFT.
     *
     * @param tenantId tenant ID
     * @param recipeId recipe ID
     * @return true if activated
     */
    boolean activateRecipe(Long tenantId, Long recipeId);

    /**
     * Get recipe by ID (tenant-isolated), including items.
     *
     * @param id       recipe ID
     * @param tenantId tenant ID
     * @return recipe DO, or null if not found
     */
    BomRecipeDO getById(Long id, Long tenantId);

    /**
     * Get items for a recipe (tenant-isolated).
     *
     * @param recipeId recipe ID
     * @param tenantId tenant ID
     * @return list of items
     */
    List<BomRecipeItemDO> getItems(Long recipeId, Long tenantId);

    /**
     * Get the active recipe for a product (tenant-isolated).
     *
     * @param productId product ID
     * @param tenantId  tenant ID
     * @return active recipe DO, or null if none
     */
    BomRecipeDO getActiveRecipe(Long productId, Long tenantId);

    /**
     * Detect if creating/editing a recipe would introduce a circular reference.
     *
     * <p>Checks: self loop (product references itself as component),
     * direct loop (A→B→A), multi-level loop (A→B→C→A).
     *
     * @param tenantId     tenant ID
     * @param productId    the product being configured
     * @param componentIds the proposed component product IDs
     * @return true if circular reference detected
     */
    boolean detectCircularReference(Long tenantId, Long productId, List<Long> componentIds);
}
