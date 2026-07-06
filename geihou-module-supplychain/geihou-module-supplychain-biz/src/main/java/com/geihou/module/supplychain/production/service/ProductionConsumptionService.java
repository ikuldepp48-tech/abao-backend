package com.geihou.module.supplychain.production.service;

import com.geihou.module.supplychain.production.controller.admin.vo.ScanPickReqVO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderConsumptionDO;

import java.util.List;

/**
 * Production consumption (scan-pick) service interface.
 *
 * <p>Handles 领料记录 (material picking) for production orders.
 *
 * <p>Source: TASK-G2-02N §6.3.
 */
public interface ProductionConsumptionService {

    /**
     * scan-pick: record a material pick for a production order.
     *
     * <p>Validates:
     * <ul>
     *   <li>Order exists and tenant matches</li>
     *   <li>Order stage is IN_PROGRESS</li>
     *   <li>Component is in the order's BOM recipe</li>
     *   <li>Pick quantity > 0</li>
     *   <li>pickSeq not already used for this component</li>
     * </ul>
     *
     * <p>Writes: consumption record (INSERT-only) + PRODUCTION_OUT stock_event.
     * StockBusinessException propagates directly.
     *
     * @param req scan-pick request
     * @return created consumption record
     */
    ProductionOrderConsumptionDO scanPick(ScanPickReqVO req);

    /**
     * List consumption records for a production order (tenant-isolated).
     */
    List<ProductionOrderConsumptionDO> listConsumptions(Long orderId, Long tenantId);
}
