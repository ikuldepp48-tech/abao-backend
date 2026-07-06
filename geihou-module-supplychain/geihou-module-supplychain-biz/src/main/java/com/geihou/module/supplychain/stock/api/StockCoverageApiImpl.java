package com.geihou.module.supplychain.stock.api;

import com.geihou.module.supplychain.api.stock.StockCoverageApi;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageDecisionRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageObserveReqDTO;
import com.geihou.module.supplychain.stock.service.StockMappingCoverageAuditService;
import org.springframework.stereotype.Service;

@Service
public class StockCoverageApiImpl implements StockCoverageApi {

    private final StockMappingCoverageAuditService auditService;

    public StockCoverageApiImpl(StockMappingCoverageAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public StockCoverageDecisionRespDTO observeMissingMapping(StockCoverageObserveReqDTO req) {
        return auditService.observeMissingMapping(req);
    }
}
