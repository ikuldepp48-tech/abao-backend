package com.geihou.module.supplychain.api.stock.dto;

import com.geihou.module.supplychain.api.stock.enums.StockCoverageModeEnum;

/**
 * Decision returned after recording a mapping coverage observation.
 */
public class StockCoverageDecisionRespDTO {

    private StockCoverageModeEnum mode;
    private boolean enforce;

    public StockCoverageDecisionRespDTO() {
    }

    public StockCoverageDecisionRespDTO(StockCoverageModeEnum mode, boolean enforce) {
        this.mode = mode;
        this.enforce = enforce;
    }

    public StockCoverageModeEnum getMode() { return mode; }
    public void setMode(StockCoverageModeEnum mode) { this.mode = mode; }

    public boolean isEnforce() { return enforce; }
    public void setEnforce(boolean enforce) { this.enforce = enforce; }
}
