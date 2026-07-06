package com.geihou.module.supplychain.bom.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.service.ProductMasterService;
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
 * Admin controller for product master management.
 *
 * <p>Provides CRUD + deactivate for product/material master data.
 * All operations are tenant-isolated via explicit tenantId parameter.
 *
 * <p>Source: TASK-G2-02A.
 */
@RestController
@RequestMapping("/admin/bom/product-master")
public class ProductMasterController {

    @Autowired
    private ProductMasterService productMasterService;

    /**
     * Create a new product master.
     */
    @PostMapping
    public CommonResult<Long> create(@RequestBody ProductMasterCreateReq req) {
        Objects.requireNonNull(req.getTenantId(), "tenantId must not be null");
        Objects.requireNonNull(req.getProductCode(), "productCode must not be null");
        Objects.requireNonNull(req.getProductName(), "productName must not be null");
        Objects.requireNonNull(req.getProductType(), "productType must not be null");
        Objects.requireNonNull(req.getUnit(), "unit must not be null");

        ProductMasterDO product = new ProductMasterDO();
        product.setTenantId(req.getTenantId());
        product.setProductCode(req.getProductCode());
        product.setProductName(req.getProductName());
        product.setProductType(req.getProductType());
        product.setSkuCode(req.getSkuCode());
        product.setUnit(req.getUnit());
        product.setCategory(req.getCategory());
        product.setDescription(req.getDescription());

        Long id = productMasterService.createProduct(product);
        return CommonResult.success(id);
    }

    /**
     * Get product by id (tenant-isolated).
     */
    @GetMapping("/{id}")
    public CommonResult<ProductMasterDO> getById(@PathVariable Long id,
                                                  @RequestParam Long tenantId) {
        ProductMasterDO product = productMasterService.getById(id, tenantId);
        return CommonResult.success(product);
    }

    /**
     * List all products for a tenant.
     */
    @GetMapping("/list")
    public CommonResult<List<ProductMasterDO>> list(@RequestParam Long tenantId) {
        List<ProductMasterDO> products = productMasterService.listByTenant(tenantId);
        return CommonResult.success(products);
    }

    /**
     * Update product fields (tenant-isolated).
     */
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable Long id,
                                         @RequestParam Long tenantId,
                                         @RequestBody ProductMasterUpdateReq req) {
        ProductMasterDO product = new ProductMasterDO();
        product.setId(id);
        product.setTenantId(tenantId);
        product.setProductName(req.getProductName());
        product.setSkuCode(req.getSkuCode());
        product.setUnit(req.getUnit());
        product.setCategory(req.getCategory());
        product.setDescription(req.getDescription());

        boolean updated = productMasterService.updateProduct(product);
        return CommonResult.success(updated);
    }

    /**
     * Deactivate a product (tenant-isolated).
     */
    @PutMapping("/{id}/deactivate")
    public CommonResult<Boolean> deactivate(@PathVariable Long id,
                                             @RequestParam Long tenantId) {
        boolean updated = productMasterService.deactivate(id, tenantId);
        return CommonResult.success(updated);
    }

    // --- Request VOs ---

    public static class ProductMasterCreateReq {
        private Long tenantId;
        private String productCode;
        private String productName;
        private String productType;
        private String skuCode;
        private String unit;
        private String category;
        private String description;

        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getProductCode() { return productCode; }
        public void setProductCode(String productCode) { this.productCode = productCode; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getProductType() { return productType; }
        public void setProductType(String productType) { this.productType = productType; }
        public String getSkuCode() { return skuCode; }
        public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class ProductMasterUpdateReq {
        private String productName;
        private String skuCode;
        private String unit;
        private String category;
        private String description;

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getSkuCode() { return skuCode; }
        public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
