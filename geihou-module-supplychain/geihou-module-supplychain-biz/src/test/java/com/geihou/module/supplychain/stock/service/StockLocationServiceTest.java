package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLocationDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StockLocationService}.
 *
 * <p>Covers: AC-16 (stock_location CRUD, tenant isolation).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_location_svc_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockLocationServiceTest {

    @Autowired
    private StockLocationService stockLocationService;
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
    void createLocation_returnsId() {
        StockLocationDO location = new StockLocationDO();
        location.setTenantId(1L);
        location.setLocationCode("WH-001");
        location.setLocationName("Main Warehouse");
        location.setLocationType("STORE");

        Long id = stockLocationService.createLocation(location);
        assertThat(id).isNotNull();
    }

    @Test
    void getById_returnsLocation() {
        StockLocationDO location = new StockLocationDO();
        location.setTenantId(1L);
        location.setLocationCode("WH-002");
        location.setLocationName("Store B");
        location.setLocationType("STORE");

        Long id = stockLocationService.createLocation(location);

        StockLocationDO found = stockLocationService.getById(id, 1L);
        assertThat(found).isNotNull();
        assertThat(found.getLocationName()).isEqualTo("Store B");
    }

    @Test
    void getById_wrongTenantReturnsNull() {
        StockLocationDO location = new StockLocationDO();
        location.setTenantId(1L);
        location.setLocationCode("WH-003");
        location.setLocationName("Store C");
        location.setLocationType("STORE");

        Long id = stockLocationService.createLocation(location);

        StockLocationDO notFound = stockLocationService.getById(id, 999L);
        assertThat(notFound).isNull();
    }

    @Test
    void listByTenant_returnsAllForTenant() {
        StockLocationDO loc1 = new StockLocationDO();
        loc1.setTenantId(1L);
        loc1.setLocationCode("WH-A");
        loc1.setLocationName("Location A");
        loc1.setLocationType("STORE");
        stockLocationService.createLocation(loc1);

        StockLocationDO loc2 = new StockLocationDO();
        loc2.setTenantId(1L);
        loc2.setLocationCode("WH-B");
        loc2.setLocationName("Location B");
        loc2.setLocationType("CENTRAL_KITCHEN");
        stockLocationService.createLocation(loc2);

        List<StockLocationDO> locations = stockLocationService.listByTenant(1L);
        assertThat(locations).hasSize(2);
    }
}
