package com.geihou.module.supplychain.api.stock.dto;

import com.geihou.module.supplychain.api.stock.enums.StockCoverageGateStatusEnum;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageModeEnum;

import java.util.List;

/**
 * Gate decision result for stock mapping coverage readiness (G2-01B3C).
 *
 * <p>Evaluated from the coverage report based on the current mode:
 * <ul>
 *   <li>AUDIT_ONLY: READY or READY_WITH_WARNINGS (never NOT_READY)</li>
 *   <li>ENFORCE: READY, READY_WITH_WARNINGS, or NOT_READY depending on which
 *       coverage types remain unresolved</li>
 * </ul>
 */
public class StockCoverageGateResultRespDTO {

    private StockCoverageGateStatusEnum status;
    private StockCoverageModeEnum mode;
    private int totalUnresolved;
    private List<String> blockReasons;

    public StockCoverageGateStatusEnum getStatus() { return status; }
    public void setStatus(StockCoverageGateStatusEnum status) { this.status = status; }

    public StockCoverageModeEnum getMode() { return mode; }
    public void setMode(StockCoverageModeEnum mode) { this.mode = mode; }

    public int getTotalUnresolved() { return totalUnresolved; }
    public void setTotalUnresolved(int totalUnresolved) { this.totalUnresolved = totalUnresolved; }

    public List<String> getBlockReasons() { return blockReasons; }
    public void setBlockReasons(List<String> blockReasons) { this.blockReasons = blockReasons; }
}
