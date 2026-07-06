package com.geihou.module.supplychain.production.service;

import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderDO;

import java.math.BigDecimal;

/**
 * Production stock event integration service.
 *
 * <p>Executes PRODUCTION_OUT (BOM component deduction) and PRODUCTION_IN
 * (semi-finished product receipt) for a completed production order.
 *
 * <p>Must be called within the same transaction as the stage transition.
 * Any failure rolls back the entire transaction including the stage update.
 *
 * <p>Source: TASK-G2-02L, TASK-G2-02N.
 */
public interface ProductionStockEventIntegrationService {

    /**
     * Execute PRODUCTION_OUT (BOM component deduction) and PRODUCTION_IN
     * (semi-finished product receipt) for a completed production order.
     *
     * <p>Must be called within the same transaction as the stage transition.
     * Any failure rolls back the entire transaction including the stage update.
     * StockBusinessException is propagated directly (not wrapped) to preserve
     * original error codes such as INSUFFICIENT_STOCK.
     *
     * @param order the completed production order (actualQty must be set)
     */
    void integrateStockEvents(ProductionOrderDO order);

    /**
     * G2-02N: Record a single PRODUCTION_OUT stock_event for scan-pick.
     *
     * <p>Reuses the existing product_master.skuCode → stock_item.id mapping chain.
     * Uses clientRequestId = PROD-PICK-{orderId}-{componentProductId}-{pickSeq} for idempotency.
     * StockBusinessException propagates directly (not wrapped).
     *
     * @param order the production order
     * @param componentProductId the BOM component product ID
     * @param quantity the pick quantity
     * @param pickSeq the pick sequence number
     * @return stock_event ID
     */
    Long recordProductionOut(ProductionOrderDO order, Long componentProductId,
                             BigDecimal quantity, Integer pickSeq);

    /**
     * G2-02N: Record a single PRODUCTION_IN stock_event for scan-output or completion.
     *
     * <p>Reuses the existing productId → product_master.skuCode → stock_item.id mapping chain.
     * Uses clientRequestId = PROD-OUTPUT-{orderId}-{outputSeq} for scan-output,
     * or PROD-IN-{orderId} for completion without output records.
     * StockBusinessException propagates directly (not wrapped).
     *
     * @param order the production order
     * @param quantity the output quantity
     * @param clientRequestId the idempotency key
     * @return stock_event ID
     */
    Long recordProductionIn(ProductionOrderDO order, BigDecimal quantity, String clientRequestId);
}
