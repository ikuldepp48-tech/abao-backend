package com.geihou.module.supplychain.stock.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLocationDO;
import com.geihou.module.supplychain.stock.service.StockLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * Admin controller for stock_location mapping governance.
 *
 * <p>Provides CRUD + enable/disable for stock_location store_id/location_type mappings.
 * All operations are tenant-isolated via explicit tenantId parameter.
 */
@RestController
@RequestMapping("/admin/stock/stock-location-mapping")
public class StockLocationMappingController {

    @Autowired
    private StockLocationService stockLocationService;

    /**
     * Create a new stock location mapping.
     */
    @PostMapping
    public CommonResult<Long> create(@RequestBody StockLocationMappingCreateReq req) {
        Objects.requireNonNull(req.getTenantId(), "tenantId must not be null");
        Objects.requireNonNull(req.getLocationCode(), "locationCode must not be null");
        Objects.requireNonNull(req.getLocationName(), "locationName must not be null");
        Objects.requireNonNull(req.getLocationType(), "locationType must not be null");

        StockLocationDO location = new StockLocationDO();
        location.setTenantId(req.getTenantId());
        location.setLocationCode(req.getLocationCode());
        location.setLocationName(req.getLocationName());
        location.setLocationType(req.getLocationType());
        location.setStoreId(req.getStoreId());
        location.setIsActive(req.getIsActive());

        Long id = stockLocationService.createLocation(location);
        return CommonResult.success(id);
    }

    /**
     * Get stock location by id (tenant-isolated).
     */
    @GetMapping("/{id}")
    public CommonResult<StockLocationDO> getById(@PathVariable Long id,
                                                   @RequestParam Long tenantId) {
        StockLocationDO location = stockLocationService.getById(id, tenantId);
        return CommonResult.success(location);
    }

    /**
     * Get stock location by store_id + location_type (tenant-isolated, active only).
     */
    @GetMapping("/by-store")
    public CommonResult<StockLocationDO> getByStoreIdAndType(@RequestParam Long tenantId,
                                                               @RequestParam Long storeId,
                                                               @RequestParam String locationType) {
        StockLocationDO location = stockLocationService.getByStoreIdAndType(tenantId, storeId, locationType);
        return CommonResult.success(location);
    }

    /**
     * List all stock locations for a tenant.
     */
    @GetMapping("/list")
    public CommonResult<List<StockLocationDO>> list(@RequestParam Long tenantId) {
        List<StockLocationDO> locations = stockLocationService.listByTenant(tenantId);
        return CommonResult.success(locations);
    }

    /**
     * Update stock location fields (tenant-isolated).
     */
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable Long id,
                                         @RequestParam Long tenantId,
                                         @RequestBody StockLocationMappingUpdateReq req) {
        StockLocationDO location = new StockLocationDO();
        location.setId(id);
        location.setTenantId(tenantId);
        location.setLocationName(req.getLocationName());
        location.setLocationType(req.getLocationType());
        location.setStoreId(req.getStoreId());
        location.setIsActive(req.getIsActive());

        boolean updated = stockLocationService.updateLocation(location);
        return CommonResult.success(updated);
    }

    /**
     * Enable or disable a stock location mapping (tenant-isolated).
     */
    @PutMapping("/{id}/active")
    public CommonResult<Boolean> setActive(@PathVariable Long id,
                                            @RequestParam Long tenantId,
                                            @RequestParam Boolean isActive) {
        boolean updated = stockLocationService.setActive(id, tenantId, isActive);
        return CommonResult.success(updated);
    }

    // --- Request VOs ---

    public static class StockLocationMappingCreateReq {
        private Long tenantId;
        private String locationCode;
        private String locationName;
        private String locationType;
        private Long storeId;
        private Boolean isActive;

        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getLocationCode() { return locationCode; }
        public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
        public String getLocationName() { return locationName; }
        public void setLocationName(String locationName) { this.locationName = locationName; }
        public String getLocationType() { return locationType; }
        public void setLocationType(String locationType) { this.locationType = locationType; }
        public Long getStoreId() { return storeId; }
        public void setStoreId(Long storeId) { this.storeId = storeId; }
        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    }

    public static class StockLocationMappingUpdateReq {
        private String locationName;
        private String locationType;
        private Long storeId;
        private Boolean isActive;

        public String getLocationName() { return locationName; }
        public void setLocationName(String locationName) { this.locationName = locationName; }
        public String getLocationType() { return locationType; }
        public void setLocationType(String locationType) { this.locationType = locationType; }
        public Long getStoreId() { return storeId; }
        public void setStoreId(Long storeId) { this.storeId = storeId; }
        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    }
}
