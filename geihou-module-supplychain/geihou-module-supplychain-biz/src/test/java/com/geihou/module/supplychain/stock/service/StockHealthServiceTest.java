package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.StockHealthRespDTO;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StockHealthService}.
 *
 * <p>Source: TASK-G2-02G.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_health_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockHealthServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;

    @Autowired
    private StockHealthService stockHealthService;
    @Autowired
    private StockBalanceMapper stockBalanceMapper;
    @Autowired
    private StockEventMapper stockEventMapper;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void getStockHealth_aggregatesTenantScopedHealthDimensions() {
        LocalDateTime now = LocalDateTime.now();
        insertBalance(TENANT_A, 101L, 10L, "3", "10", "0", "5");
        insertBalance(TENANT_A, 102L, 10L, "-1", "-1", "0", "0");
        insertBalance(TENANT_A, 103L, 10L, "20", "25", "4", "5");
        insertBalance(TENANT_B, 201L, 10L, "-9", "-9", "9", "10");
        insertEvent(TENANT_A, 101L, 10L, now.minusDays(1));
        insertEvent(TENANT_A, 102L, 10L, now.minusDays(9));
        insertEvent(TENANT_A, 103L, 10L, now.minusHours(2));
        insertEvent(TENANT_B, 201L, 10L, now.minusHours(1));

        TenantContextHolder.setTenantId(TENANT_A);
        StockHealthRespDTO result = stockHealthService.getStockHealth(TENANT_A);

        assertThat(result.getTenantId()).isEqualTo(TENANT_A);
        assertThat(result.getLowStockItemCount()).isEqualTo(2);
        assertThat(result.getNegativeStockItemCount()).isEqualTo(1);
        assertThat(result.getReservedStockItemCount()).isEqualTo(1);
        assertThat(result.getRecentEventCount()).isEqualTo(2);
        assertThat(result.getLatestEventTime()).isEqualToIgnoringNanos(now.minusHours(2));
    }

    @Test
    void getStockHealth_noRowsReturnsZeroSnapshot() {
        TenantContextHolder.setTenantId(TENANT_A);
        StockHealthRespDTO result = stockHealthService.getStockHealth(TENANT_A);

        assertThat(result.getTenantId()).isEqualTo(TENANT_A);
        assertThat(result.getLowStockItemCount()).isZero();
        assertThat(result.getNegativeStockItemCount()).isZero();
        assertThat(result.getReservedStockItemCount()).isZero();
        assertThat(result.getRecentEventCount()).isZero();
        assertThat(result.getLatestEventTime()).isNull();
    }

    private void insertBalance(Long tenantId, Long stockItemId, Long locationId,
                               String availableQty, String totalQty,
                               String reservedQty, String minThreshold) {
        StockBalanceDO balance = new StockBalanceDO();
        balance.setTenantId(tenantId);
        balance.setStockItemId(stockItemId);
        balance.setLocationId(locationId);
        balance.setAvailableQty(new BigDecimal(availableQty));
        balance.setTotalQty(new BigDecimal(totalQty));
        balance.setReservedQty(new BigDecimal(reservedQty));
        balance.setAvgUnitCost(BigDecimal.ZERO);
        balance.setMinThreshold(new BigDecimal(minThreshold));
        balance.setVersion(0);
        balance.setCreator("test");
        balance.setCreateTime(LocalDateTime.now());
        balance.setUpdater("test");
        balance.setUpdateTime(LocalDateTime.now());
        balance.setDeleted(false);
        stockBalanceMapper.insert(balance);
    }

    private void insertEvent(Long tenantId, Long stockItemId, Long locationId, LocalDateTime eventTime) {
        StockEventDO event = new StockEventDO();
        event.setTenantId(tenantId);
        event.setEventTime(eventTime);
        event.setBusinessDate(LocalDate.now());
        event.setEventType("PURCHASE_IN");
        event.setDirection("IN");
        event.setStockItemId(stockItemId);
        event.setSkuCode("SKU-" + stockItemId);
        event.setLocationId(locationId);
        event.setQuantity(BigDecimal.ONE);
        event.setUnit("kg");
        event.setClientRequestId("health-test-" + tenantId + "-" + stockItemId + "-" + eventTime);
        event.setOperatorUserId(1L);
        event.setCreateTime(eventTime);
        stockEventMapper.insert(event);
    }
}
