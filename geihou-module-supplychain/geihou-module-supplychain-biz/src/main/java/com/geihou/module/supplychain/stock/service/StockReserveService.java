package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.StockCommitReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReleaseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReserveReqDTO;

/**
 * Stock reserve service interface.
 *
 * <p>Implements reserve/release/commit semantics for stock reservation.
 * All operations are tenant-isolated, idempotent, and use optimistic locking.
 *
 * <p>Source: TASK-G2-01B1 Section 6.
 */
public interface StockReserveService {

    /**
     * Reserve stock (reserve).
     *
     * <p>stock_balance.reserved_qty += qty, available_qty -= qty, total_qty unchanged.
     * INSERT stock_reserve (status = RESERVED). Does NOT write stock_event.
     *
     * @param req reserve request
     * @return reserve record ID
     */
    Long reserveStock(StockReserveReqDTO req);

    /**
     * Release a reservation (release).
     *
     * <p>stock_balance.reserved_qty -= qty, available_qty += qty, total_qty unchanged.
     * UPDATE stock_reserve SET status = RELEASED. Does NOT write stock_event.
     *
     * @param req release request
     */
    void releaseStock(StockReleaseReqDTO req);

    /**
     * Commit a reservation (commit — real stock deduction).
     *
     * <p>stock_balance.reserved_qty -= qty, total_qty -= qty, available_qty unchanged.
     * UPDATE stock_reserve SET status = COMMITTED.
     * INSERT stock_event (event_type = CONSUME_OUT, direction = OUT).
     *
     * @param req commit request
     * @return stock_event ID (CONSUME_OUT event ID)
     */
    Long commitStock(StockCommitReqDTO req);
}
