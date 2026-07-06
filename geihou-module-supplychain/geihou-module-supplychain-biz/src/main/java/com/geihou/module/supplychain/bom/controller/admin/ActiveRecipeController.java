package com.geihou.module.supplychain.bom.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.supplychain.api.bom.BomApi;
import com.geihou.module.supplychain.api.bom.dto.BomRecipeRespDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin read-only endpoint for active BOM recipe by product.
 *
 * <p>Canonical path: GET /admin/bom/products/{productId}/active-recipe
 *
 * <p>Source: TASK-G2-02G.
 */
@RestController
@RequestMapping("/admin/bom/products")
public class ActiveRecipeController {

    private final BomApi bomApi;

    public ActiveRecipeController(BomApi bomApi) {
        this.bomApi = bomApi;
    }

    @GetMapping("/{productId}/active-recipe")
    public CommonResult<BomRecipeRespDTO> getActiveRecipe(
            @RequestParam Long tenantId,
            @PathVariable Long productId) {
        return CommonResult.success(bomApi.getActiveRecipe(tenantId, productId));
    }
}
