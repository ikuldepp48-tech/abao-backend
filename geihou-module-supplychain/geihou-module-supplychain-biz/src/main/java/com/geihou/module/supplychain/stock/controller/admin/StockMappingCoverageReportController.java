package com.geihou.module.supplychain.stock.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageGateResultRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageReportRespDTO;
import com.geihou.module.supplychain.stock.service.StockMappingCoverageReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Admin controller for stock mapping coverage report and readiness gate (G2-01B3C).
 *
 * <p>Single read-only GET endpoint that returns the coverage report and gate
 * decision for a given tenant. No POST/PUT/DELETE operations.
 */
@RestController
@RequestMapping("/admin/stock/coverage-report")
public class StockMappingCoverageReportController {

    @Autowired
    private StockMappingCoverageReportService reportService;

    /**
     * Get coverage report and gate result for a tenant (read-only).
     */
    @GetMapping
    public CommonResult<StockCoverageReportGateRespVO> getReportAndGate(@RequestParam Long tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        StockCoverageReportRespDTO report = reportService.generateReport(tenantId);
        StockCoverageGateResultRespDTO gate = reportService.evaluateGate(tenantId);

        StockCoverageReportGateRespVO vo = new StockCoverageReportGateRespVO();
        vo.setReport(report);
        vo.setGate(gate);
        return CommonResult.success(vo);
    }

    // --- Response VO ---

    public static class StockCoverageReportGateRespVO {
        private StockCoverageReportRespDTO report;
        private StockCoverageGateResultRespDTO gate;

        public StockCoverageReportRespDTO getReport() { return report; }
        public void setReport(StockCoverageReportRespDTO report) { this.report = report; }

        public StockCoverageGateResultRespDTO getGate() { return gate; }
        public void setGate(StockCoverageGateResultRespDTO gate) { this.gate = gate; }
    }
}
