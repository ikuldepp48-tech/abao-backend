package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;

import java.util.List;

/**
 * Stock item service interface (CRUD + mapping governance).
 *
 * <p>Internal use within supplychain module. Admin controllers delegate here.
 */
public interface StockItemService {

    /**
     * Create a stock item.
     *
     * <p>Throws IllegalStateException if an active item with the same
     * tenant_id + sku_code already exists (business conflict, not idempotent).
     *
     * @param item item DO
     * @return created item ID
     */
    Long createItem(StockItemDO item);

    /**
     * Get item by ID (tenant-isolated).
     *
     * @param id       item ID
     * @param tenantId tenant ID
     * @return item DO, or null if not found
     */
    StockItemDO getById(Long id, Long tenantId);

    /**
     * Get item by SKU code (tenant-isolated).
     *
     * <p>Returns only active items. Inactive items are treated as
     * "not configured" so that StockQueryApi does not return disabled mappings.
     *
     * @param skuCode  SKU code
     * @param tenantId tenant ID
     * @return active item DO, or null if not found or inactive
     */
    StockItemDO getBySkuCode(String skuCode, Long tenantId);

    /**
     * Get item by SKU code including inactive ones (for admin governance).
     *
     * @param skuCode  SKU code
     * @param tenantId tenant ID
     * @return item DO (active or inactive), or null if not found
     */
    StockItemDO getBySkuCodeIncludeInactive(String skuCode, Long tenantId);

    /**
     * List all items for a tenant.
     *
     * @param tenantId tenant ID
     * @return list of items
     */
    List<StockItemDO> listByTenant(Long tenantId);

    /**
     * Update a stock item (tenant-isolated).
     *
     * @param item item DO with updated fields (id + tenantId required)
     * @return true if updated, false if not found or tenant mismatch
     */
    boolean updateItem(StockItemDO item);

    /**
     * Enable or disable a stock item (tenant-isolated).
     *
     * @param id       item ID
     * @param tenantId tenant ID
     * @param isActive true to enable, false to disable
     * @return true if updated, false if not found or tenant mismatch
     */
    boolean setActive(Long id, Long tenantId, Boolean isActive);
}
