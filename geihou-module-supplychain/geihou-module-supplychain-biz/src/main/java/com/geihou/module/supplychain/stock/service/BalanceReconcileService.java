package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.ReconcileReportRespDTO;

/**
 * Read-only stock balance reconciliation service (G2-02J).
 *
 * <p>Recomputes expected total_qty / available_qty from the stock_event ledger
 * and compares against the current stock_balance table. The entire operation
 * is strictly read-only: no writes to stock_event, stock_balance, or any other
 * table. No calls to {@code StockEventService.recordEvent}.
 *
 * <p>For INTERNAL direction (COUNT_ADJUST) events, the adjustment sign is
 * <b>inferred</b> from the {@code balance_after} snapshot (task package §4.2),
 * because {@code stock_event} does not persist {@code adjustment_sign}. The
 * inferred sign and any inference warnings are surfaced in the report.
 *
 * <p>Source: TASK-G2-02J §6.1.
 */
public interface BalanceReconcileService {

    /**
     * Reconcile all (stockItem, location) dimensions for a tenant.
     *
     * @param tenantId tenant ID (required)
     * @return reconciliation report
     */
    ReconcileReportRespDTO reconcileAll(Long tenantId);

    /**
     * Reconcile all locations for a specific stock item within a tenant.
     *
     * @param tenantId    tenant ID (required)
     * @param stockItemId stock item ID
     * @return reconciliation report
     */
    ReconcileReportRespDTO reconcileByItem(Long tenantId, Long stockItemId);

    /**
     * Reconcile a single (stockItem, location) dimension.
     *
     * @param tenantId    tenant ID (required)
     * @param stockItemId stock item ID
     * @param locationId  location ID
     * @return reconciliation report (single dimension)
     */
    ReconcileReportRespDTO reconcileByItemLocation(Long tenantId, Long stockItemId, Long locationId);
}
