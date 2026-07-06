package com.geihou.module.supplychain.stock.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLocationDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockLocationMapper;
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
 * Tests for {@link StockLocationMapper}.
 *
 * <p>Covers: AC-2 (stock_location table with UNIQUE(tenant_id, location_code)),
 * AC-16 (tenant isolation).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_location_mapper_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockLocationMapperTest {

    @Autowired
    private StockLocationMapper stockLocationMapper;
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
    void insertAndSelectByTenantCode() {
        StockLocationDO location = buildLocation(1L, "WH-001", "Main Warehouse");
        stockLocationMapper.insert(location);

        StockLocationDO found = stockLocationMapper.selectByTenantCode(1L, "WH-001");
        assertThat(found).isNotNull();
        assertThat(found.getLocationName()).isEqualTo("Main Warehouse");
    }

    @Test
    void selectByIdAndTenant_tenantIsolation() {
        StockLocationDO location = buildLocation(1L, "WH-002", "Store A");
        stockLocationMapper.insert(location);

        StockLocationDO found = stockLocationMapper.selectByIdAndTenant(location.getId(), 1L);
        assertThat(found).isNotNull();

        StockLocationDO notFound = stockLocationMapper.selectByIdAndTenant(location.getId(), 999L);
        assertThat(notFound).isNull();
    }

    @Test
    void listByTenant_returnsOnlySameTenant() {
        TenantContextHolder.setTenantId(1L);
        stockLocationMapper.insert(buildLocation(1L, "WH-003", "Location 1"));
        stockLocationMapper.insert(buildLocation(1L, "WH-004", "Location 2"));

        TenantContextHolder.setTenantId(2L);
        stockLocationMapper.insert(buildLocation(2L, "WH-003", "Other Tenant Location"));

        TenantContextHolder.setTenantId(1L);
        List<StockLocationDO> tenant1Locations = stockLocationMapper.listByTenant(1L);
        assertThat(tenant1Locations).hasSize(2);

        TenantContextHolder.setTenantId(2L);
        List<StockLocationDO> tenant2Locations = stockLocationMapper.listByTenant(2L);
        assertThat(tenant2Locations).hasSize(1);
    }

    private StockLocationDO buildLocation(Long tenantId, String code, String name) {
        StockLocationDO location = new StockLocationDO();
        location.setTenantId(tenantId);
        location.setLocationCode(code);
        location.setLocationName(name);
        location.setLocationType("STORE");
        location.setIsActive(true);
        location.setCreator("test");
        location.setCreateTime(LocalDateTime.now());
        location.setUpdater("test");
        location.setUpdateTime(LocalDateTime.now());
        location.setDeleted(false);
        return location;
    }
}
