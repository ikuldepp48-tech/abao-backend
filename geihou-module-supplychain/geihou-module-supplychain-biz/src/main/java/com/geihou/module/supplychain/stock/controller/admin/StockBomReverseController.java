package com.geihou.module.supplychain.stock.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.supplychain.api.stock.StockApi;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreRespDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin controller for sales-out BOM reverse consumption and sales reverse
 * restore (G2-02F).
 *
 * <p>Pure delegation to {@link StockApi} — no business logic.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /admin/stock/bom-reverse/sales-out} — BOM reverse consumption</li>
 *   <li>{@code POST /admin/stock/bom-reverse/sales-reverse-restore} — reverse restore</li>
 * </ul>
 *
 * <p>Source: TASK-G2-02F.
 */
@RestController
@RequestMapping("/admin/stock/bom-reverse")
public class StockBomReverseController {

    @Autowired
    private StockApi stockApi;

    /**
     * Execute sales-out BOM reverse consumption.
     *
     * @param req request DTO
     * @return response with created event IDs and per-component deduction rows
     */
    @PostMapping("/sales-out")
    public CommonResult<SalesOutBomReverseRespDTO> salesOutWithBomReverse(
            @RequestBody SalesOutBomReverseReqDTO req) {
        SalesOutBomReverseRespDTO resp = stockApi.salesOutWithBomReverse(req);
        return CommonResult.success(resp);
    }

    /**
     * Execute sales reverse restore.
     *
     * @param req request DTO
     * @return response with restore event IDs and per-component rows
     */
    @PostMapping("/sales-reverse-restore")
    public CommonResult<SalesReverseRestoreRespDTO> salesReverseRestore(
            @RequestBody SalesReverseRestoreReqDTO req) {
        SalesReverseRestoreRespDTO resp = stockApi.salesReverseRestore(req);
        return CommonResult.success(resp);
    }
}
