package com.geihou.module.supplychain.bom.service;

import com.geihou.module.supplychain.api.bom.enums.ProductTypeEnum;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.dal.mapper.ProductMasterMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link ProductMasterService}.
 *
 * <p>CRUD + deactivation for product master data.
 *
 * <p>Source: TASK-G2-02A.
 */
@Service
public class ProductMasterServiceImpl implements ProductMasterService {

    @Autowired
    private ProductMasterMapper productMasterMapper;

    @Override
    public Long createProduct(ProductMasterDO product) {
        Objects.requireNonNull(product, "product must not be null");
        Objects.requireNonNull(product.getTenantId(), "tenantId must not be null");
        Objects.requireNonNull(product.getProductCode(), "productCode must not be null");
        Objects.requireNonNull(product.getProductName(), "productName must not be null");
        Objects.requireNonNull(product.getProductType(), "productType must not be null");
        Objects.requireNonNull(product.getUnit(), "unit must not be null");

        // Validate product type
        ProductTypeEnum.fromCode(product.getProductType());

        // Business conflict: duplicate product_code within same tenant
        ProductMasterDO existing = productMasterMapper.selectByTenantProductCode(
                product.getTenantId(), product.getProductCode());
        if (existing != null) {
            throw new IllegalStateException(
                    "product_master already exists for tenant_id=" + product.getTenantId()
                    + " product_code=" + product.getProductCode());
        }

        if (product.getIsActive() == null) {
            product.setIsActive(true);
        }
        product.setCreator("system");
        product.setCreateTime(LocalDateTime.now());
        product.setUpdater("system");
        product.setUpdateTime(LocalDateTime.now());
        product.setDeleted(false);

        productMasterMapper.insert(product);
        return product.getId();
    }

    @Override
    public boolean updateProduct(ProductMasterDO product) {
        Objects.requireNonNull(product, "product must not be null");
        Objects.requireNonNull(product.getId(), "id must not be null");
        Objects.requireNonNull(product.getTenantId(), "tenantId must not be null");

        ProductMasterDO existing = productMasterMapper.selectByIdAndTenant(product.getId(), product.getTenantId());
        if (existing == null) {
            return false;
        }

        // Update allowed mutable fields (productCode and productType are immutable)
        existing.setProductName(product.getProductName() != null ? product.getProductName() : existing.getProductName());
        existing.setSkuCode(product.getSkuCode() != null ? product.getSkuCode() : existing.getSkuCode());
        existing.setUnit(product.getUnit() != null ? product.getUnit() : existing.getUnit());
        existing.setCategory(product.getCategory() != null ? product.getCategory() : existing.getCategory());
        existing.setDescription(product.getDescription() != null ? product.getDescription() : existing.getDescription());
        existing.setUpdater("system");
        existing.setUpdateTime(LocalDateTime.now());

        int rows = productMasterMapper.updateMutableByIdAndTenant(
                existing.getId(), existing.getTenantId(),
                existing.getProductName(), existing.getSkuCode(),
                existing.getUnit(), existing.getCategory(),
                existing.getDescription(), existing.getUpdater(),
                existing.getUpdateTime());
        return rows > 0;
    }

    @Override
    public ProductMasterDO getById(Long id, Long tenantId) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return productMasterMapper.selectByIdAndTenant(id, tenantId);
    }

    @Override
    public List<ProductMasterDO> listByTenant(Long tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return productMasterMapper.listByTenant(tenantId);
    }

    @Override
    public boolean deactivate(Long id, Long tenantId) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        int rows = productMasterMapper.updateActiveByIdAndTenant(id, tenantId, false,
                "system", LocalDateTime.now());
        return rows > 0;
    }
}
