package com.geihou.module.supplychain.bom.service;

import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;

import java.util.List;

/**
 * Product master service interface.
 *
 * <p>CRUD + deactivation for product/material master data.
 * All operations are tenant-isolated.
 *
 * <p>Source: TASK-G2-02A.
 */
public interface ProductMasterService {

    /**
     * Create a product master.
     *
     * <p>Throws IllegalStateException if product_code already exists within same tenant.
     *
     * @param product product DO
     * @return created product ID
     */
    Long createProduct(ProductMasterDO product);

    /**
     * Update a product master (tenant-isolated).
     *
     * @param product product DO with updated fields (id + tenantId required)
     * @return true if updated, false if not found or tenant mismatch
     */
    boolean updateProduct(ProductMasterDO product);

    /**
     * Get product by ID (tenant-isolated).
     *
     * @param id       product ID
     * @param tenantId tenant ID
     * @return product DO, or null if not found
     */
    ProductMasterDO getById(Long id, Long tenantId);

    /**
     * List all products for a tenant.
     *
     * @param tenantId tenant ID
     * @return list of products
     */
    List<ProductMasterDO> listByTenant(Long tenantId);

    /**
     * Deactivate a product (tenant-isolated).
     *
     * @param id       product ID
     * @param tenantId tenant ID
     * @return true if updated, false if not found or tenant mismatch
     */
    boolean deactivate(Long id, Long tenantId);
}
