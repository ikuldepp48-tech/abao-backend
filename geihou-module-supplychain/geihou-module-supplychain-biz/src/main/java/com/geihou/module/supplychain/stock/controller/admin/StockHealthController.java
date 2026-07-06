package com.geihou.module.supplychain.stock.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.supplychain.api.stock.StockApi;
import com.geihou.module.supplychain.api.stock.dto.StockHealthRespDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin read-only stock health endpoint.
 *
 * <p>Source: TASK-G2-02G.
 */
@RestController
@RequestMapping("/admin/stock/health")
public class StockHealthController {

    private final StockApi stockApi;

    public StockHealthController(StockApi stockApi) {
        this.stockApi = stockApi;
    }

    @GetMapping
    public CommonResult<StockHealthRespDTO> getStockHealth(@RequestParam Long tenantId) {
        return CommonResult.success(stockApi.getStockHealth(tenantId));
    }
}
