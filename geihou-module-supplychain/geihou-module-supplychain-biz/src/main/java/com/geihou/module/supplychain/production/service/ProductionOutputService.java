package com.geihou.module.supplychain.production.service;

import com.geihou.module.supplychain.production.controller.admin.vo.ScanOutputReqVO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderOutputDO;

import java.util.List;

/**
 * Production output (scan-output) service interface.
 *
 * <p>Handles 产出登记 (output registration) for production orders.
 *
 * <p>Source: TASK-G2-02N §6.4.
 */
public interface ProductionOutputService {

    /**
     * scan-output: record a production output for a production order.
     *
     * <p>Validates:
     * <ul>
     *   <li>Order exists and tenant matches</li>
     *   <li>Order stage is IN_PROGRESS</li>
     *   <li>Output quantity > 0</li>
     *   <li>outputSeq not already used</li>
     * </ul>
     *
     * <p>Writes: output record (INSERT-only) + PRODUCTION_IN stock_event.
     * StockBusinessException propagates directly.
     *
     * @param req scan-output request
     * @return created output record
     */
    ProductionOrderOutputDO scanOutput(ScanOutputReqVO req);

    /**
     * List output records for a production order (tenant-isolated).
     */
    List<ProductionOrderOutputDO> listOutputs(Long orderId, Long tenantId);
}
