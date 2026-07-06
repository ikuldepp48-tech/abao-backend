package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.StockCoverageDecisionRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageObserveReqDTO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockMappingCoverageAuditDO;

import java.util.List;

public interface StockMappingCoverageAuditService {

    StockCoverageDecisionRespDTO observeMissingMapping(StockCoverageObserveReqDTO req);

    List<StockMappingCoverageAuditDO> listRecent(Long tenantId, int limit);
}
