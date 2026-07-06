package com.geihou.module.supplychain.bom.service;

import com.geihou.module.supplychain.api.bom.enums.BomRecipeStatusEnum;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeItemDO;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.dal.mapper.BomRecipeItemMapper;
import com.geihou.module.supplychain.bom.dal.mapper.BomRecipeMapper;
import com.geihou.module.supplychain.bom.dal.mapper.ProductMasterMapper;
import com.geihou.module.supplychain.stock.mq.StockEventPublisher;
import com.geihou.module.supplychain.stock.mq.event.BomVersionChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Implementation of {@link BomRecipeService}.
 *
 * <p>Recipe lifecycle management with circular reference detection.
 *
 * <p>Source: TASK-G2-02A.
 */
@Service
public class BomRecipeServiceImpl implements BomRecipeService {

    @Autowired
    private BomRecipeMapper bomRecipeMapper;
    @Autowired
    private BomRecipeItemMapper bomRecipeItemMapper;
    @Autowired
    private ProductMasterMapper productMasterMapper;
    @Autowired
    private StockEventPublisher stockEventPublisher;

    @Override
    public Long createDraftRecipe(BomRecipeDO recipe) {
        Objects.requireNonNull(recipe, "recipe must not be null");
        Objects.requireNonNull(recipe.getTenantId(), "tenantId must not be null");
        Objects.requireNonNull(recipe.getProductId(), "productId must not be null");

        // Verify product exists in same tenant
        ProductMasterDO product = productMasterMapper.selectByIdAndTenant(
                recipe.getProductId(), recipe.getTenantId());
        if (product == null) {
            throw new IllegalStateException(
                    "product not found for tenant_id=" + recipe.getTenantId()
                    + " product_id=" + recipe.getProductId());
        }

        // Auto-assign version number
        int maxVersion = bomRecipeMapper.selectMaxVersionNo(recipe.getTenantId(), recipe.getProductId());
        recipe.setVersionNo(maxVersion + 1);
        recipe.setStatus(BomRecipeStatusEnum.DRAFT.getCode());
        recipe.setCreator("system");
        recipe.setCreateTime(LocalDateTime.now());
        recipe.setUpdater("system");
        recipe.setUpdateTime(LocalDateTime.now());
        recipe.setDeleted(false);

        bomRecipeMapper.insert(recipe);
        return recipe.getId();
    }

    @Override
    @Transactional
    public void updateDraftItems(Long tenantId, Long recipeId, List<BomRecipeItemDO> items) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(recipeId, "recipeId must not be null");

        BomRecipeDO recipe = bomRecipeMapper.selectByIdAndTenant(recipeId, tenantId);
        if (recipe == null) {
            throw new IllegalStateException("recipe not found: id=" + recipeId + " tenant=" + tenantId);
        }

        // Only DRAFT recipes can have items edited
        if (!BomRecipeStatusEnum.DRAFT.getCode().equals(recipe.getStatus())) {
            throw new IllegalStateException(
                    "only DRAFT recipe can edit items, current status=" + recipe.getStatus());
        }

        if (items == null) {
            items = new ArrayList<>();
        }

        // Validate each item
        Set<Long> componentIds = new HashSet<>();
        for (BomRecipeItemDO item : items) {
            Objects.requireNonNull(item.getComponentProductId(), "componentProductId must not be null");
            Objects.requireNonNull(item.getQuantity(), "quantity must not be null");

            // quantity > 0
            if (item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException(
                        "quantity must be > 0, got " + item.getQuantity());
            }

            // waste_rate: default 0 if null
            if (item.getWasteRate() == null) {
                item.setWasteRate(BigDecimal.ZERO);
            }

            // 0 <= waste_rate < 1
            if (item.getWasteRate().compareTo(BigDecimal.ZERO) < 0
                    || item.getWasteRate().compareTo(BigDecimal.ONE) >= 0) {
                throw new IllegalStateException(
                        "waste_rate must be >= 0 and < 1, got " + item.getWasteRate());
            }

            // Component product must exist in same tenant
            ProductMasterDO component = productMasterMapper.selectByIdAndTenant(
                    item.getComponentProductId(), tenantId);
            if (component == null) {
                throw new IllegalStateException(
                        "component product not found: id=" + item.getComponentProductId()
                        + " tenant=" + tenantId);
            }

            componentIds.add(item.getComponentProductId());
        }

        // Circular reference detection
        if (detectCircularReference(tenantId, recipe.getProductId(), new ArrayList<>(componentIds))) {
            throw new IllegalStateException(
                    "circular reference detected for product_id=" + recipe.getProductId());
        }

        // Soft-delete existing items
        bomRecipeItemMapper.softDeleteByTenantRecipe(tenantId, recipeId, "system", LocalDateTime.now());

        // Insert new items
        for (BomRecipeItemDO item : items) {
            item.setTenantId(tenantId);
            item.setRecipeId(recipeId);
            item.setCreator("system");
            item.setCreateTime(LocalDateTime.now());
            item.setUpdater("system");
            item.setUpdateTime(LocalDateTime.now());
            item.setDeleted(false);
            bomRecipeItemMapper.insert(item);
        }
    }

    @Override
    @Transactional
    public boolean activateRecipe(Long tenantId, Long recipeId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(recipeId, "recipeId must not be null");

        BomRecipeDO recipe = bomRecipeMapper.selectByIdAndTenant(recipeId, tenantId);
        if (recipe == null) {
            throw new IllegalStateException("recipe not found: id=" + recipeId + " tenant=" + tenantId);
        }

        // Only DRAFT can be activated
        if (!BomRecipeStatusEnum.DRAFT.getCode().equals(recipe.getStatus())) {
            throw new IllegalStateException(
                    "only DRAFT recipe can be activated, current status=" + recipe.getStatus());
        }

        // Load recipe items for circular reference validation
        List<BomRecipeItemDO> items = bomRecipeItemMapper.listByTenantRecipe(tenantId, recipeId);
        List<Long> componentIds = new ArrayList<>();
        for (BomRecipeItemDO item : items) {
            componentIds.add(item.getComponentProductId());
        }

        // Circular reference validation including DRAFT recipes,
        // so DRAFT-to-DRAFT cycles cannot become ACTIVE.
        if (detectCircularReferenceForActivation(tenantId, recipe.getProductId(), componentIds)) {
            throw new IllegalStateException(
                    "circular reference detected for product_id=" + recipe.getProductId()
                    + " on activation of recipe_id=" + recipeId);
        }

        // Archive any existing ACTIVE recipe for same product
        bomRecipeMapper.archiveActiveRecipes(tenantId, recipe.getProductId(),
                "system", LocalDateTime.now());

        // Activate this recipe using tenant-scoped update (WHERE id AND tenant_id AND deleted=false)
        bomRecipeMapper.updateStatusByIdAndTenant(recipeId, tenantId,
                BomRecipeStatusEnum.ACTIVE.getCode(), "system", LocalDateTime.now());
        publishBomVersionChanged(recipe);
        return true;
    }

    private void publishBomVersionChanged(BomRecipeDO recipe) {
        BomVersionChangedEvent event = new BomVersionChangedEvent();
        event.setTenantId(recipe.getTenantId());
        event.setRecipeId(recipe.getId());
        event.setProductId(recipe.getProductId());
        event.setRecipeVersion(recipe.getVersionNo());
        event.setStatus(BomRecipeStatusEnum.ACTIVE.getCode());
        event.setEventTime(LocalDateTime.now());
        stockEventPublisher.publishBomVersionChanged(event);
    }

    @Override
    public BomRecipeDO getById(Long id, Long tenantId) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return bomRecipeMapper.selectByIdAndTenant(id, tenantId);
    }

    @Override
    public List<BomRecipeItemDO> getItems(Long recipeId, Long tenantId) {
        Objects.requireNonNull(recipeId, "recipeId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return bomRecipeItemMapper.listByTenantRecipe(tenantId, recipeId);
    }

    @Override
    public BomRecipeDO getActiveRecipe(Long productId, Long tenantId) {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return bomRecipeMapper.selectActiveByTenantProduct(tenantId, productId);
    }

    @Override
    public boolean detectCircularReference(Long tenantId, Long productId, List<Long> componentIds) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(productId, "productId must not be null");

        if (componentIds == null || componentIds.isEmpty()) {
            return false;
        }

        // Self loop: product references itself
        if (componentIds.contains(productId)) {
            return true;
        }

        // DFS to detect cycles through existing ACTIVE recipes
        // Build adjacency: for each component, check if it has an ACTIVE recipe
        // whose components eventually lead back to productId
        Set<Long> visited = new HashSet<>();
        Set<Long> recursionStack = new HashSet<>();

        for (Long componentId : componentIds) {
            if (hasCycleDFS(tenantId, componentId, productId, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * DFS traversal to detect cycles.
     *
     * <p>Starting from componentId, follow ACTIVE recipe links.
     * If we ever reach productId, a cycle exists.
     */
    private boolean hasCycleDFS(Long tenantId, Long currentProductId,
                                 Long targetProductId,
                                 Set<Long> visited,
                                 Set<Long> recursionStack) {
        // If we've reached the target, it's a cycle
        if (currentProductId.equals(targetProductId)) {
            return true;
        }

        // Already fully explored this node without finding target
        if (visited.contains(currentProductId)) {
            return false;
        }

        // Currently in recursion stack → cycle within sub-graph
        if (recursionStack.contains(currentProductId)) {
            return true;
        }

        recursionStack.add(currentProductId);

        // Get ACTIVE recipe for this component product
        BomRecipeDO activeRecipe = bomRecipeMapper.selectActiveByTenantProduct(tenantId, currentProductId);
        if (activeRecipe != null) {
            List<BomRecipeItemDO> items = bomRecipeItemMapper.listByTenantRecipe(tenantId, activeRecipe.getId());
            for (BomRecipeItemDO item : items) {
                if (hasCycleDFS(tenantId, item.getComponentProductId(), targetProductId,
                        visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(currentProductId);
        visited.add(currentProductId);
        return false;
    }

    /**
     * Circular reference detection that considers both DRAFT and ACTIVE recipes.
     *
     * <p>Used during activation so that DRAFT-to-DRAFT cycles are caught before
     * either recipe becomes ACTIVE. Traverses all non-archived (DRAFT + ACTIVE)
     * recipe links via {@link BomRecipeMapper#selectNonArchivedByTenantProduct}.
     *
     * @param tenantId     tenant ID
     * @param productId    the product being activated
     * @param componentIds the component product IDs of the recipe being activated
     * @return true if a cycle would be introduced
     */
    private boolean detectCircularReferenceForActivation(Long tenantId, Long productId,
                                                         List<Long> componentIds) {
        if (componentIds == null || componentIds.isEmpty()) {
            return false;
        }

        // Self loop
        if (componentIds.contains(productId)) {
            return true;
        }

        Set<Long> visited = new HashSet<>();
        Set<Long> recursionStack = new HashSet<>();

        for (Long componentId : componentIds) {
            if (hasCycleDFSIncludingDraft(tenantId, componentId, productId,
                    visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * DFS traversal that follows ALL non-archived (DRAFT + ACTIVE) recipe links.
     *
     * <p>If we ever reach {@code targetProductId}, a cycle exists.
     */
    private boolean hasCycleDFSIncludingDraft(Long tenantId, Long currentProductId,
                                              Long targetProductId,
                                              Set<Long> visited,
                                              Set<Long> recursionStack) {
        if (currentProductId.equals(targetProductId)) {
            return true;
        }

        if (visited.contains(currentProductId)) {
            return false;
        }

        if (recursionStack.contains(currentProductId)) {
            return true;
        }

        recursionStack.add(currentProductId);

        // Get ALL non-archived (DRAFT + ACTIVE) recipes for this product
        List<BomRecipeDO> recipes = bomRecipeMapper.selectNonArchivedByTenantProduct(
                tenantId, currentProductId);
        for (BomRecipeDO recipe : recipes) {
            List<BomRecipeItemDO> items = bomRecipeItemMapper.listByTenantRecipe(
                    tenantId, recipe.getId());
            for (BomRecipeItemDO item : items) {
                if (hasCycleDFSIncludingDraft(tenantId, item.getComponentProductId(),
                        targetProductId, visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(currentProductId);
        visited.add(currentProductId);
        return false;
    }
}
