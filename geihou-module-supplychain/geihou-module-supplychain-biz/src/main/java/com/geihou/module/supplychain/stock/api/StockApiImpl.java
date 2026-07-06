package com.geihou.module.supplychain.stock.api;

import com.geihou.module.supplychain.api.stock.StockApi;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreRespDTO;
import com.geihou.module.supplychain.api.stock.dto.ProductCurrentCostRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SkuQuantityDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCheckRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockHealthRespDTO;
import com.geihou.module.supplychain.stock.check.StockCheckService;
import com.geihou.module.supplychain.stock.service.StockBomReverseService;
import com.geihou.module.supplychain.stock.service.StockHealthService;
import com.geihou.module.supplychain.stock.service.ProductCurrentCostService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bridge implementation of {@link StockApi}.
 *
 * <p>Pure delegation — no business logic. Delegates to
 * {@link StockCheckService} for the read-only BOM-aware stock sufficiency
 * preflight check, and to {@link StockBomReverseService} for the G2-02F
 * sales-out BOM reverse consumption and sales reverse-restore write operations.
 *
 * <p>Exceptions from the service layer (e.g. {@code StockBusinessException})
 * propagate through unchanged — this class does not catch, wrap, or suppress
 * any business exception.
 *
 * <p>Source: TASK-G2-02C, extended by TASK-G2-02F.
 */
@Service
public class StockApiImpl implements StockApi {

    private final StockCheckService stockCheckService;
    private final StockBomReverseService stockBomReverseService;
    private final StockHealthService stockHealthService;
    private final ProductCurrentCostService productCurrentCostService;

    public StockApiImpl(StockCheckService stockCheckService,
                        StockBomReverseService stockBomReverseService,
                        StockHealthService stockHealthService,
                        ProductCurrentCostService productCurrentCostService) {
        this.stockCheckService = stockCheckService;
        this.stockBomReverseService = stockBomReverseService;
        this.stockHealthService = stockHealthService;
        this.productCurrentCostService = productCurrentCostService;
    }

    @Override
    public StockCheckRespDTO checkStock(Long tenantId, List<SkuQuantityDTO> items) {
        return stockCheckService.checkStock(tenantId, items);
    }

    @Override
    public StockHealthRespDTO getStockHealth(Long tenantId) {
        return stockHealthService.getStockHealth(tenantId);
    }

    @Override
    public ProductCurrentCostRespDTO getProductCurrentCost(Long tenantId, Long productId) {
        return productCurrentCostService.getProductCurrentCost(tenantId, productId);
    }

    @Override
    public SalesOutBomReverseRespDTO salesOutWithBomReverse(SalesOutBomReverseReqDTO req) {
        return stockBomReverseService.salesOutWithBomReverse(req);
    }

    @Override
    public SalesReverseRestoreRespDTO salesReverseRestore(SalesReverseRestoreReqDTO req) {
        return stockBomReverseService.salesReverseRestore(req);
    }
}
