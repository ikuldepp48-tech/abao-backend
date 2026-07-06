package com.geihou.module.supplychain.stock.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockItemMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StockItemMapper}.
 *
 * <p>Covers: AC-3 (stock_item table with UNIQUE(tenant_id, sku_code)),
 * AC-16 (tenant isolation).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_item_mapper_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockItemMapperTest {

    @Autowired
    private StockItemMapper stockItemMapper;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void insertAndSelectByTenantSkuCode() {
        StockItemDO item = buildItem(1L, "SKU-001", "Chicken Breast");
        stockItemMapper.insert(item);

        StockItemDO found = stockItemMapper.selectByTenantSkuCode(1L, "SKU-001");
        assertThat(found).isNotNull();
        assertThat(found.getItemName()).isEqualTo("Chicken Breast");
    }

    @Test
    void selectByIdAndTenant_tenantIsolation() {
        StockItemDO item = buildItem(1L, "SKU-002", "Beef");
        stockItemMapper.insert(item);

        StockItemDO found = stockItemMapper.selectByIdAndTenant(item.getId(), 1L);
        assertThat(found).isNotNull();

        StockItemDO notFound = stockItemMapper.selectByIdAndTenant(item.getId(), 999L);
        assertThat(notFound).isNull();
    }

    @Test
    void listByTenant_returnsOnlySameTenant() {
        TenantContextHolder.setTenantId(1L);
        stockItemMapper.insert(buildItem(1L, "SKU-A", "Item A"));
        stockItemMapper.insert(buildItem(1L, "SKU-B", "Item B"));

        TenantContextHolder.setTenantId(2L);
        stockItemMapper.insert(buildItem(2L, "SKU-A", "Other Tenant Item"));

        TenantContextHolder.setTenantId(1L);
        List<StockItemDO> tenant1Items = stockItemMapper.listByTenant(1L);
        assertThat(tenant1Items).hasSize(2);

        TenantContextHolder.setTenantId(2L);
        List<StockItemDO> tenant2Items = stockItemMapper.listByTenant(2L);
        assertThat(tenant2Items).hasSize(1);
    }

    private StockItemDO buildItem(Long tenantId, String skuCode, String name) {
        StockItemDO item = new StockItemDO();
        item.setTenantId(tenantId);
        item.setSkuCode(skuCode);
        item.setItemName(name);
        item.setUnit("kg");
        item.setIsRawMaterial(true);
        item.setIsSemiFinished(false);
        item.setIsFinished(false);
        item.setCreator("test");
        item.setCreateTime(LocalDateTime.now());
        item.setUpdater("test");
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleted(false);
        return item;
    }
}
