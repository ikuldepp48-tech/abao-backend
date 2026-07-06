package com.geihou.module.supplychain.bom.explode;

import com.geihou.module.supplychain.api.bom.enums.BomRecipeStatusEnum;
import com.geihou.module.supplychain.api.bom.explode.dto.BomExplosionRespDTO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeItemDO;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.dal.mapper.BomRecipeItemMapper;
import com.geihou.module.supplychain.bom.dal.mapper.BomRecipeMapper;
import com.geihou.module.supplychain.bom.dal.mapper.ProductMasterMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Implementation of {@link BomExplosionService}.
 *
 * <p>Read-only recursive DFS explosion. Does not call any stock/finance services.
 *
 * <p>Source: TASK-G2-02B.
 */
@Service
public class BomExplosionServiceImpl implements BomExplosionService {

    private static final int MAX_DEPTH = 3;
    private static final int SCALE = 6;

    @Autowired
    private BomRecipeMapper bomRecipeMapper;
    @Autowired
    private BomRecipeItemMapper bomRecipeItemMapper;
    @Autowired
    private ProductMasterMapper productMasterMapper;

    @Override
    public List<BomExplosionRespDTO> explode(Long productId, Long recipeId,
                                              BigDecimal requestedQuantity, Long tenantId) {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        if (requestedQuantity == null || requestedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            requestedQuantity = BigDecimal.ONE;
        }

        // Resolve recipe: explicit recipeId or ACTIVE recipe for the product
        BomRecipeDO recipe;
        if (recipeId != null) {
            recipe = bomRecipeMapper.selectByIdAndTenant(recipeId, tenantId);
            if (recipe == null || !BomRecipeStatusEnum.ACTIVE.getCode().equals(recipe.getStatus())) {
                return new ArrayList<>();
            }
        } else {
            recipe = bomRecipeMapper.selectActiveByTenantProduct(tenantId, productId);
            if (recipe == null) {
                return new ArrayList<>();
            }
        }

        // Verify the recipe is for the requested product
        if (!productId.equals(recipe.getProductId())) {
            return new ArrayList<>();
        }

        // Load items
        List<BomRecipeItemDO> items = bomRecipeItemMapper.listByTenantRecipe(tenantId, recipe.getId());

        // Calculate the scaling factor: requestedQuantity / output_quantity
        BigDecimal outputQuantity = recipe.getOutputQuantity();
        if (outputQuantity == null || outputQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            outputQuantity = BigDecimal.ONE;
        }
        BigDecimal scale = requestedQuantity.divide(outputQuantity, SCALE, RoundingMode.HALF_UP);

        // DFS explode each item
        List<BomExplosionRespDTO> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        visited.add(productId); // root product is the starting point

        for (BomRecipeItemDO item : items) {
            BomExplosionRespDTO node = buildNode(item, scale, tenantId, 1, visited);
            result.add(node);
        }

        return result;
    }

    /**
     * Build a single explosion node and recursively expand its children.
     *
     * @param item       the recipe item
     * @param parentScale the accumulated scale from parent (requestedQty / outputQty at this level)
     * @param tenantId   tenant ID
     * @param depth      current depth (root children = 1)
     * @param visited    set of product IDs already visited in this path (for cycle detection)
     * @return explosion node
     */
    private BomExplosionRespDTO buildNode(BomRecipeItemDO item, BigDecimal parentScale,
                                           Long tenantId, int depth, Set<Long> visited) {
        Long componentProductId = item.getComponentProductId();

        // Look up product master for code/name/type
        ProductMasterDO product = productMasterMapper.selectByIdAndTenant(componentProductId, tenantId);

        BomExplosionRespDTO node = new BomExplosionRespDTO();
        node.setProductId(componentProductId);
        node.setProductCode(product != null ? product.getProductCode() : null);
        node.setProductName(product != null ? product.getProductName() : null);
        node.setComponentType(item.getComponentType() != null ? item.getComponentType() : "RAW_MATERIAL");
        node.setUnit(item.getUnit() != null ? item.getUnit() : (product != null ? product.getUnit() : ""));
        node.setWasteRate(item.getWasteRate() != null ? item.getWasteRate() : BigDecimal.ZERO);
        node.setDepth(depth);
        node.setTruncated(false);
        node.setCycleDetected(false);

        // Calculate quantity: parentScale * item.quantity * (1 + wasteRate)
        BigDecimal wasteRate = item.getWasteRate() != null ? item.getWasteRate() : BigDecimal.ZERO;
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        BigDecimal qtyWithWaste = quantity.multiply(BigDecimal.ONE.add(wasteRate));
        BigDecimal scaledQty = parentScale.multiply(qtyWithWaste).setScale(SCALE, RoundingMode.HALF_UP);
        node.setQuantity(scaledQty);

        // Cycle detection: if this product was already visited in the current path, mark and stop
        if (visited.contains(componentProductId)) {
            node.setCycleDetected(true);
            return node;
        }

        // Depth check: if depth >= MAX_DEPTH, mark truncated and stop
        if (depth >= MAX_DEPTH) {
            node.setTruncated(true);
            return node;
        }

        // Try to find an ACTIVE recipe for this component to expand further
        BomRecipeDO childRecipe = bomRecipeMapper.selectActiveByTenantProduct(tenantId, componentProductId);
        if (childRecipe == null) {
            // No recipe → leaf node, no children
            return node;
        }

        // Calculate the child scale: scaledQty / childRecipe.outputQuantity
        BigDecimal childOutput = childRecipe.getOutputQuantity();
        if (childOutput == null || childOutput.compareTo(BigDecimal.ZERO) <= 0) {
            childOutput = BigDecimal.ONE;
        }
        BigDecimal childScale = scaledQty.divide(childOutput, SCALE, RoundingMode.HALF_UP);

        // Load child items and recurse
        List<BomRecipeItemDO> childItems = bomRecipeItemMapper.listByTenantRecipe(tenantId, childRecipe.getId());
        if (!childItems.isEmpty()) {
            // Add this component to visited for the child path
            Set<Long> childVisited = new HashSet<>(visited);
            childVisited.add(componentProductId);

            List<BomExplosionRespDTO> children = new ArrayList<>();
            for (BomRecipeItemDO childItem : childItems) {
                BomExplosionRespDTO childNode = buildNode(childItem, childScale, tenantId, depth + 1, childVisited);
                children.add(childNode);
            }
            node.setChildren(children);
        }

        return node;
    }
}
