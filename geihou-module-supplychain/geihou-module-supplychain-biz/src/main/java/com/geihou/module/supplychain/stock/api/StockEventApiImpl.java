package com.geihou.module.supplychain.stock.api;

import com.geihou.module.supplychain.api.stock.StockEventApi;
import com.geihou.module.supplychain.api.stock.dto.StockCommitReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReleaseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReserveReqDTO;
import com.geihou.module.supplychain.stock.service.StockBalanceService;
import com.geihou.module.supplychain.stock.service.StockEventService;
import com.geihou.module.supplychain.stock.service.StockReserveService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Bridge implementation of {@link StockEventApi}.
 *
 * <p>Pure delegation — no business logic. Delegates to:
 * <ul>
 *   <li>{@link StockEventService} for recordEvent</li>
 *   <li>{@link StockBalanceService} for getAvailableQty / checkAvailable</li>
 *   <li>{@link StockReserveService} for reserveStock / releaseStock / commitStock</li>
 * </ul>
 *
 * <p>Source: TASK-G2-01B2 Section 7.2
 */
@Service
public class StockEventApiImpl implements StockEventApi {

    private final StockEventService stockEventService;
    private final StockReserveService stockReserveService;
    private final StockBalanceService stockBalanceService;

    public StockEventApiImpl(StockEventService stockEventService,
                              StockReserveService stockReserveService,
                              StockBalanceService stockBalanceService) {
        this.stockEventService = stockEventService;
        this.stockReserveService = stockReserveService;
        this.stockBalanceService = stockBalanceService;
    }

    @Override
    public Long recordEvent(StockEventReqDTO req) {
        return stockEventService.recordEvent(req);
    }

    @Override
    public BigDecimal getAvailableQty(Long tenantId, Long itemId, Long locationId) {
        return stockBalanceService.getAvailableQty(tenantId, itemId, locationId);
    }

    @Override
    public boolean checkAvailable(Long tenantId, Long itemId, Long locationId, BigDecimal requiredQty) {
        return stockBalanceService.checkAvailable(tenantId, itemId, locationId, requiredQty);
    }

    @Override
    public Long reserveStock(StockReserveReqDTO req) {
        return stockReserveService.reserveStock(req);
    }

    @Override
    public void releaseStock(StockReleaseReqDTO req) {
        stockReserveService.releaseStock(req);
    }

    @Override
    public Long commitStock(StockCommitReqDTO req) {
        return stockReserveService.commitStock(req);
    }
}
