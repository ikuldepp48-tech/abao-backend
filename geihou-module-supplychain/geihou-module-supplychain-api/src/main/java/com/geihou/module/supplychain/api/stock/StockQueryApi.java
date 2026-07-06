package com.geihou.module.supplychain.api.stock;

import com.geihou.module.supplychain.api.stock.dto.StockItemQueryRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockLocationQueryRespDTO;

/**
 * Stock query API contract (G2-01B2).
 *
 * <p>Provides read-only mapping queries for finance module to discover
 * stock_item and stock_location mappings by skuCode / storeId.
 *
 * <p>This is a pure Java contract in the supplychain API module.
 * The implementation is provided in geihou-module-supplychain-biz.
 *
 * <p>Source: TASK-G2-01B2 Section 10.4
 */
public interface StockQueryApi {

    /**
     * Query stock_item by skuCode (tenant-isolated).
     *
     * @param tenantId tenant ID
     * @param skuCode  SKU code
     * @return StockItemQueryRespDTO or null if no mapping configured
     */
    StockItemQueryRespDTO getStockItemBySkuCode(Long tenantId, String skuCode);

    /**
     * Query stock_location by storeId + locationType (tenant-isolated).
     *
     * @param tenantId     tenant ID
     * @param storeId      store ID (maps to shopId in finance)
     * @param locationType location type (e.g. "STORE")
     * @return StockLocationQueryRespDTO or null if no mapping configured
     */
    StockLocationQueryRespDTO getStockLocationByStoreId(Long tenantId, Long storeId, String locationType);
}
