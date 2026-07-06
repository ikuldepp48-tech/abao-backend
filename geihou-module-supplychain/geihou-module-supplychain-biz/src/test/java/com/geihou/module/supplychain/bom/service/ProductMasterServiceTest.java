package com.geihou.module.supplychain.bom.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.bom.BomTestConfig;
import com.geihou.module.supplychain.bom.BomTestSchemaInitializer;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ProductMasterService}.
 *
 * <p>Covers: AC-2 (product master CRUD, tenant isolation, unique product_code).
 *
 * <p>Source: TASK-G2-02A.
 */
@SpringBootTest(
        classes = BomTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:bom_product_master_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ProductMasterServiceTest {

    @Autowired
    private ProductMasterService productMasterService;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        BomTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createProduct_returnsId() {
        ProductMasterDO product = newProduct(1L, "P-001", "Chicken Burger", "FINISHED", "kg");
        Long id = productMasterService.createProduct(product);
        assertThat(id).isNotNull();
    }

    @Test
    void createProduct_duplicateCodeThrows() {
        ProductMasterDO product = newProduct(1L, "P-001", "Chicken Burger", "FINISHED", "kg");
        productMasterService.createProduct(product);

        ProductMasterDO dup = newProduct(1L, "P-001", "Another Burger", "FINISHED", "kg");
        assertThatThrownBy(() -> productMasterService.createProduct(dup))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createProduct_invalidTypeThrows() {
        ProductMasterDO product = newProduct(1L, "P-BAD", "Bad Product", "INVALID_TYPE", "kg");
        assertThatThrownBy(() -> productMasterService.createProduct(product))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getById_returnsProduct() {
        ProductMasterDO product = newProduct(1L, "P-002", "Beef Burger", "FINISHED", "kg");
        Long id = productMasterService.createProduct(product);

        ProductMasterDO found = productMasterService.getById(id, 1L);
        assertThat(found).isNotNull();
        assertThat(found.getProductName()).isEqualTo("Beef Burger");
        assertThat(found.getProductType()).isEqualTo("FINISHED");
    }

    @Test
    void getById_wrongTenantReturnsNull() {
        ProductMasterDO product = newProduct(1L, "P-003", "Pork Burger", "FINISHED", "kg");
        Long id = productMasterService.createProduct(product);

        ProductMasterDO notFound = productMasterService.getById(id, 999L);
        assertThat(notFound).isNull();
    }

    @Test
    void listByTenant_returnsAllForTenant() {
        productMasterService.createProduct(newProduct(1L, "P-A", "Item A", "RAW_MATERIAL", "kg"));
        productMasterService.createProduct(newProduct(1L, "P-B", "Item B", "SEMI_FINISHED", "个"));

        List<ProductMasterDO> products = productMasterService.listByTenant(1L);
        assertThat(products).hasSize(2);
    }

    @Test
    void listByTenant_tenantIsolation() {
        TenantContextHolder.setTenantId(1L);
        productMasterService.createProduct(newProduct(1L, "P-T1", "Tenant 1 Item", "RAW_MATERIAL", "kg"));

        TenantContextHolder.setTenantId(2L);
        productMasterService.createProduct(newProduct(2L, "P-T2", "Tenant 2 Item", "RAW_MATERIAL", "kg"));

        TenantContextHolder.setTenantId(1L);
        List<ProductMasterDO> t1Products = productMasterService.listByTenant(1L);
        assertThat(t1Products).hasSize(1);
        assertThat(t1Products.get(0).getProductCode()).isEqualTo("P-T1");

        TenantContextHolder.setTenantId(2L);
        List<ProductMasterDO> t2Products = productMasterService.listByTenant(2L);
        assertThat(t2Products).hasSize(1);
        assertThat(t2Products.get(0).getProductCode()).isEqualTo("P-T2");
    }

    @Test
    void updateProduct_updatesMutableFields() {
        ProductMasterDO product = newProduct(1L, "P-UPD", "Original Name", "FINISHED", "kg");
        Long id = productMasterService.createProduct(product);

        ProductMasterDO update = new ProductMasterDO();
        update.setId(id);
        update.setTenantId(1L);
        update.setProductName("Updated Name");
        update.setUnit("个");

        boolean updated = productMasterService.updateProduct(update);
        assertThat(updated).isTrue();

        ProductMasterDO found = productMasterService.getById(id, 1L);
        assertThat(found.getProductName()).isEqualTo("Updated Name");
        assertThat(found.getUnit()).isEqualTo("个");
    }

    @Test
    void updateProduct_wrongTenantReturnsFalse() {
        ProductMasterDO product = newProduct(1L, "P-UPD2", "Original", "FINISHED", "kg");
        Long id = productMasterService.createProduct(product);

        ProductMasterDO update = new ProductMasterDO();
        update.setId(id);
        update.setTenantId(999L);
        update.setProductName("Hacked");

        boolean updated = productMasterService.updateProduct(update);
        assertThat(updated).isFalse();
    }

    @Test
    void deactivate_setsInactive() {
        ProductMasterDO product = newProduct(1L, "P-DEACT", "To Deactivate", "FINISHED", "kg");
        Long id = productMasterService.createProduct(product);

        boolean deactivated = productMasterService.deactivate(id, 1L);
        assertThat(deactivated).isTrue();

        ProductMasterDO found = productMasterService.getById(id, 1L);
        assertThat(found.getIsActive()).isFalse();
    }

    @Test
    void deactivate_wrongTenantReturnsFalse() {
        ProductMasterDO product = newProduct(1L, "P-DEACT2", "To Deactivate", "FINISHED", "kg");
        Long id = productMasterService.createProduct(product);

        boolean deactivated = productMasterService.deactivate(id, 999L);
        assertThat(deactivated).isFalse();
    }

    @Test
    void createProduct_allThreeTypesSucceed() {
        productMasterService.createProduct(newProduct(1L, "P-F", "Finished", "FINISHED", "kg"));
        productMasterService.createProduct(newProduct(1L, "P-S", "Semi", "SEMI_FINISHED", "个"));
        productMasterService.createProduct(newProduct(1L, "P-R", "Raw", "RAW_MATERIAL", "g"));

        List<ProductMasterDO> products = productMasterService.listByTenant(1L);
        assertThat(products).hasSize(3);
    }

    // --- Helper ---

    private ProductMasterDO newProduct(Long tenantId, String code, String name, String type, String unit) {
        ProductMasterDO p = new ProductMasterDO();
        p.setTenantId(tenantId);
        p.setProductCode(code);
        p.setProductName(name);
        p.setProductType(type);
        p.setUnit(unit);
        return p;
    }
}
