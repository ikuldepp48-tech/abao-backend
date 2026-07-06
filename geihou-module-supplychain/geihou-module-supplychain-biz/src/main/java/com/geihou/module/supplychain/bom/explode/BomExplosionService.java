package com.geihou.module.supplychain.bom.explode;

import com.geihou.module.supplychain.api.bom.explode.dto.BomExplosionRespDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only BOM explosion service.
 *
 * <p>Recursive DFS explosion with:
 * <ul>
 *   <li>Max depth 3 (root = depth 1)</li>
 *   <li>Proportional scaling by output_quantity</li>
 *   <li>Waste rate compounding across levels</li>
 *   <li>Defensive cycle detection (visited set)</li>
 *   <li>Strict tenant isolation</li>
 * </ul>
 *
 * <p>Source: TASK-G2-02B.
 */
public interface BomExplosionService {

    /**
     * Explode a product's BOM into a tree structure.
     *
     * @param productId        product ID to explode
     * @param recipeId         optional specific recipe; null = use ACTIVE recipe
     * @param requestedQuantity requested output quantity (default 1 if null)
     * @param tenantId         tenant ID for isolation
     * @return list of top-level explosion nodes; empty if no ACTIVE recipe
     */
    List<BomExplosionRespDTO> explode(Long productId, Long recipeId,
                                       BigDecimal requestedQuantity, Long tenantId);
}
