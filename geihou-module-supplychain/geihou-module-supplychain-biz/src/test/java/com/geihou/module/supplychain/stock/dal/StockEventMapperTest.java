package com.geihou.module.supplychain.stock.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StockEventMapper}.
 *
 * <p>Covers: AC-1 (INSERT-only — no update/delete methods declared).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_event_mapper_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockEventMapperTest {

    @Autowired
    private StockEventMapper stockEventMapper;
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
    void insertAndSelectById() {
        StockEventDO event = buildEventDO("PURCHASE_IN", "IN", new BigDecimal("10"));
        stockEventMapper.insert(event);

        StockEventDO found = stockEventMapper.selectById(event.getId());
        assertThat(found).isNotNull();
        assertThat(found.getEventType()).isEqualTo("PURCHASE_IN");
        assertThat(found.getQuantity()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void selectByClientRequestId() {
        StockEventDO event = buildEventDO("PURCHASE_IN", "IN", new BigDecimal("20"));
        event.setClientRequestId("test-req-id-123");
        stockEventMapper.insert(event);

        StockEventDO found = stockEventMapper.selectByClientRequestId(1L, "test-req-id-123");
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(event.getId());
    }

    @Test
    void selectByClientRequestId_crossTenantIsolation() {
        // Tenant 1 inserts with client_request_id "shared-req"
        TenantContextHolder.setTenantId(1L);
        StockEventDO event1 = buildEventDO("PURCHASE_IN", "IN", new BigDecimal("20"));
        event1.setTenantId(1L);
        event1.setClientRequestId("shared-req");
        stockEventMapper.insert(event1);

        // Tenant 2 inserts with the SAME client_request_id "shared-req"
        TenantContextHolder.setTenantId(2L);
        StockEventDO event2 = buildEventDO("PURCHASE_IN", "IN", new BigDecimal("99"));
        event2.setTenantId(2L);
        event2.setClientRequestId("shared-req");
        stockEventMapper.insert(event2);

        // Each tenant sees only its own event
        TenantContextHolder.setTenantId(1L);
        StockEventDO found1 = stockEventMapper.selectByClientRequestId(1L, "shared-req");
        assertThat(found1).isNotNull();
        assertThat(found1.getTenantId()).isEqualTo(1L);
        assertThat(found1.getQuantity()).isEqualByComparingTo(new BigDecimal("20"));

        TenantContextHolder.setTenantId(2L);
        StockEventDO found2 = stockEventMapper.selectByClientRequestId(2L, "shared-req");
        assertThat(found2).isNotNull();
        assertThat(found2.getTenantId()).isEqualTo(2L);
        assertThat(found2.getQuantity()).isEqualByComparingTo(new BigDecimal("99"));
    }

    @Test
    void selectByTenantItemLocation() {
        StockEventDO event1 = buildEventDO("PURCHASE_IN", "IN", new BigDecimal("10"));
        stockEventMapper.insert(event1);

        StockEventDO event2 = buildEventDO("CONSUME_OUT", "OUT", new BigDecimal("5"));
        stockEventMapper.insert(event2);

        List<StockEventDO> events = stockEventMapper.selectByTenantItemLocation(1L, 1001L, 10L);
        assertThat(events).hasSize(2);
    }

    @Test
    void selectByTenantItemLocation_sameEventTime_ordersByIdAsc() {
        LocalDateTime sameTime = LocalDateTime.now();
        // Insert two events with identical event_time for the same item+location
        StockEventDO event1 = buildEventDO("PURCHASE_IN", "IN", new BigDecimal("60"));
        event1.setEventTime(sameTime);
        stockEventMapper.insert(event1);

        StockEventDO event2 = buildEventDO("CONSUME_OUT", "OUT", new BigDecimal("30"));
        event2.setEventTime(sameTime);
        stockEventMapper.insert(event2);

        List<StockEventDO> events = stockEventMapper.selectByTenantItemLocation(1L, 1001L, 10L);

        assertThat(events).hasSize(2);
        // Deterministic ordering: id ASC is the tiebreaker when event_time is equal
        assertThat(events.get(0).getId()).isLessThan(events.get(1).getId());
        assertThat(events.get(0).getEventType()).isEqualTo("PURCHASE_IN");
        assertThat(events.get(1).getEventType()).isEqualTo("CONSUME_OUT");
    }

    @Test
    void mapperDeclaresNoUpdateOrDeleteMethods() {
        // Verify that StockEventMapper interface itself does not declare update/delete methods
        // (inherited methods from BaseMapper are not declared on this interface)
        Method[] declaredMethods = StockEventMapper.class.getDeclaredMethods();
        for (Method method : declaredMethods) {
            String methodName = method.getName().toLowerCase();
            assertThat(methodName).doesNotContain("update");
            assertThat(methodName).doesNotContain("delete");
        }
    }

    @Test
    void selectDistinctTenantIds() {
        // Insert events for tenant 1
        TenantContextHolder.setTenantId(1L);
        StockEventDO event1 = buildEventDO("PURCHASE_IN", "IN", new BigDecimal("10"));
        event1.setTenantId(1L);
        stockEventMapper.insert(event1);

        // Insert events for tenant 2 (bypass tenant context for cross-tenant insert)
        TenantContextHolder.setIgnore(true);
        try {
            StockEventDO event2 = buildEventDO("PURCHASE_IN", "IN", new BigDecimal("20"));
            event2.setTenantId(2L);
            stockEventMapper.insert(event2);
        } finally {
            TenantContextHolder.setIgnore(false);
        }

        // Query distinct tenant IDs (annotated with @TenantIgnore)
        TenantContextHolder.setIgnore(true);
        try {
            List<Long> tenantIds = stockEventMapper.selectDistinctTenantIds();
            assertThat(tenantIds).isNotNull();
            assertThat(tenantIds).containsExactlyInAnyOrder(1L, 2L);
        } finally {
            TenantContextHolder.setIgnore(false);
        }
    }

    private StockEventDO buildEventDO(String eventType, String direction, BigDecimal quantity) {
        StockEventDO event = new StockEventDO();
        event.setTenantId(1L);
        event.setEventTime(LocalDateTime.now());
        event.setBusinessDate(LocalDate.now());
        event.setEventType(eventType);
        event.setDirection(direction);
        event.setStockItemId(1001L);
        event.setSkuCode("SKU_TEST");
        event.setLocationId(10L);
        event.setQuantity(quantity);
        event.setUnit("个");
        event.setOperatorUserId(999L);
        event.setCreateTime(LocalDateTime.now());
        return event;
    }
}
