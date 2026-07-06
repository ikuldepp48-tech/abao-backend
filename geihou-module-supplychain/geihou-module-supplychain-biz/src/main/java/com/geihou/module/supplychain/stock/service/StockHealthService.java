package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.StockHealthRespDTO;

/**
 * Read-only stock health summary service.
 *
 * <p>Source: TASK-G2-02G.
 */
public interface StockHealthService {

    /**
     * Build a tenant-scoped stock health snapshot from existing stock tables.
     *
     * @param tenantId tenant ID
     * @return health snapshot; never null
     */
    StockHealthRespDTO getStockHealth(Long tenantId);
}
