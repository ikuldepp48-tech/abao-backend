package com.geihou.module.supplychain.bom.preview;

import com.geihou.module.supplychain.api.bom.preview.dto.BomCostPreviewRespDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only BOM cost preview service.
 *
 * <p>Aggregates RAW_MATERIAL leaves from the explosion tree into a
 * flat cost-preview summary. Cost fields are placeholders pending G2-02C.
 *
 * <p>Source: TASK-G2-02B.
 */
public interface BomCostPreviewService {

    /**
     * Preview cost: explode then aggregate RAW_MATERIAL leaves by product.
     *
     * @param productId        product ID to preview
     * @param recipeId         optional specific recipe; null = use ACTIVE recipe
     * @param requestedQuantity requested output quantity (default 1 if null)
     * @param tenantId         tenant ID for isolation
     * @return aggregated raw-material cost preview rows
     */
    List<BomCostPreviewRespDTO> previewCost(Long productId, Long recipeId,
                                             BigDecimal requestedQuantity, Long tenantId);
}
