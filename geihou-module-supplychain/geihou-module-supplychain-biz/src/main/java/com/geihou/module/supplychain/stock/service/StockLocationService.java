package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.stock.dal.dataobject.StockLocationDO;

import java.util.List;

/**
 * Stock location service interface (CRUD + mapping governance).
 *
 * <p>Internal use within supplychain module. Admin controllers delegate here.
 */
public interface StockLocationService {

    /**
     * Create a stock location.
     *
     * <p>Throws IllegalStateException if a location with the same
     * tenant_id + store_id + location_type already exists (business conflict).
     *
     * @param location location DO
     * @return created location ID
     */
    Long createLocation(StockLocationDO location);

    /**
     * Get location by ID (tenant-isolated).
     *
     * @param id       location ID
     * @param tenantId tenant ID
     * @return location DO, or null if not found
     */
    StockLocationDO getById(Long id, Long tenantId);

    /**
     * Get location by tenant_id + store_id + location_type (tenant-isolated).
     *
     * <p>Returns only active locations.
     *
     * @param tenantId     tenant ID
     * @param storeId      store ID
     * @param locationType location type
     * @return active location DO, or null if not found or inactive
     */
    StockLocationDO getByStoreIdAndType(Long tenantId, Long storeId, String locationType);

    /**
     * Get location by tenant_id + store_id + location_type including inactive (for admin).
     *
     * @param tenantId     tenant ID
     * @param storeId      store ID
     * @param locationType location type
     * @return location DO (active or inactive), or null if not found
     */
    StockLocationDO getByStoreIdAndTypeIncludeInactive(Long tenantId, Long storeId, String locationType);

    /**
     * List all locations for a tenant.
     *
     * @param tenantId tenant ID
     * @return list of locations
     */
    List<StockLocationDO> listByTenant(Long tenantId);

    /**
     * Update a stock location (tenant-isolated).
     *
     * @param location location DO with updated fields (id + tenantId required)
     * @return true if updated, false if not found or tenant mismatch
     */
    boolean updateLocation(StockLocationDO location);

    /**
     * Enable or disable a stock location (tenant-isolated).
     *
     * @param id       location ID
     * @param tenantId tenant ID
     * @param isActive true to enable, false to disable
     * @return true if updated, false if not found or tenant mismatch
     */
    boolean setActive(Long id, Long tenantId, Boolean isActive);
}
