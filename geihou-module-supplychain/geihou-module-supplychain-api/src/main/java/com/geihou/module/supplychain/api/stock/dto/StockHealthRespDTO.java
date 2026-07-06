package com.geihou.module.supplychain.api.stock.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for stock health summary (G2-02G).
 *
 * <p>Read-only aggregation over stock_balance and stock_event
 * tables. Provides a quick snapshot of inventory health for a given tenant.
 *
 * <p>Dimensions:
 * <ul>
 *   <li>lowStockItemCount - balance rows where available_qty &lt;= min_threshold</li>
 *   <li>negativeStockItemCount - balance rows where available_qty &lt; 0</li>
 *   <li>reservedStockItemCount - balance rows where reserved_qty &gt; 0</li>
 *   <li>recentEventCount - stock events in the last 7 days</li>
 *   <li>latestEventTime - most recent event time across all stock events</li>
 * </ul>
 *
 * <p>Read-only: this DTO does not trigger any write operation.
 *
 * <p>Source: TASK-G2-02G Section 5.2.
 */
public class StockHealthRespDTO {

    /** Tenant ID used for this health snapshot. */
    private Long tenantId;

    /** Number of stock balance rows at or below their low-stock threshold. */
    private int lowStockItemCount;

    /** Number of stock balance rows with negative available quantity. */
    private int negativeStockItemCount;

    /** Number of stock balance rows with non-zero reserved quantity. */
    private int reservedStockItemCount;

    /** Number of stock events recorded in the last 7 days. */
    private int recentEventCount;

    /** Timestamp of the most recent stock event; null if no events exist. */
    private LocalDateTime latestEventTime;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public int getLowStockItemCount() { return lowStockItemCount; }
    public void setLowStockItemCount(int lowStockItemCount) { this.lowStockItemCount = lowStockItemCount; }

    public int getNegativeStockItemCount() { return negativeStockItemCount; }
    public void setNegativeStockItemCount(int negativeStockItemCount) { this.negativeStockItemCount = negativeStockItemCount; }

    public int getReservedStockItemCount() { return reservedStockItemCount; }
    public void setReservedStockItemCount(int reservedStockItemCount) { this.reservedStockItemCount = reservedStockItemCount; }

    public int getRecentEventCount() { return recentEventCount; }
    public void setRecentEventCount(int recentEventCount) { this.recentEventCount = recentEventCount; }

    public LocalDateTime getLatestEventTime() { return latestEventTime; }
    public void setLatestEventTime(LocalDateTime latestEventTime) { this.latestEventTime = latestEventTime; }
}
