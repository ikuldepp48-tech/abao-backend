package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link StockItemService}.
 *
 * <p>CRUD + mapping governance for stock item master data.
 */
@Service
public class StockItemServiceImpl implements StockItemService {

    @Autowired
    private StockItemMapper stockItemMapper;

    @Override
    public Long createItem(StockItemDO item) {
        Objects.requireNonNull(item, "item must not be null");
        Objects.requireNonNull(item.getTenantId(), "tenantId must not be null");
        Objects.requireNonNull(item.getSkuCode(), "skuCode must not be null");
        Objects.requireNonNull(item.getItemName(), "itemName must not be null");
        Objects.requireNonNull(item.getUnit(), "unit must not be null");

        // Business conflict: duplicate sku_code within same tenant
        StockItemDO existing = stockItemMapper.selectByTenantSkuCode(item.getTenantId(), item.getSkuCode());
        if (existing != null) {
            throw new IllegalStateException(
                    "stock_item already exists for tenant_id=" + item.getTenantId()
                    + " sku_code=" + item.getSkuCode());
        }

        if (item.getIsRawMaterial() == null) {
            item.setIsRawMaterial(false);
        }
        if (item.getIsSemiFinished() == null) {
            item.setIsSemiFinished(false);
        }
        if (item.getIsFinished() == null) {
            item.setIsFinished(false);
        }
        if (item.getIsActive() == null) {
            item.setIsActive(true);
        }
        item.setCreator("system");
        item.setCreateTime(LocalDateTime.now());
        item.setUpdater("system");
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleted(false);

        stockItemMapper.insert(item);
        return item.getId();
    }

    @Override
    public StockItemDO getById(Long id, Long tenantId) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return stockItemMapper.selectByIdAndTenant(id, tenantId);
    }

    @Override
    public StockItemDO getBySkuCode(String skuCode, Long tenantId) {
        Objects.requireNonNull(skuCode, "skuCode must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        // Only return active items — disabled mappings are invisible to StockQueryApi
        return stockItemMapper.selectActiveByTenantSkuCode(tenantId, skuCode);
    }

    @Override
    public StockItemDO getBySkuCodeIncludeInactive(String skuCode, Long tenantId) {
        Objects.requireNonNull(skuCode, "skuCode must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return stockItemMapper.selectByTenantSkuCode(tenantId, skuCode);
    }

    @Override
    public List<StockItemDO> listByTenant(Long tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return stockItemMapper.listByTenant(tenantId);
    }

    @Override
    public boolean updateItem(StockItemDO item) {
        Objects.requireNonNull(item, "item must not be null");
        Objects.requireNonNull(item.getId(), "id must not be null");
        Objects.requireNonNull(item.getTenantId(), "tenantId must not be null");

        StockItemDO existing = stockItemMapper.selectByIdAndTenant(item.getId(), item.getTenantId());
        if (existing == null) {
            return false;
        }

        // Update allowed fields
        existing.setItemName(item.getItemName() != null ? item.getItemName() : existing.getItemName());
        existing.setCategory(item.getCategory() != null ? item.getCategory() : existing.getCategory());
        existing.setUnit(item.getUnit() != null ? item.getUnit() : existing.getUnit());
        existing.setShelfLifeDays(item.getShelfLifeDays() != null ? item.getShelfLifeDays() : existing.getShelfLifeDays());
        existing.setStorageCondition(item.getStorageCondition() != null ? item.getStorageCondition() : existing.getStorageCondition());
        existing.setIsRawMaterial(item.getIsRawMaterial() != null ? item.getIsRawMaterial() : existing.getIsRawMaterial());
        existing.setIsSemiFinished(item.getIsSemiFinished() != null ? item.getIsSemiFinished() : existing.getIsSemiFinished());
        existing.setIsFinished(item.getIsFinished() != null ? item.getIsFinished() : existing.getIsFinished());
        existing.setIsActive(item.getIsActive() != null ? item.getIsActive() : existing.getIsActive());
        existing.setUpdater("system");
        existing.setUpdateTime(LocalDateTime.now());

        int rows = stockItemMapper.updateById(existing);
        return rows > 0;
    }

    @Override
    public boolean setActive(Long id, Long tenantId, Boolean isActive) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(isActive, "isActive must not be null");

        int rows = stockItemMapper.updateActiveByIdAndTenant(id, tenantId, isActive,
                "system", LocalDateTime.now());
        return rows > 0;
    }
}
