package com.geihou.module.supplychain.stock.check;

import com.geihou.module.supplychain.api.stock.dto.SkuQuantityDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCheckRespDTO;

import java.util.List;

/**
 * Read-only BOM-aware stock sufficiency preflight check service (G2-02C).
 *
 * <p>For each input SKU:
 * <ol>
 *   <li>Resolves the finished-product in {@code product_master} by skuCode/productCode</li>
 *   <li>Explodes the BOM tree via {@link com.geihou.module.supplychain.bom.explode.BomExplosionService}</li>
 *   <li>Aggregates RAW_MATERIAL leaf component requirements</li>
 *   <li>Maps each component to {@code stock_item} via {@code sku_code}</li>
 *   <li>Reads {@code stock_balance.available_qty} summed across locations</li>
 *   <li>Returns per-component SUFFICIENT / INSUFFICIENT / UNMAPPED status</li>
 * </ol>
 *
 * <p><b>Read-only</b>: does not write stock_event, stock_balance, or stock_reserve.
 *
 * <p>Source: TASK-G2-02C.
 */
public interface StockCheckService {

    /**
     * Read-only BOM-aware stock sufficiency preflight check.
     *
     * @param tenantId tenant ID (required, for isolation)
     * @param items    list of SKU + quantity pairs to check
     * @return stock check result with per-component sufficiency status; never {@code null}
     */
    StockCheckRespDTO checkStock(Long tenantId, List<SkuQuantityDTO> items);
}
