package com.geihou.module.finance.stock;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Finance-internal stock integration orchestration layer.
 *
 * <p>Encapsulates:
 * <ol>
 *   <li>skuId → skuCode → stockItemId mapping queries (via StockQueryApi)</li>
 *   <li>shopId → stockLocationId mapping queries (via StockQueryApi)</li>
 *   <li>reserve/release/commit batch orchestration (via StockEventApi)</li>
 *   <li>active-BOM detection (via BomApi) and BOM reverse sales-out (via StockApi)</li>
 *   <li>refund BOM raw-material restore (via StockApi)</li>
 *   <li>SKIP logic for unmapped SKUs (CG-8 boundary)</li>
 * </ol>
 *
 * <p>Only depends on supplychain-api contracts (StockEventApi + StockQueryApi + BomApi + StockApi).
 * Does NOT depend on supplychain-biz services. Does NOT query supplychain tables directly.
 *
 * <p>Source: TASK-G2-01B2 Section 10, TASK-G2-02H-3.
 */
public interface StockIntegrationService {

    /**
     * Reserve stock for all cart items during checkout initiation.
     *
     * <p>For mapped SKUs (stock_item + stock_location configured): calls StockEventApi.reserveStock().
     * For unmapped SKUs: SKIP (log.info), no reserve call.
     *
     * <p>For active-BOM finished SKUs (BomApi.getActiveRecipeBySkuCode returns a DTO with id != null):
     * does NOT call finished-SKU reserveStock. Instead calls StockApi.checkStock as a read-only
     * BOM-aware sufficiency preflight; if insufficient or unmapped raw material, fails closed.
     *
     * <p>If any mapped SKU's reserve fails (e.g. INSUFFICIENT_AVAILABLE_STOCK),
     * throws exception → entire checkout initiate transaction rolls back.
     *
     * @param tenantId       tenant ID
     * @param sessionId      checkout_session ID (used as sourceRecordId + idempotentKey component)
     * @param cartId         cart ID (for querying cart items)
     * @param shopId         shop ID (maps to stock_location.storeId)
     * @param operatorUserId operator user ID
     */
    void reserveForCheckout(Long tenantId, Long sessionId, Long cartId,
                            Long shopId, Long operatorUserId);

    /**
     * Release all reservations for a checkout session (cancel/expire/pay-fail).
     *
     * <p>For mapped SKUs: calls StockEventApi.releaseStock().
     * For unmapped SKUs: SKIP (no reserve record to release).
     *
     * <p>Active-BOM SKUs have no finished-SKU reservation to release (reserve was skipped),
     * so release is a no-op for them.
     *
     * <p>Release failures are caught and logged (log.warn) — do NOT block terminal state.
     * Risk of stuck reservation is recorded for future compensation job.
     *
     * @param tenantId       tenant ID
     * @param sessionId      checkout_session ID
     * @param operatorUserId operator user ID
     */
    void releaseByCheckoutSession(Long tenantId, Long sessionId, Long operatorUserId);

    /**
     * Commit all reservations for a checkout session (order conversion).
     *
     * <p>For mapped non-BOM SKUs: calls StockEventApi.commitStock() (uses CONSUME_OUT event type).
     * For unmapped SKUs: SKIP (no reserve record to commit).
     *
     * <p>For active-BOM finished SKUs (BomApi.getActiveRecipeBySkuCode returns a DTO with id != null):
     * does NOT call finished-SKU commitStock. Instead calls StockApi.salesOutWithBomReverse to
     * deduct raw-material stock. locationId is resolved from the checkout session's shopId via
     * StockQueryApi.getStockLocationByStoreId.
     *
     * <p>If any mapped SKU's commit fails, throws exception → entire createFromCheckout
     * transaction rolls back.
     *
     * @param tenantId       tenant ID
     * @param sessionId      checkout_session ID
     * @param orderId        order ID (used as sourceRecordId for BOM reverse)
     * @param operatorUserId operator user ID
     * @param businessDate   business date
     * @param referenceNo    reference number (order_no)
     * @param sourceOrderItemIdByCartItemId checkout cart_item.id -> order_items.id mapping for BOM line traceability
     */
    void commitByCheckoutSession(Long tenantId, Long sessionId, Long orderId, Long operatorUserId,
                                 LocalDate businessDate, String referenceNo,
                                 Map<Long, Long> sourceOrderItemIdByCartItemId);

    /**
     * Restore original BOM raw-material consumption for a refunded sale source.
     *
     * <p>Calls StockApi.salesReverseRestore for the original sale source identified by
     * (sourceModule, orderId, orderNo). Uses deterministic idempotency based on the refund
     * and original sale identity.
     *
     * <p>If supplychain reports that no original BOM CONSUME_OUT events exist for that sale
     * source (non-BOM order), treats it as a no-op and logs the reason. Does NOT swallow
     * conflicts such as a concurrent restore of the same source — those propagate.
     *
     * @param tenantId       tenant ID
     * @param orderId        original order ID (used as sourceRecordId)
     * @param orderNo        original order number (used as referenceNo)
     * @param refundId       refund ID (used for restore clientRequestId)
     * @param operatorUserId operator user ID
     */
    void restoreForRefund(Long tenantId, Long orderId, String orderNo,
                          Long refundId, Long operatorUserId);

    /**
     * Restore original BOM raw-material consumption for a refunded sale source,
     * optionally restricted to selected order item IDs for partial/item refunds.
     */
    void restoreForRefund(Long tenantId, Long orderId, String orderNo,
                          Long refundId, Long operatorUserId,
                          List<Long> sourceOrderItemIds);
}
