package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;

/**
 * Stock event service interface.
 *
 * <p>Records immutable stock events and updates balance atomically.
 * Implements {@link com.geihou.module.supplychain.api.stock.StockEventApi}.
 */
public interface StockEventService {

    /**
     * Record a stock event (idempotent + balance update in same transaction).
     *
     * @param req event request
     * @return event ID
     */
    Long recordEvent(StockEventReqDTO req);
}
