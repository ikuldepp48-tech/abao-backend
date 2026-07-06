package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.StockBalanceRespDTO;

import java.math.BigDecimal;

/**
 * Stock balance service interface.
 *
 * <p>Read-only balance queries. Balance changes only through StockEventService.recordEvent().
 */
public interface StockBalanceService {

    /**
     * Get available quantity for a specific item + location.
     *
     * @param tenantId   tenant ID
     * @param itemId     stock item ID
     * @param locationId location ID
     * @return available quantity (0 if no balance record exists)
     */
    BigDecimal getAvailableQty(Long tenantId, Long itemId, Long locationId);

    /**
     * Check whether stock is sufficient.
     *
     * @param tenantId     tenant ID
     * @param itemId       stock item ID
     * @param locationId   location ID
     * @param requiredQty  required quantity
     * @return true if available qty >= required qty
     */
    boolean checkAvailable(Long tenantId, Long itemId, Long locationId, BigDecimal requiredQty);

    /**
     * Get full balance info for a specific item + location.
     *
     * @param tenantId   tenant ID
     * @param itemId     stock item ID
     * @param locationId location ID
     * @return balance DTO, or null if no balance record exists
     */
    StockBalanceRespDTO getBalance(Long tenantId, Long itemId, Long locationId);

    /**
     * Get reserved quantity for a specific item + location.
     *
     * @param tenantId   tenant ID
     * @param itemId     stock item ID
     * @param locationId location ID
     * @return reserved quantity (0 if no balance record exists)
     */
    BigDecimal getReservedQty(Long tenantId, Long itemId, Long locationId);
}
