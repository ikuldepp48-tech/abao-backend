package com.geihou.module.supplychain.stock.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.supplychain.api.stock.StockApi;
import com.geihou.module.supplychain.api.stock.dto.ProductCurrentCostRespDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin read-only endpoint for current product cost estimate.
 *
 * <p>Source: TASK-G2-02G.
 */
@RestController
@RequestMapping("/admin/stock/products")
public class ProductCurrentCostController {

    private final StockApi stockApi;

    public ProductCurrentCostController(StockApi stockApi) {
        this.stockApi = stockApi;
    }

    @GetMapping("/{productId}/current-cost")
    public CommonResult<ProductCurrentCostRespDTO> getProductCurrentCost(
            @RequestParam Long tenantId,
            @PathVariable Long productId) {
        return CommonResult.success(stockApi.getProductCurrentCost(tenantId, productId));
    }
}
