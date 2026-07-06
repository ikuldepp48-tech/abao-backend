package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StockItemService}.
 *
 * <p>Covers: AC-16 (stock_item CRUD, tenant isolation).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_item_svc_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockItemServiceTest {

    @Autowired
    private StockItemService stockItemService;
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
    void createItem_returnsId() {
        StockItemDO item = new StockItemDO();
        item.setTenantId(1L);
        item.setSkuCode("SKU-001");
        item.setItemName("Chicken Breast");
        item.setUnit("kg");

        Long id = stockItemService.createItem(item);
        assertThat(id).isNotNull();
    }

    @Test
    void getById_returnsItem() {
        StockItemDO item = new StockItemDO();
        item.setTenantId(1L);
        item.setSkuCode("SKU-002");
        item.setItemName("Beef");
        item.setUnit("kg");

        Long id = stockItemService.createItem(item);

        StockItemDO found = stockItemService.getById(id, 1L);
        assertThat(found).isNotNull();
        assertThat(found.getItemName()).isEqualTo("Beef");
    }

    @Test
    void getById_wrongTenantReturnsNull() {
        StockItemDO item = new StockItemDO();
        item.setTenantId(1L);
        item.setSkuCode("SKU-003");
        item.setItemName("Pork");
        item.setUnit("kg");

        Long id = stockItemService.createItem(item);

        StockItemDO notFound = stockItemService.getById(id, 999L);
        assertThat(notFound).isNull();
    }

    @Test
    void getBySkuCode_returnsItem() {
        StockItemDO item = new StockItemDO();
        item.setTenantId(1L);
        item.setSkuCode("SKU-SPECIAL");
        item.setItemName("Special Item");
        item.setUnit("个");

        stockItemService.createItem(item);

        StockItemDO found = stockItemService.getBySkuCode("SKU-SPECIAL", 1L);
        assertThat(found).isNotNull();
        assertThat(found.getItemName()).isEqualTo("Special Item");
    }

    @Test
    void listByTenant_returnsAllForTenant() {
        StockItemDO item1 = new StockItemDO();
        item1.setTenantId(1L);
        item1.setSkuCode("SKU-A");
        item1.setItemName("Item A");
        item1.setUnit("kg");
        stockItemService.createItem(item1);

        StockItemDO item2 = new StockItemDO();
        item2.setTenantId(1L);
        item2.setSkuCode("SKU-B");
        item2.setItemName("Item B");
        item2.setUnit("个");
        stockItemService.createItem(item2);

        List<StockItemDO> items = stockItemService.listByTenant(1L);
        assertThat(items).hasSize(2);
    }
}
