package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.ProductCurrentCostRespDTO;

/**
 * Read-only product current cost estimate service.
 *
 * <p>Source: TASK-G2-02G.
 */
public interface ProductCurrentCostService {

    ProductCurrentCostRespDTO getProductCurrentCost(Long tenantId, Long productId);
}
