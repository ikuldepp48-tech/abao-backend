package com.geihou.module.supplychain.api.stock;

import com.geihou.module.supplychain.api.stock.dto.StockCoverageDecisionRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageObserveReqDTO;

/**
 * Stock mapping coverage API (G2-01B3B).
 *
 * <p>Finance calls this when it hits an existing stock mapping SKIP path.
 * The implementation records an audit observation and returns whether the
 * caller should preserve SKIP behavior or enforce a failure.
 */
public interface StockCoverageApi {

    StockCoverageDecisionRespDTO observeMissingMapping(StockCoverageObserveReqDTO req);
}
