package com.geihou.module.supplychain.bom.preview;

import com.geihou.module.supplychain.api.bom.explode.dto.BomExplosionRespDTO;
import com.geihou.module.supplychain.api.bom.preview.dto.BomCostPreviewRespDTO;
import com.geihou.module.supplychain.bom.explode.BomExplosionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of {@link BomCostPreviewService}.
 *
 * <p>Delegates to {@link BomExplosionService} for tree expansion, then
 * flattens and aggregates RAW_MATERIAL leaves by product ID.
 * Cost fields (unitCost, totalCost) are null placeholders — G2-02C.
 *
 * <p>Source: TASK-G2-02B.
 */
@Service
public class BomCostPreviewServiceImpl implements BomCostPreviewService {

    private static final String RAW_MATERIAL = "RAW_MATERIAL";
    private static final int SCALE = 6;

    @Autowired
    private BomExplosionService bomExplosionService;

    @Override
    public List<BomCostPreviewRespDTO> previewCost(Long productId, Long recipeId,
                                                    BigDecimal requestedQuantity, Long tenantId) {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        // Step 1: explode the BOM tree
        List<BomExplosionRespDTO> explosion = bomExplosionService.explode(
                productId, recipeId, requestedQuantity, tenantId);

        // Step 2: flatten and aggregate RAW_MATERIAL leaves
        Map<Long, BomCostPreviewRespDTO> aggregation = new LinkedHashMap<>();
        for (BomExplosionRespDTO node : explosion) {
            collectRawMaterials(node, aggregation);
        }

        return new ArrayList<>(aggregation.values());
    }

    /**
     * Recursively traverse the explosion tree, collecting RAW_MATERIAL nodes.
     *
     * <p>A node is considered a "leaf" for aggregation purposes if:
     * <ul>
     *   <li>Its componentType is RAW_MATERIAL, OR</li>
     *   <li>It has no children (truncated, cycle, or no recipe)</li>
     * </ul>
     * Only RAW_MATERIAL nodes are aggregated; other leaf types are skipped
     * (they are not raw materials and have no cost basis in this slice).
     *
     * @param node        current explosion node
     * @param aggregation map keyed by product ID for aggregation
     */
    private void collectRawMaterials(BomExplosionRespDTO node,
                                      Map<Long, BomCostPreviewRespDTO> aggregation) {
        if (node == null) {
            return;
        }

        String componentType = node.getComponentType();
        boolean isRawMaterial = RAW_MATERIAL.equals(componentType);

        // If this is a raw material, aggregate it
        if (isRawMaterial) {
            Long pid = node.getProductId();
            BomCostPreviewRespDTO existing = aggregation.get(pid);
            if (existing == null) {
                BomCostPreviewRespDTO row = new BomCostPreviewRespDTO();
                row.setProductId(pid);
                row.setProductCode(node.getProductCode());
                row.setProductName(node.getProductName());
                row.setComponentType(RAW_MATERIAL);
                row.setUnit(node.getUnit());
                BigDecimal qty = node.getQuantity() != null ? node.getQuantity() : BigDecimal.ZERO;
                row.setTotalQuantity(qty.setScale(SCALE, RoundingMode.HALF_UP));
                // Cost placeholders — G2-02C
                row.setUnitCost(null);
                row.setTotalCost(null);
                aggregation.put(pid, row);
            } else {
                BigDecimal qty = node.getQuantity() != null ? node.getQuantity() : BigDecimal.ZERO;
                BigDecimal newTotal = existing.getTotalQuantity().add(qty);
                existing.setTotalQuantity(newTotal.setScale(SCALE, RoundingMode.HALF_UP));
            }
        }

        // Recurse into children regardless of this node's type
        if (node.getChildren() != null) {
            for (BomExplosionRespDTO child : node.getChildren()) {
                collectRawMaterials(child, aggregation);
            }
        }
    }
}
