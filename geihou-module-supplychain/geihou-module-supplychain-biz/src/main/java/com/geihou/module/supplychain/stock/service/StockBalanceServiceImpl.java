package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.StockBalanceRespDTO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Implementation of {@link StockBalanceService}.
 *
 * <p>Read-only balance queries. No methods to directly set available_qty or total_qty.
 * Balance changes only through {@link StockEventService#recordEvent}.
 *
 * <p>AC-2: stock_balance 不可裸改 — no setAvailableQty/setTotalQty in service interface.
 */
@Service
public class StockBalanceServiceImpl implements StockBalanceService {

    @Autowired
    private StockBalanceMapper stockBalanceMapper;

    @Override
    public BigDecimal getAvailableQty(Long tenantId, Long itemId, Long locationId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(locationId, "locationId must not be null");

        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(tenantId, itemId, locationId);
        if (balance == null) {
            return BigDecimal.ZERO;
        }
        return balance.getAvailableQty() != null ? balance.getAvailableQty() : BigDecimal.ZERO;
    }

    @Override
    public boolean checkAvailable(Long tenantId, Long itemId, Long locationId, BigDecimal requiredQty) {
        Objects.requireNonNull(requiredQty, "requiredQty must not be null");
        if (requiredQty.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        BigDecimal available = getAvailableQty(tenantId, itemId, locationId);
        return available.compareTo(requiredQty) >= 0;
    }

    @Override
    public StockBalanceRespDTO getBalance(Long tenantId, Long itemId, Long locationId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(locationId, "locationId must not be null");

        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(tenantId, itemId, locationId);
        if (balance == null) {
            return null;
        }

        StockBalanceRespDTO dto = new StockBalanceRespDTO();
        dto.setTenantId(balance.getTenantId());
        dto.setStockItemId(balance.getStockItemId());
        dto.setLocationId(balance.getLocationId());
        dto.setAvailableQty(balance.getAvailableQty());
        dto.setTotalQty(balance.getTotalQty());
        dto.setReservedQty(balance.getReservedQty());
        dto.setAvgUnitCost(balance.getAvgUnitCost());
        dto.setLastEventId(balance.getLastEventId());
        dto.setLastEventTime(balance.getLastEventTime());
        return dto;
    }

    @Override
    public BigDecimal getReservedQty(Long tenantId, Long itemId, Long locationId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(locationId, "locationId must not be null");

        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(tenantId, itemId, locationId);
        if (balance == null) {
            return BigDecimal.ZERO;
        }
        return balance.getReservedQty() != null ? balance.getReservedQty() : BigDecimal.ZERO;
    }
}
