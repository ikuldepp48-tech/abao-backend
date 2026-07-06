package com.geihou.module.supplychain.bom.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.supplychain.api.bom.preview.dto.BomCostPreviewRespDTO;
import com.geihou.module.supplychain.bom.preview.BomCostPreviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Admin controller for BOM cost preview (read-only).
 *
 * <p>Provides only GET endpoints. No POST/PUT/DELETE.
 *
 * <p>Source: TASK-G2-02B.
 */
@RestController
@RequestMapping("/admin-api/supplychain/bom/preview")
public class BomCostPreviewController {

    @Autowired
    private BomCostPreviewService bomCostPreviewService;

    /**
     * Preview cost: explode BOM and aggregate RAW_MATERIAL leaves.
     *
     * @param productId        product ID (required)
     * @param recipeId         optional recipe ID; null = ACTIVE recipe
     * @param requestedQuantity requested output quantity (default 1)
     * @param tenantId         tenant ID (required)
     * @return aggregated cost preview rows
     */
    @GetMapping
    public CommonResult<List<BomCostPreviewRespDTO>> preview(
            @RequestParam Long productId,
            @RequestParam(required = false) Long recipeId,
            @RequestParam(required = false, defaultValue = "1") BigDecimal requestedQuantity,
            @RequestParam Long tenantId) {
        List<BomCostPreviewRespDTO> result = bomCostPreviewService.previewCost(
                productId, recipeId, requestedQuantity, tenantId);
        return CommonResult.success(result);
    }
}
