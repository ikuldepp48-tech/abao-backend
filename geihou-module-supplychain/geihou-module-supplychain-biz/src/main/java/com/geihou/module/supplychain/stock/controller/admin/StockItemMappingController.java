package com.geihou.module.supplychain.stock.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.service.StockItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Admin controller for stock_item mapping governance.
 *
 * <p>Provides CRUD + enable/disable for stock_item sku_code mappings.
 * All operations are tenant-isolated via explicit tenantId parameter.
 */
@RestController
@RequestMapping("/admin/stock/stock-item-mapping")
public class StockItemMappingController {

    @Autowired
    private StockItemService stockItemService;

    /**
     * Create a new stock item mapping.
     */
    @PostMapping
    public CommonResult<Long> create(@RequestBody StockItemMappingCreateReq req) {
        Objects.requireNonNull(req.getTenantId(), "tenantId must not be null");
        Objects.requireNonNull(req.getSkuCode(), "skuCode must not be null");
        Objects.requireNonNull(req.getItemName(), "itemName must not be null");
        Objects.requireNonNull(req.getUnit(), "unit must not be null");

        StockItemDO item = new StockItemDO();
        item.setTenantId(req.getTenantId());
        item.setSkuCode(req.getSkuCode());
        item.setItemName(req.getItemName());
        item.setCategory(req.getCategory());
        item.setUnit(req.getUnit());
        item.setShelfLifeDays(req.getShelfLifeDays());
        item.setStorageCondition(req.getStorageCondition());
        item.setIsRawMaterial(req.getIsRawMaterial());
        item.setIsSemiFinished(req.getIsSemiFinished());
        item.setIsFinished(req.getIsFinished());

        Long id = stockItemService.createItem(item);
        return CommonResult.success(id);
    }

    /**
     * Get stock item by id (tenant-isolated).
     */
    @GetMapping("/{id}")
    public CommonResult<StockItemDO> getById(@PathVariable Long id,
                                              @RequestParam Long tenantId) {
        StockItemDO item = stockItemService.getById(id, tenantId);
        return CommonResult.success(item);
    }

    /**
     * Get stock item by sku_code (tenant-isolated, active only).
     */
    @GetMapping("/by-sku-code")
    public CommonResult<StockItemDO> getBySkuCode(@RequestParam String skuCode,
                                                    @RequestParam Long tenantId) {
        StockItemDO item = stockItemService.getBySkuCode(skuCode, tenantId);
        return CommonResult.success(item);
    }

    /**
     * List all stock items for a tenant.
     */
    @GetMapping("/list")
    public CommonResult<List<StockItemDO>> list(@RequestParam Long tenantId) {
        List<StockItemDO> items = stockItemService.listByTenant(tenantId);
        return CommonResult.success(items);
    }

    /**
     * Update stock item fields (tenant-isolated).
     */
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable Long id,
                                         @RequestParam Long tenantId,
                                         @RequestBody StockItemMappingUpdateReq req) {
        StockItemDO item = new StockItemDO();
        item.setId(id);
        item.setTenantId(tenantId);
        item.setItemName(req.getItemName());
        item.setCategory(req.getCategory());
        item.setUnit(req.getUnit());
        item.setShelfLifeDays(req.getShelfLifeDays());
        item.setStorageCondition(req.getStorageCondition());
        item.setIsRawMaterial(req.getIsRawMaterial());
        item.setIsSemiFinished(req.getIsSemiFinished());
        item.setIsFinished(req.getIsFinished());
        item.setIsActive(req.getIsActive());

        boolean updated = stockItemService.updateItem(item);
        return CommonResult.success(updated);
    }

    /**
     * Enable or disable a stock item mapping (tenant-isolated).
     */
    @PutMapping("/{id}/active")
    public CommonResult<Boolean> setActive(@PathVariable Long id,
                                            @RequestParam Long tenantId,
                                            @RequestParam Boolean isActive) {
        boolean updated = stockItemService.setActive(id, tenantId, isActive);
        return CommonResult.success(updated);
    }

    // --- Request VOs ---

    public static class StockItemMappingCreateReq {
        private Long tenantId;
        private String skuCode;
        private String itemName;
        private String category;
        private String unit;
        private Integer shelfLifeDays;
        private String storageCondition;
        private Boolean isRawMaterial;
        private Boolean isSemiFinished;
        private Boolean isFinished;

        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getSkuCode() { return skuCode; }
        public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public Integer getShelfLifeDays() { return shelfLifeDays; }
        public void setShelfLifeDays(Integer shelfLifeDays) { this.shelfLifeDays = shelfLifeDays; }
        public String getStorageCondition() { return storageCondition; }
        public void setStorageCondition(String storageCondition) { this.storageCondition = storageCondition; }
        public Boolean getIsRawMaterial() { return isRawMaterial; }
        public void setIsRawMaterial(Boolean isRawMaterial) { this.isRawMaterial = isRawMaterial; }
        public Boolean getIsSemiFinished() { return isSemiFinished; }
        public void setIsSemiFinished(Boolean isSemiFinished) { this.isSemiFinished = isSemiFinished; }
        public Boolean getIsFinished() { return isFinished; }
        public void setIsFinished(Boolean isFinished) { this.isFinished = isFinished; }
    }

    public static class StockItemMappingUpdateReq {
        private String itemName;
        private String category;
        private String unit;
        private Integer shelfLifeDays;
        private String storageCondition;
        private Boolean isRawMaterial;
        private Boolean isSemiFinished;
        private Boolean isFinished;
        private Boolean isActive;

        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public Integer getShelfLifeDays() { return shelfLifeDays; }
        public void setShelfLifeDays(Integer shelfLifeDays) { this.shelfLifeDays = shelfLifeDays; }
        public String getStorageCondition() { return storageCondition; }
        public void setStorageCondition(String storageCondition) { this.storageCondition = storageCondition; }
        public Boolean getIsRawMaterial() { return isRawMaterial; }
        public void setIsRawMaterial(Boolean isRawMaterial) { this.isRawMaterial = isRawMaterial; }
        public Boolean getIsSemiFinished() { return isSemiFinished; }
        public void setIsSemiFinished(Boolean isSemiFinished) { this.isSemiFinished = isSemiFinished; }
        public Boolean getIsFinished() { return isFinished; }
        public void setIsFinished(Boolean isFinished) { this.isFinished = isFinished; }
        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    }
}
