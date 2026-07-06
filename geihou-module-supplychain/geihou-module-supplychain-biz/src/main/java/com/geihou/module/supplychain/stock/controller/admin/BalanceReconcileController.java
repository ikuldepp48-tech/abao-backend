package com.geihou.module.supplychain.stock.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.supplychain.api.stock.dto.ReconcileReportRespDTO;
import com.geihou.module.supplychain.stock.controller.admin.vo.ReconcileReportRespVO;
import com.geihou.module.supplychain.stock.service.BalanceReconcileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Admin controller for stock balance reconciliation (G2-02J).
 *
 * <p>Single read-only GET endpoint that returns a reconciliation report
 * comparing stock_event recomputed balance against stock_balance.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /admin/stock/reconcile?tenantId={tenantId}} — full tenant reconciliation</li>
 *   <li>{@code GET /admin/stock/reconcile?tenantId={tenantId}&stockItemId={stockItemId}} — per-item reconciliation</li>
 *   <li>{@code GET /admin/stock/reconcile?tenantId={tenantId}&stockItemId={stockItemId}&locationId={locationId}} — single dimension</li>
 * </ul>
 *
 * <p>tenantId is required. No POST/PUT/DELETE operations — strictly read-only.
 *
 * <p>Source: TASK-G2-02J §6.2.
 */
@RestController
@RequestMapping("/admin/stock/reconcile")
public class BalanceReconcileController {

    @Autowired
    private BalanceReconcileService balanceReconcileService;

    /**
     * Get reconciliation report (read-only).
     *
     * @param tenantId    tenant ID (required)
     * @param stockItemId optional stock item filter
     * @param locationId  optional location filter (requires stockItemId)
     * @return reconciliation report wrapped in CommonResult
     */
    @GetMapping
    public CommonResult<ReconcileReportRespVO> reconcile(
            @RequestParam Long tenantId,
            @RequestParam(required = false) Long stockItemId,
            @RequestParam(required = false) Long locationId) {

        Objects.requireNonNull(tenantId, "tenantId must not be null");

        ReconcileReportRespDTO report;
        if (stockItemId != null && locationId != null) {
            report = balanceReconcileService.reconcileByItemLocation(tenantId, stockItemId, locationId);
        } else if (stockItemId != null) {
            report = balanceReconcileService.reconcileByItem(tenantId, stockItemId);
        } else {
            report = balanceReconcileService.reconcileAll(tenantId);
        }

        ReconcileReportRespVO vo = new ReconcileReportRespVO();
        vo.setReport(report);
        return CommonResult.success(vo);
    }
}
