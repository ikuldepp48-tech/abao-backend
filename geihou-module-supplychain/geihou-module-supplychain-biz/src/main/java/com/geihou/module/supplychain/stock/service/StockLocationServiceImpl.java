package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.stock.dal.dataobject.StockLocationDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockLocationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link StockLocationService}.
 *
 * <p>CRUD + mapping governance for stock location master data.
 */
@Service
public class StockLocationServiceImpl implements StockLocationService {

    @Autowired
    private StockLocationMapper stockLocationMapper;

    @Override
    public Long createLocation(StockLocationDO location) {
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(location.getTenantId(), "tenantId must not be null");
        Objects.requireNonNull(location.getLocationCode(), "locationCode must not be null");
        Objects.requireNonNull(location.getLocationName(), "locationName must not be null");
        Objects.requireNonNull(location.getLocationType(), "locationType must not be null");

        // Business conflict: duplicate store_id + location_type within same tenant
        if (location.getStoreId() != null) {
            StockLocationDO existing = stockLocationMapper.selectByTenantStoreIdType(
                    location.getTenantId(), location.getStoreId(), location.getLocationType());
            if (existing != null) {
                throw new IllegalStateException(
                        "stock_location already exists for tenant_id=" + location.getTenantId()
                        + " store_id=" + location.getStoreId()
                        + " location_type=" + location.getLocationType());
            }
        }

        if (location.getIsActive() == null) {
            location.setIsActive(true);
        }
        location.setCreator("system");
        location.setCreateTime(LocalDateTime.now());
        location.setUpdater("system");
        location.setUpdateTime(LocalDateTime.now());
        location.setDeleted(false);

        stockLocationMapper.insert(location);
        return location.getId();
    }

    @Override
    public StockLocationDO getById(Long id, Long tenantId) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return stockLocationMapper.selectByIdAndTenant(id, tenantId);
    }

    @Override
    public StockLocationDO getByStoreIdAndType(Long tenantId, Long storeId, String locationType) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(storeId, "storeId must not be null");
        Objects.requireNonNull(locationType, "locationType must not be null");
        // Only return active locations — disabled mappings are invisible to StockQueryApi
        return stockLocationMapper.selectActiveByTenantStoreIdType(tenantId, storeId, locationType);
    }

    @Override
    public StockLocationDO getByStoreIdAndTypeIncludeInactive(Long tenantId, Long storeId, String locationType) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(storeId, "storeId must not be null");
        Objects.requireNonNull(locationType, "locationType must not be null");
        return stockLocationMapper.selectByTenantStoreIdType(tenantId, storeId, locationType);
    }

    @Override
    public List<StockLocationDO> listByTenant(Long tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return stockLocationMapper.listByTenant(tenantId);
    }

    @Override
    public boolean updateLocation(StockLocationDO location) {
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(location.getId(), "id must not be null");
        Objects.requireNonNull(location.getTenantId(), "tenantId must not be null");

        StockLocationDO existing = stockLocationMapper.selectByIdAndTenant(location.getId(), location.getTenantId());
        if (existing == null) {
            return false;
        }

        existing.setLocationName(location.getLocationName() != null ? location.getLocationName() : existing.getLocationName());
        existing.setLocationType(location.getLocationType() != null ? location.getLocationType() : existing.getLocationType());
        existing.setStoreId(location.getStoreId() != null ? location.getStoreId() : existing.getStoreId());
        existing.setIsActive(location.getIsActive() != null ? location.getIsActive() : existing.getIsActive());
        existing.setUpdater("system");
        existing.setUpdateTime(LocalDateTime.now());

        int rows = stockLocationMapper.updateById(existing);
        return rows > 0;
    }

    @Override
    public boolean setActive(Long id, Long tenantId, Boolean isActive) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(isActive, "isActive must not be null");

        int rows = stockLocationMapper.updateActiveByIdAndTenant(id, tenantId, isActive,
                "system", LocalDateTime.now());
        return rows > 0;
    }
}
