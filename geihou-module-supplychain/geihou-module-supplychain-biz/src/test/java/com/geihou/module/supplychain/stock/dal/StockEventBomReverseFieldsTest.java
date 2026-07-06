package com.geihou.module.supplychain.stock.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import com.geihou.module.supplychain.stock.service.StockEventService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused tests for G2-02D-pre: BOM reverse consumption nullable fields on stock_event.
 *
 * <p>AC-2: StockEventDO contains parentEventId, recipeId, recipeVersion.
 * <p>AC-3: StockEventReqDTO contains parentEventId, recipeId, recipeVersion.
 * <p>AC-4: buildEventDO passes through three fields from DTO to DO.
 * <p>AC-8: Fields are nullable — not setting them results in null.
 * <p>AC-9: selectByParentEventId is tenant-scoped (cross-tenant isolation).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:bom_reverse_fields_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockEventBomReverseFieldsTest {

    @Autowired
    private StockEventService stockEventService;
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

    // AC-2, AC-4, AC-8: record event WITH bom reverse fields, read back, verify values
    @Test
    void recordEvent_withBomReverseFields_fieldsPersistedAndPassedThrough() {
        StockEventReqDTO req = buildBaseReqDTO();
        req.setParentEventId(555L);
        req.setRecipeId(77L);
        req.setRecipeVersion(3);

        Long eventId = stockEventService.recordEvent(req);

        StockEventDO found = stockEventMapper.selectById(eventId);
        assertThat(found).isNotNull();
        assertThat(found.getParentEventId()).isEqualTo(555L);
        assertThat(found.getRecipeId()).isEqualTo(77L);
        assertThat(found.getRecipeVersion()).isEqualTo(3);
    }

    // AC-8: record event WITHOUT bom reverse fields, read back, verify null
    @Test
    void recordEvent_withoutBomReverseFields_fieldsAreNull() {
        StockEventReqDTO req = buildBaseReqDTO();
        // Do not set parentEventId / recipeId / recipeVersion

        Long eventId = stockEventService.recordEvent(req);

        StockEventDO found = stockEventMapper.selectById(eventId);
        assertThat(found).isNotNull();
        assertThat(found.getParentEventId()).isNull();
        assertThat(found.getRecipeId()).isNull();
        assertThat(found.getRecipeVersion()).isNull();
    }

    // AC-4: buildEventDO pass-through — verify via direct mapper insert and DTO field values
    @Test
    void buildEventDO_passThrough_directMapperInsert() {
        StockEventDO event = new StockEventDO();
        event.setTenantId(1L);
        event.setEventTime(LocalDateTime.now());
        event.setBusinessDate(LocalDate.now());
        event.setEventType("PURCHASE_IN");
        event.setDirection("IN");
        event.setStockItemId(1001L);
        event.setSkuCode("SKU_TEST");
        event.setLocationId(10L);
        event.setQuantity(new BigDecimal("10"));
        event.setUnit("个");
        event.setOperatorUserId(999L);
        event.setCreateTime(LocalDateTime.now());
        event.setParentEventId(42L);
        event.setRecipeId(9L);
        event.setRecipeVersion(2);

        stockEventMapper.insert(event);

        StockEventDO found = stockEventMapper.selectById(event.getId());
        assertThat(found).isNotNull();
        assertThat(found.getParentEventId()).isEqualTo(42L);
        assertThat(found.getRecipeId()).isEqualTo(9L);
        assertThat(found.getRecipeVersion()).isEqualTo(2);
    }

    // AC-6, AC-9: selectByParentEventId returns child events for same tenant
    @Test
    void selectByParentEventId_returnsChildEvents() {
        StockEventDO parent = buildEventDO("PURCHASE_IN", "IN", new BigDecimal("100"));
        stockEventMapper.insert(parent);

        StockEventDO child1 = buildEventDO("CONSUME_OUT", "OUT", new BigDecimal("10"));
        child1.setParentEventId(parent.getId());
        stockEventMapper.insert(child1);

        StockEventDO child2 = buildEventDO("CONSUME_OUT", "OUT", new BigDecimal("5"));
        child2.setParentEventId(parent.getId());
        stockEventMapper.insert(child2);

        List<StockEventDO> children = stockEventMapper.selectByParentEventId(1L, parent.getId());
        assertThat(children).hasSize(2);
        assertThat(children).extracting(StockEventDO::getId)
                .containsExactlyInAnyOrder(child1.getId(), child2.getId());
    }

    // AC-9: selectByParentEventId cross-tenant isolation
    @Test
    void selectByParentEventId_crossTenantIsolation() {
        // Tenant 1: parent + child
        TenantContextHolder.setTenantId(1L);
        StockEventDO parent1 = buildEventDO("PURCHASE_IN", "IN", new BigDecimal("100"));
        parent1.setTenantId(1L);
        stockEventMapper.insert(parent1);

        StockEventDO child1 = buildEventDO("CONSUME_OUT", "OUT", new BigDecimal("10"));
        child1.setTenantId(1L);
        child1.setParentEventId(parent1.getId());
        stockEventMapper.insert(child1);

        // Tenant 2: child pointing to same parentEventId value but different tenant
        TenantContextHolder.setTenantId(2L);
        StockEventDO child2 = buildEventDO("CONSUME_OUT", "OUT", new BigDecimal("20"));
        child2.setTenantId(2L);
        child2.setParentEventId(parent1.getId()); // same parent ID value, different tenant
        stockEventMapper.insert(child2);

        // Tenant 1 query should only see tenant 1's child
        TenantContextHolder.setTenantId(1L);
        List<StockEventDO> tenant1Children = stockEventMapper.selectByParentEventId(1L, parent1.getId());
        assertThat(tenant1Children).hasSize(1);
        assertThat(tenant1Children.get(0).getTenantId()).isEqualTo(1L);

        // Tenant 2 query should only see tenant 2's child
        TenantContextHolder.setTenantId(2L);
        List<StockEventDO> tenant2Children = stockEventMapper.selectByParentEventId(2L, parent1.getId());
        assertThat(tenant2Children).hasSize(1);
        assertThat(tenant2Children.get(0).getTenantId()).isEqualTo(2L);
    }

    // AC-8: recordEvent with only some fields set
    @Test
    void recordEvent_partialBomReverseFields_onlySetFieldsPersisted() {
        StockEventReqDTO req = buildBaseReqDTO();
        req.setRecipeId(88L);
        // parentEventId and recipeVersion remain null

        Long eventId = stockEventService.recordEvent(req);

        StockEventDO found = stockEventMapper.selectById(eventId);
        assertThat(found).isNotNull();
        assertThat(found.getParentEventId()).isNull();
        assertThat(found.getRecipeId()).isEqualTo(88L);
        assertThat(found.getRecipeVersion()).isNull();
    }

    // --- Helpers ---

    private StockEventReqDTO buildBaseReqDTO() {
        StockEventReqDTO req = new StockEventReqDTO();
        req.setTenantId(1L);
        req.setEventTime(LocalDateTime.now());
        req.setBusinessDate(LocalDate.now());
        req.setEventType(StockEventTypeEnum.PURCHASE_IN.getCode());
        req.setDirection(StockDirectionEnum.IN.getCode());
        req.setStockItemId(1001L);
        req.setSkuCode("SKU_TEST");
        req.setLocationId(10L);
        req.setQuantity(new BigDecimal("10"));
        req.setUnit("个");
        req.setOperatorUserId(999L);
        req.setClientRequestId("bom-reverse-" + System.nanoTime());
        return req;
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
