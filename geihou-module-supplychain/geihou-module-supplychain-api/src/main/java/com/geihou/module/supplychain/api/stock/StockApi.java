package com.geihou.module.supplychain.api.stock;

import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreRespDTO;
import com.geihou.module.supplychain.api.stock.dto.ProductCurrentCostRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCheckRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockHealthRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SkuQuantityDTO;

import java.util.List;

/**
 * Stock preflight check API contract (G2-02C).
 *
 * <p>Provides a read-only BOM-aware stock sufficiency check.
 * Given a list of order SKUs and quantities, this API:
 * <ol>
 *   <li>Resolves each SKU to a {@code product_master} finished product</li>
 *   <li>Explodes the BOM via {@link com.geihou.module.supplychain.api.bom.BomApi#explode}</li>
 *   <li>Aggregates RAW_MATERIAL leaf component requirements</li>
 *   <li>Maps each component to {@code stock_item} via {@code sku_code}</li>
 *   <li>Reads {@code stock_balance.available_qty} (summed across locations)</li>
 *   <li>Returns per-component SUFFICIENT / INSUFFICIENT / UNMAPPED status</li>
 * </ol>
 *
 * <p><b>Read-only</b>: this API does not write {@code stock_event},
 * {@code stock_balance}, or {@code stock_reserve}. No stock mutation occurs.
 *
 * <p>Source: TASK-G2-02C.
 */
public interface StockApi {

    /**
     * Read-only BOM-aware stock sufficiency preflight check.
     *
     * <p>For each input SKU, explodes the BOM tree, aggregates RAW_MATERIAL
     * leaf requirements, maps to stock_item, and checks available balance.
     *
     * @param tenantId tenant ID (required, for isolation)
     * @param items    list of SKU + quantity pairs to check
     * @return stock check result with per-component sufficiency status;
     *         never {@code null}
     */
    StockCheckRespDTO checkStock(Long tenantId, List<SkuQuantityDTO> items);

    /**
     * Read-only stock health summary.
     *
     * <p>Aggregates existing {@code stock_balance} and {@code stock_event}
     * rows for the given tenant into low-stock, negative-stock,
     * reserved-stock, and recent-event health indicators.
     *
     * <p><b>Read-only</b>: this method does not write stock events,
     * balances, reservations, or warning topics.
     *
     * @param tenantId tenant ID (required, for isolation)
     * @return stock health snapshot; never {@code null}
     */
    StockHealthRespDTO getStockHealth(Long tenantId);

    /**
     * Read-only current product cost estimate.
     *
     * <p>Uses active BOM raw-material requirements and existing
     * {@code stock_balance.avg_unit_cost}. This is an explanatory estimate for
     * contract completion, not G3-03 activity-based costing.
     *
     * @param tenantId tenant ID (required, for isolation)
     * @param productId product ID
     * @return current cost estimate with cost source and calculation mode
     */
    ProductCurrentCostRespDTO getProductCurrentCost(Long tenantId, Long productId);

    /**
     * Execute sales-out BOM reverse consumption.
     *
     * <p>Explodes the BOM of the given finished product to raw-material leaves
     * and records a {@code CONSUME_OUT} stock event for each leaf component.
     *
     * <p>This is a <b>write</b> operation that mutates stock_balance.
     *
     * @param req request DTO
     * @return response with created event IDs and per-component deduction rows
     */
    SalesOutBomReverseRespDTO salesOutWithBomReverse(SalesOutBomReverseReqDTO req);

    /**
     * Execute sales reverse restore.
     *
     * <p>Finds the original raw-material {@code CONSUME_OUT} events created by
     * {@link #salesOutWithBomReverse(SalesOutBomReverseReqDTO)} for the given
     * source sale, and creates matching inbound restore events.
     *
     * <p>This is a <b>write</b> operation that mutates stock_balance.
     *
     * @param req request DTO
     * @return response with restore event IDs and per-component rows
     */
    SalesReverseRestoreRespDTO salesReverseRestore(SalesReverseRestoreReqDTO req);
}
