package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseItemRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreItemRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeItemDO;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.service.BomRecipeService;
import com.geihou.module.supplychain.bom.service.ProductMasterService;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link StockBomReverseService}.
 *
 * <p>Covers TASK-G2-02D requirements:
 * <ul>
 *   <li>Single-layer BOM deducts raw materials only.</li>
 *   <li>Finished product stock is unchanged.</li>
 *   <li>No parent stock_event row is created.</li>
 *   <li>All created rows are CONSUME_OUT.</li>
 *   <li>parent_event_id is null.</li>
 *   <li>recipe_id and recipe_version snapshots are stored.</li>
 *   <li>Traceability by source_module / source_record_id / reference_no.</li>
 *   <li>Insufficient stock rolls back all component changes.</li>
 *   <li>Repeating the same clientRequestId does not create duplicates.</li>
 *   <li>Tenant isolation.</li>
 *   <li>Multi-layer BOM expands to raw-material leaves only.</li>
 * </ul>
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:bom_reverse_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockBomReverseServiceTest {

    @Autowired
    private StockBomReverseService stockBomReverseService;
    @Autowired
    private StockEventService stockEventService;
    @Autowired
    private StockEventMapper stockEventMapper;
    @Autowired
    private StockItemService stockItemService;
    @Autowired
    private StockBalanceService stockBalanceService;
    @Autowired
    private ProductMasterService productMasterService;
    @Autowired
    private BomRecipeService bomRecipeService;
    @Autowired
    private DataSource dataSource;

    // Test constants
    private static final Long TENANT_1 = 1L;
    private static final Long TENANT_2 = 2L;
    private static final Long LOCATION_ID = 10L;
    private static final Long OPERATOR_ID = 999L;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(TENANT_1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ================================================================
    // Single-layer BOM
    // ================================================================

    @Test
    void singleLayerBom_deductsRawMaterialsOnly() {
        // Setup: finished product FP-A with 2 raw materials (RM-1, RM-2)
        // Recipe: 1 FP-A = 2 RM-1 + 3 RM-2
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        // Sell 5 units of FP-A
        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("5"), "sales-order-001", "SO-001");

        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);

        // Should have 2 component deductions
        assertThat(resp.getItems()).hasSize(2);

        // Verify quantities: 5 * 2 = 10 for RM-1, 5 * 3 = 15 for RM-2
        SalesOutBomReverseItemRespDTO rm1Item = findItem(resp, setup.rm1ProductId);
        SalesOutBomReverseItemRespDTO rm2Item = findItem(resp, setup.rm2ProductId);

        assertThat(rm1Item.getQuantity()).isEqualByComparingTo(new BigDecimal("10.000000"));
        assertThat(rm2Item.getQuantity()).isEqualByComparingTo(new BigDecimal("15.000000"));

        // Verify events are CONSUME_OUT
        StockEventDO event1 = stockEventMapper.selectById(rm1Item.getEventId());
        StockEventDO event2 = stockEventMapper.selectById(rm2Item.getEventId());
        assertThat(event1.getEventType()).isEqualTo("CONSUME_OUT");
        assertThat(event1.getDirection()).isEqualTo("OUT");
        assertThat(event2.getEventType()).isEqualTo("CONSUME_OUT");
        assertThat(event2.getDirection()).isEqualTo("OUT");
    }

    @Test
    void singleLayerBom_finishedProductStockUnchanged() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        // Give finished product some stock
        giveStock(TENANT_1, setup.finishedStockItemId, setup.finishedSkuCode, new BigDecimal("100"));

        BigDecimal before = stockBalanceService.getAvailableQty(TENANT_1, setup.finishedStockItemId, LOCATION_ID);
        assertThat(before).isEqualByComparingTo(new BigDecimal("100"));

        // Sell 5 units
        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("5"), "sales-order-002", "SO-002");
        stockBomReverseService.salesOutWithBomReverse(req);

        // Finished product stock should still be 100 — unchanged
        BigDecimal after = stockBalanceService.getAvailableQty(TENANT_1, setup.finishedStockItemId, LOCATION_ID);
        assertThat(after).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void noParentStockEventCreated() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("1"), "sales-order-003", "SO-003");
        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);

        // Count total events with the source record ID
        // All events should be CONSUME_OUT for raw materials — no parent event
        for (SalesOutBomReverseItemRespDTO item : resp.getItems()) {
            StockEventDO event = stockEventMapper.selectById(item.getEventId());
            assertThat(event.getParentEventId()).isNull();
            assertThat(event.getEventType()).isEqualTo("CONSUME_OUT");
        }

        // Verify only 2 events exist for this source record (no parent)
        // We check by clientRequestId pattern — each component gets base + "::" + componentId
        // No extra event should exist for the finished product
        long totalEventsForSource = countEventsBySource(TENANT_1, "SALES", 1001L);
        assertThat(totalEventsForSource).isEqualTo(2);
    }

    @Test
    void allCreatedRowsAreConsumeOut() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("2"), "sales-order-004", "SO-004");
        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);

        for (SalesOutBomReverseItemRespDTO item : resp.getItems()) {
            StockEventDO event = stockEventMapper.selectById(item.getEventId());
            assertThat(event.getEventType()).isEqualTo("CONSUME_OUT");
            assertThat(event.getDirection()).isEqualTo("OUT");
        }
    }

    @Test
    void parentEventIdIsNull() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("1"), "sales-order-005", "SO-005");
        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);

        for (SalesOutBomReverseItemRespDTO item : resp.getItems()) {
            StockEventDO event = stockEventMapper.selectById(item.getEventId());
            assertThat(event.getParentEventId()).isNull();
        }
    }

    @Test
    void recipeIdAndVersionSnapshotted() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("1"), "sales-order-006", "SO-006");
        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);

        assertThat(resp.getRecipeId()).isEqualTo(setup.recipeId);
        assertThat(resp.getRecipeVersion()).isEqualTo(setup.recipeVersion);

        for (SalesOutBomReverseItemRespDTO item : resp.getItems()) {
            StockEventDO event = stockEventMapper.selectById(item.getEventId());
            assertThat(event.getRecipeId()).isEqualTo(setup.recipeId);
            assertThat(event.getRecipeVersion()).isEqualTo(setup.recipeVersion);
        }
    }

    @Test
    void traceabilityBySourceFields() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        String sourceModule = "SALES";
        Long sourceRecordId = 2001L;
        String referenceNo = "SO-TRACE-001";

        SalesOutBomReverseReqDTO req = new SalesOutBomReverseReqDTO();
        req.setTenantId(TENANT_1);
        req.setProductId(setup.finishedProductId);
        req.setQuantity(new BigDecimal("1"));
        req.setLocationId(LOCATION_ID);
        req.setSourceModule(sourceModule);
        req.setSourceRecordId(sourceRecordId);
        req.setReferenceNo(referenceNo);
        req.setOperatorUserId(OPERATOR_ID);
        req.setClientRequestId("trace-test-" + UUID.randomUUID());

        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);

        for (SalesOutBomReverseItemRespDTO item : resp.getItems()) {
            StockEventDO event = stockEventMapper.selectById(item.getEventId());
            assertThat(event.getSourceModule()).isEqualTo(sourceModule);
            assertThat(event.getSourceRecordId()).isEqualTo(sourceRecordId);
            assertThat(event.getReferenceNo()).isEqualTo(referenceNo);
        }
    }

    @Test
    void traceabilityIncludesSourceOrderItemIdWhenProvided() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        Long sourceOrderItemId = 30001L;
        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                BigDecimal.ONE, "trace-order-item-" + UUID.randomUUID(), "SO-LINE-TRACE-001");
        req.setSourceOrderItemId(sourceOrderItemId);

        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);

        for (SalesOutBomReverseItemRespDTO item : resp.getItems()) {
            StockEventDO event = stockEventMapper.selectById(item.getEventId());
            assertThat(event.getSourceOrderItemId()).isEqualTo(sourceOrderItemId);
        }
    }

    @Test
    void insufficientStockRollsBackAllComponents() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);

        // Give RM-1 enough stock (need 10 for 5 units), but RM-2 insufficient (need 15, give only 5)
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("100"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("5"));

        BigDecimal rm1Before = stockBalanceService.getAvailableQty(TENANT_1, setup.rm1StockItemId, LOCATION_ID);
        BigDecimal rm2Before = stockBalanceService.getAvailableQty(TENANT_1, setup.rm2StockItemId, LOCATION_ID);

        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("5"), "sales-order-rollback", "SO-ROLLBACK");

        // Should fail due to insufficient RM-2 stock
        assertThatThrownBy(() -> stockBomReverseService.salesOutWithBomReverse(req))
                .isInstanceOf(StockBusinessException.class);

        // Both balances should be unchanged — rollback
        BigDecimal rm1After = stockBalanceService.getAvailableQty(TENANT_1, setup.rm1StockItemId, LOCATION_ID);
        BigDecimal rm2After = stockBalanceService.getAvailableQty(TENANT_1, setup.rm2StockItemId, LOCATION_ID);
        assertThat(rm1After).isEqualByComparingTo(rm1Before);
        assertThat(rm2After).isEqualByComparingTo(rm2Before);
    }

    @Test
    void idempotentSameClientRequestIdNoDuplicates() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        String clientRequestId = "idempotent-bom-" + UUID.randomUUID();
        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("3"), clientRequestId, "SO-IDEMPOTENT");

        // First call
        SalesOutBomReverseRespDTO resp1 = stockBomReverseService.salesOutWithBomReverse(req);
        assertThat(resp1.getItems()).hasSize(2);

        // Second call with same clientRequestId
        SalesOutBomReverseRespDTO resp2 = stockBomReverseService.salesOutWithBomReverse(req);
        assertThat(resp2.getItems()).hasSize(2);

        // Event IDs should be the same (idempotent)
        for (int i = 0; i < resp1.getItems().size(); i++) {
            assertThat(resp1.getItems().get(i).getEventId())
                    .isEqualTo(resp2.getItems().get(i).getEventId());
        }

        // No duplicate events — only 2 total
        long eventCount = countEventsBySource(TENANT_1, "SALES", resp1.getItems().get(0).getEventId() != null ?
                req.getSourceRecordId() : req.getSourceRecordId());
        // Count by client request ID prefix
        long totalEvents = resp1.getItems().stream()
                .mapToLong(item -> stockEventMapper.selectByClientRequestId(TENANT_1, item.getClientRequestId()) != null ? 1 : 0)
                .sum();
        assertThat(totalEvents).isEqualTo(2);
    }

    @Test
    void tenantIsolation() {
        // Setup single-layer BOM for tenant 1
        SetupResult setup1 = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup1.rm1StockItemId, setup1.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup1.rm2StockItemId, setup1.rm2SkuCode, new BigDecimal("1000"));

        // Setup single-layer BOM for tenant 2
        TenantContextHolder.setTenantId(TENANT_2);
        SetupResult setup2 = setupSingleLayerBom(TENANT_2);
        giveStock(TENANT_2, setup2.rm1StockItemId, setup2.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_2, setup2.rm2StockItemId, setup2.rm2SkuCode, new BigDecimal("1000"));

        // Tenant 1 sells
        TenantContextHolder.setTenantId(TENANT_1);
        SalesOutBomReverseReqDTO req1 = buildReq(TENANT_1, setup1.finishedProductId,
                new BigDecimal("2"), "tenant1-order", "T1-SO");
        SalesOutBomReverseRespDTO resp1 = stockBomReverseService.salesOutWithBomReverse(req1);

        // Tenant 2 sells
        TenantContextHolder.setTenantId(TENANT_2);
        SalesOutBomReverseReqDTO req2 = buildReq(TENANT_2, setup2.finishedProductId,
                new BigDecimal("3"), "tenant2-order", "T2-SO");
        SalesOutBomReverseRespDTO resp2 = stockBomReverseService.salesOutWithBomReverse(req2);

        // Verify events are tenant-isolated
        TenantContextHolder.setTenantId(TENANT_1);
        for (SalesOutBomReverseItemRespDTO item : resp1.getItems()) {
            StockEventDO event = stockEventMapper.selectById(item.getEventId());
            assertThat(event.getTenantId()).isEqualTo(TENANT_1);
        }
        TenantContextHolder.setTenantId(TENANT_2);
        for (SalesOutBomReverseItemRespDTO item : resp2.getItems()) {
            StockEventDO event = stockEventMapper.selectById(item.getEventId());
            assertThat(event.getTenantId()).isEqualTo(TENANT_2);
        }

        // Tenant 1 balances are affected, tenant 2 independently
        TenantContextHolder.setTenantId(TENANT_1);
        BigDecimal t1Rm1After = stockBalanceService.getAvailableQty(TENANT_1, setup1.rm1StockItemId, LOCATION_ID);
        assertThat(t1Rm1After).isEqualByComparingTo(new BigDecimal("996.000000")); // 1000 - 2*2

        TenantContextHolder.setTenantId(TENANT_2);
        BigDecimal t2Rm1After = stockBalanceService.getAvailableQty(TENANT_2, setup2.rm1StockItemId, LOCATION_ID);
        assertThat(t2Rm1After).isEqualByComparingTo(new BigDecimal("994.000000")); // 1000 - 3*2
    }

    // ================================================================
    // Multi-layer BOM
    // ================================================================

    @Test
    void multiLayerBom_expandsToRawMaterialLeavesAndSemiNodes() {
        // Setup: FP-A → SF-B (semi-finished) + RM-1
        //         SF-B → RM-2 + RM-3
        // Leaves: RM-1, RM-2, RM-3 (RAW) + SF-B (SEMI_FINISHED) = 4 items
        SetupResultMultiLayer setup = setupMultiLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm3StockItemId, setup.rm3SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.sfStockItemId, setup.sfSkuCode, new BigDecimal("1000"));

        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("1"), "multi-layer-order", "ML-SO");

        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);

        // Should have 4 items: 3 RAW leaves + 1 SEMI_FINISHED node
        assertThat(resp.getItems()).hasSize(4);

        // Verify component product IDs — all 4 should be present
        List<Long> componentIds = resp.getItems().stream()
                .map(SalesOutBomReverseItemRespDTO::getComponentProductId)
                .toList();
        assertThat(componentIds).contains(setup.rm1ProductId, setup.rm2ProductId,
                setup.rm3ProductId, setup.sfProductId);

        // All should be CONSUME_OUT with null parent_event_id
        for (SalesOutBomReverseItemRespDTO item : resp.getItems()) {
            StockEventDO event = stockEventMapper.selectById(item.getEventId());
            assertThat(event.getEventType()).isEqualTo("CONSUME_OUT");
            assertThat(event.getParentEventId()).isNull();
        }
    }

    // ================================================================
    // Validation tests
    // ================================================================

    @Test
    void validate_nullTenantIdThrows() {
        SalesOutBomReverseReqDTO req = new SalesOutBomReverseReqDTO();
        req.setProductId(1L);
        req.setQuantity(BigDecimal.ONE);
        assertThatThrownBy(() -> stockBomReverseService.salesOutWithBomReverse(req))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void validate_nullProductIdAndSkuCodeThrows() {
        SalesOutBomReverseReqDTO req = new SalesOutBomReverseReqDTO();
        req.setTenantId(TENANT_1);
        req.setQuantity(BigDecimal.ONE);
        assertThatThrownBy(() -> stockBomReverseService.salesOutWithBomReverse(req))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void validate_zeroQuantityThrows() {
        SalesOutBomReverseReqDTO req = new SalesOutBomReverseReqDTO();
        req.setTenantId(TENANT_1);
        req.setProductId(1L);
        req.setQuantity(BigDecimal.ZERO);
        assertThatThrownBy(() -> stockBomReverseService.salesOutWithBomReverse(req))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void validate_nullLocationIdThrows() {
        SalesOutBomReverseReqDTO req = new SalesOutBomReverseReqDTO();
        req.setTenantId(TENANT_1);
        req.setProductId(1L);
        req.setQuantity(BigDecimal.ONE);
        req.setSourceModule("SALES");
        req.setSourceRecordId(1L);
        req.setOperatorUserId(1L);
        req.setClientRequestId("test");
        assertThatThrownBy(() -> stockBomReverseService.salesOutWithBomReverse(req))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void resolveBySkuCode_whenProductIdNull() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        // Use skuCode instead of productId
        SalesOutBomReverseReqDTO req = new SalesOutBomReverseReqDTO();
        req.setTenantId(TENANT_1);
        req.setSkuCode(setup.finishedSkuCode);
        req.setQuantity(new BigDecimal("1"));
        req.setLocationId(LOCATION_ID);
        req.setSourceModule("SALES");
        req.setSourceRecordId(3001L);
        req.setReferenceNo("SO-SKU");
        req.setOperatorUserId(OPERATOR_ID);
        req.setClientRequestId("sku-resolve-" + UUID.randomUUID());

        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);
        assertThat(resp.getProductId()).isEqualTo(setup.finishedProductId);
        assertThat(resp.getSkuCode()).isEqualTo(setup.finishedSkuCode);
        assertThat(resp.getItems()).hasSize(2);
    }

    // ================================================================
    // Helper: per-component clientRequestId
    // ================================================================

    @Test
    void perComponentClientRequestIdFormat() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        String baseRequestId = "format-test-" + UUID.randomUUID();
        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("1"), baseRequestId, "SO-FORMAT");

        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);

        for (SalesOutBomReverseItemRespDTO item : resp.getItems()) {
            String expected = baseRequestId + "::" + item.getComponentProductId();
            assertThat(item.getClientRequestId()).isEqualTo(expected);
        }
    }

    // ================================================================
    // Sales reverse restore
    // ================================================================

    @Test
    void salesReverseRestore_createsRawMaterialInEventsFromOriginalConsumeOut() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        Long sourceRecordId = 9001L;
        String referenceNo = "SO-RESTORE-001";
        SalesOutBomReverseReqDTO salesReq = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("2"), "r-sale-001", referenceNo);
        salesReq.setSourceRecordId(sourceRecordId);
        SalesOutBomReverseRespDTO salesResp = stockBomReverseService.salesOutWithBomReverse(salesReq);

        SalesReverseRestoreRespDTO restoreResp = stockBomReverseService.salesReverseRestore(
                buildRestoreReq(TENANT_1, sourceRecordId, referenceNo, "r-001"));

        assertThat(restoreResp.getRestoredItemCount()).isEqualTo(2);
        assertThat(restoreResp.getItems()).hasSize(2);

        for (SalesReverseRestoreItemRespDTO item : restoreResp.getItems()) {
            StockEventDO original = stockEventMapper.selectById(item.getOriginalEventId());
            StockEventDO restore = stockEventMapper.selectById(item.getRestoreEventId());

            assertThat(salesResp.getItems())
                    .extracting(SalesOutBomReverseItemRespDTO::getEventId)
                    .contains(original.getId());
            assertThat(original.getEventType()).isEqualTo(StockEventTypeEnum.CONSUME_OUT.getCode());
            assertThat(restore.getEventType()).isEqualTo(StockEventTypeEnum.RETURN_IN.getCode());
            assertThat(restore.getDirection()).isEqualTo(StockDirectionEnum.IN.getCode());
            assertThat(restore.getStockItemId()).isEqualTo(original.getStockItemId());
            assertThat(restore.getQuantity()).isEqualByComparingTo(original.getQuantity());
            assertThat(restore.getParentEventId()).isNull();
            assertThat(restore.getRecipeId()).isEqualTo(original.getRecipeId());
            assertThat(restore.getRecipeVersion()).isEqualTo(original.getRecipeVersion());
            assertThat(restore.getSourceModule()).isEqualTo(original.getSourceModule());
            assertThat(restore.getSourceRecordId()).isEqualTo(original.getSourceRecordId());
            assertThat(restore.getReferenceNo()).isEqualTo(original.getReferenceNo());
        }
    }

    @Test
    void salesReverseRestore_filtersBySourceOrderItemIdForItemRefunds() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        Long sourceRecordId = 9012L;
        String referenceNo = "SO-RESTORE-LINE-001";
        Long firstOrderItemId = 81001L;
        Long secondOrderItemId = 81002L;

        SalesOutBomReverseReqDTO firstLineReq = buildReq(TENANT_1, setup.finishedProductId,
                BigDecimal.ONE, "r-sale-line-001-a", referenceNo);
        firstLineReq.setSourceRecordId(sourceRecordId);
        firstLineReq.setSourceOrderItemId(firstOrderItemId);
        stockBomReverseService.salesOutWithBomReverse(firstLineReq);

        SalesOutBomReverseReqDTO secondLineReq = buildReq(TENANT_1, setup.finishedProductId,
                BigDecimal.ONE, "r-sale-line-001-b", referenceNo);
        secondLineReq.setSourceRecordId(sourceRecordId);
        secondLineReq.setSourceOrderItemId(secondOrderItemId);
        stockBomReverseService.salesOutWithBomReverse(secondLineReq);

        SalesReverseRestoreReqDTO firstRestoreReq = buildRestoreReq(TENANT_1, sourceRecordId,
                referenceNo, "r-line-001-a");
        firstRestoreReq.setSourceOrderItemIds(List.of(firstOrderItemId));
        SalesReverseRestoreRespDTO firstRestore = stockBomReverseService.salesReverseRestore(firstRestoreReq);

        assertThat(firstRestore.getRestoredItemCount()).isEqualTo(2);
        for (SalesReverseRestoreItemRespDTO item : firstRestore.getItems()) {
            StockEventDO original = stockEventMapper.selectById(item.getOriginalEventId());
            StockEventDO restore = stockEventMapper.selectById(item.getRestoreEventId());
            assertThat(original.getSourceOrderItemId()).isEqualTo(firstOrderItemId);
            assertThat(restore.getSourceOrderItemId()).isEqualTo(firstOrderItemId);
        }
        assertThat(countEventsBySource(TENANT_1, "SALES", sourceRecordId)).isEqualTo(6);

        SalesReverseRestoreReqDTO secondRestoreReq = buildRestoreReq(TENANT_1, sourceRecordId,
                referenceNo, "r-line-001-b");
        secondRestoreReq.setSourceOrderItemIds(List.of(secondOrderItemId));
        SalesReverseRestoreRespDTO secondRestore = stockBomReverseService.salesReverseRestore(secondRestoreReq);

        assertThat(secondRestore.getRestoredItemCount()).isEqualTo(2);
        for (SalesReverseRestoreItemRespDTO item : secondRestore.getItems()) {
            StockEventDO original = stockEventMapper.selectById(item.getOriginalEventId());
            StockEventDO restore = stockEventMapper.selectById(item.getRestoreEventId());
            assertThat(original.getSourceOrderItemId()).isEqualTo(secondOrderItemId);
            assertThat(restore.getSourceOrderItemId()).isEqualTo(secondOrderItemId);
        }
        assertThat(countEventsBySource(TENANT_1, "SALES", sourceRecordId)).isEqualTo(8);

        SalesReverseRestoreReqDTO duplicateLineReq = buildRestoreReq(TENANT_1, sourceRecordId,
                referenceNo, "r-line-001-a-different");
        duplicateLineReq.setSourceOrderItemIds(List.of(firstOrderItemId));
        assertThatThrownBy(() -> stockBomReverseService.salesReverseRestore(duplicateLineReq))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void salesReverseRestore_lineScopeRejectsHistoricalNullSourceOrderItemId() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        Long sourceRecordId = 9013L;
        String referenceNo = "SO-RESTORE-LINE-HIST-NULL-001";
        SalesOutBomReverseReqDTO historicalReq = buildReq(TENANT_1, setup.finishedProductId,
                BigDecimal.ONE, "r-sale-line-hist-null-001", referenceNo);
        historicalReq.setSourceRecordId(sourceRecordId);
        stockBomReverseService.salesOutWithBomReverse(historicalReq);

        SalesReverseRestoreReqDTO restoreReq = buildRestoreReq(TENANT_1, sourceRecordId,
                referenceNo, "r-line-hist-null-001");
        restoreReq.setSourceOrderItemIds(List.of(81003L));

        assertThatThrownBy(() -> stockBomReverseService.salesReverseRestore(restoreReq))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("source_order_item_id");
    }

    @Test
    void salesReverseRestore_sameClientRequestIdIsIdempotent() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        Long sourceRecordId = 9002L;
        String referenceNo = "SO-RESTORE-002";
        SalesOutBomReverseReqDTO salesReq = buildReq(TENANT_1, setup.finishedProductId,
                BigDecimal.ONE, "r-sale-002", referenceNo);
        salesReq.setSourceRecordId(sourceRecordId);
        stockBomReverseService.salesOutWithBomReverse(salesReq);

        String clientRequestId = "r-002";
        SalesReverseRestoreReqDTO restoreReq = buildRestoreReq(TENANT_1, sourceRecordId, referenceNo, clientRequestId);
        SalesReverseRestoreRespDTO first = stockBomReverseService.salesReverseRestore(restoreReq);
        SalesReverseRestoreRespDTO second = stockBomReverseService.salesReverseRestore(restoreReq);

        assertThat(second.getItems())
                .extracting(SalesReverseRestoreItemRespDTO::getRestoreEventId)
                .containsExactlyElementsOf(first.getItems().stream()
                        .map(SalesReverseRestoreItemRespDTO::getRestoreEventId)
                        .toList());
        assertThat(countEventsBySource(TENANT_1, "SALES", sourceRecordId)).isEqualTo(4);
    }

    @Test
    void salesReverseRestore_differentClientRequestIdRejectsSecondRestore() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        Long sourceRecordId = 9003L;
        String referenceNo = "SO-RESTORE-003";
        SalesOutBomReverseReqDTO salesReq = buildReq(TENANT_1, setup.finishedProductId,
                BigDecimal.ONE, "r-sale-003", referenceNo);
        salesReq.setSourceRecordId(sourceRecordId);
        stockBomReverseService.salesOutWithBomReverse(salesReq);

        stockBomReverseService.salesReverseRestore(
                buildRestoreReq(TENANT_1, sourceRecordId, referenceNo, "r-003a"));

        assertThatThrownBy(() -> stockBomReverseService.salesReverseRestore(
                buildRestoreReq(TENANT_1, sourceRecordId, referenceNo, "r-003b")))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void salesReverseRestore_doesNotRestoreAcrossTenantOrWrongReference() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        Long sourceRecordId = 9004L;
        String referenceNo = "SO-RESTORE-004";
        SalesOutBomReverseReqDTO salesReq = buildReq(TENANT_1, setup.finishedProductId,
                BigDecimal.ONE, "r-sale-004", referenceNo);
        salesReq.setSourceRecordId(sourceRecordId);
        stockBomReverseService.salesOutWithBomReverse(salesReq);

        assertThatThrownBy(() -> stockBomReverseService.salesReverseRestore(
                buildRestoreReq(TENANT_2, sourceRecordId, referenceNo, "r-004t2")))
                .isInstanceOf(StockBusinessException.class);

        assertThatThrownBy(() -> stockBomReverseService.salesReverseRestore(
                buildRestoreReq(TENANT_1, sourceRecordId, "SO-WRONG", "r-004ref")))
                .isInstanceOf(StockBusinessException.class);

        assertThat(countEventsBySource(TENANT_1, "SALES", sourceRecordId)).isEqualTo(2);
    }

    // ================================================================
    // G2-02R: RETURN_IN behavior switch tests
    // ================================================================

    @Test
    void salesReverseRestore_newRestoreUsesReturnInEventType() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        Long sourceRecordId = 9101L;
        String referenceNo = "SO-RETURN-IN-001";
        SalesOutBomReverseReqDTO salesReq = buildReq(TENANT_1, setup.finishedProductId,
                BigDecimal.ONE, "r-sale-9101", referenceNo);
        salesReq.setSourceRecordId(sourceRecordId);
        stockBomReverseService.salesOutWithBomReverse(salesReq);

        SalesReverseRestoreRespDTO restoreResp = stockBomReverseService.salesReverseRestore(
                buildRestoreReq(TENANT_1, sourceRecordId, referenceNo, "r-9101"));

        assertThat(restoreResp.getItems()).hasSize(2);
        for (SalesReverseRestoreItemRespDTO item : restoreResp.getItems()) {
            StockEventDO restore = stockEventMapper.selectById(item.getRestoreEventId());
            assertThat(restore.getEventType()).isEqualTo(StockEventTypeEnum.RETURN_IN.getCode());
            assertThat(restore.getDirection()).isEqualTo(StockDirectionEnum.IN.getCode());
        }
    }

    @Test
    void salesReverseRestore_findsExistingPurchaseInRestoreEvents() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        Long sourceRecordId = 9102L;
        String referenceNo = "SO-PURCHASE-IN-HIST-001";
        SalesOutBomReverseReqDTO salesReq = buildReq(TENANT_1, setup.finishedProductId,
                BigDecimal.ONE, "r-sale-9102", referenceNo);
        salesReq.setSourceRecordId(sourceRecordId);
        SalesOutBomReverseRespDTO salesResp = stockBomReverseService.salesOutWithBomReverse(salesReq);

        // Simulate G2-02E historical PURCHASE_IN restore events
        String clientRequestId = "r-9102";
        for (SalesOutBomReverseItemRespDTO saleItem : salesResp.getItems()) {
            StockEventDO original = stockEventMapper.selectById(saleItem.getEventId());
            insertHistoricalPurchaseInRestore(original, clientRequestId + "::restore::" + original.getId());
        }

        // Call salesReverseRestore — should find existing PURCHASE_IN restores and be idempotent
        SalesReverseRestoreRespDTO restoreResp = stockBomReverseService.salesReverseRestore(
                buildRestoreReq(TENANT_1, sourceRecordId, referenceNo, clientRequestId));

        assertThat(restoreResp.getItems()).hasSize(2);
        // No duplicate restore events created — still only 2 CONSUME_OUT + 2 PURCHASE_IN = 4
        assertThat(countEventsBySource(TENANT_1, "SALES", sourceRecordId)).isEqualTo(4);
    }

    @Test
    void salesReverseRestore_findsExistingReturnInRestoreEvents() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        Long sourceRecordId = 9103L;
        String referenceNo = "SO-RETURN-IN-HIST-001";
        SalesOutBomReverseReqDTO salesReq = buildReq(TENANT_1, setup.finishedProductId,
                BigDecimal.ONE, "r-sale-9103", referenceNo);
        salesReq.setSourceRecordId(sourceRecordId);
        stockBomReverseService.salesOutWithBomReverse(salesReq);

        String clientRequestId = "r-9103";
        SalesReverseRestoreRespDTO first = stockBomReverseService.salesReverseRestore(
                buildRestoreReq(TENANT_1, sourceRecordId, referenceNo, clientRequestId));

        // Second call — should be idempotent (RETURN_IN recognized)
        SalesReverseRestoreRespDTO second = stockBomReverseService.salesReverseRestore(
                buildRestoreReq(TENANT_1, sourceRecordId, referenceNo, clientRequestId));

        assertThat(second.getItems())
                .extracting(SalesReverseRestoreItemRespDTO::getRestoreEventId)
                .containsExactlyElementsOf(first.getItems().stream()
                        .map(SalesReverseRestoreItemRespDTO::getRestoreEventId)
                        .toList());
        // 2 CONSUME_OUT + 2 RETURN_IN = 4
        assertThat(countEventsBySource(TENANT_1, "SALES", sourceRecordId)).isEqualTo(4);
    }

    @Test
    void salesReverseRestore_mixedRestoreTypesIdempotent() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        Long sourceRecordId = 9104L;
        String referenceNo = "SO-MIXED-001";
        SalesOutBomReverseReqDTO salesReq = buildReq(TENANT_1, setup.finishedProductId,
                BigDecimal.ONE, "r-sale-9104", referenceNo);
        salesReq.setSourceRecordId(sourceRecordId);
        SalesOutBomReverseRespDTO salesResp = stockBomReverseService.salesOutWithBomReverse(salesReq);

        // Simulate mixed: first component has historical PURCHASE_IN restore,
        // second component has new RETURN_IN restore
        String clientRequestId = "r-9104";
        StockEventDO original1 = stockEventMapper.selectById(salesResp.getItems().get(0).getEventId());
        insertHistoricalPurchaseInRestore(original1, clientRequestId + "::restore::" + original1.getId());

        StockEventDO original2 = stockEventMapper.selectById(salesResp.getItems().get(1).getEventId());
        insertReturnInRestore(original2, clientRequestId + "::restore::" + original2.getId());

        // Call salesReverseRestore — should find both and be idempotent
        SalesReverseRestoreRespDTO restoreResp = stockBomReverseService.salesReverseRestore(
                buildRestoreReq(TENANT_1, sourceRecordId, referenceNo, clientRequestId));

        assertThat(restoreResp.getItems()).hasSize(2);
        // No duplicates: 2 CONSUME_OUT + 1 PURCHASE_IN + 1 RETURN_IN = 4
        assertThat(countEventsBySource(TENANT_1, "SALES", sourceRecordId)).isEqualTo(4);
    }

    @Test
    void salesReverseRestore_doesNotMigrateHistoricalPurchaseInEvents() {
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        Long sourceRecordId = 9105L;
        String referenceNo = "SO-NO-MIGRATE-001";
        SalesOutBomReverseReqDTO salesReq = buildReq(TENANT_1, setup.finishedProductId,
                BigDecimal.ONE, "r-sale-9105", referenceNo);
        salesReq.setSourceRecordId(sourceRecordId);
        SalesOutBomReverseRespDTO salesResp = stockBomReverseService.salesOutWithBomReverse(salesReq);

        // Insert historical PURCHASE_IN restore events
        String clientRequestId = "r-9105";
        Long[] historicalRestoreIds = new Long[salesResp.getItems().size()];
        for (int i = 0; i < salesResp.getItems().size(); i++) {
            StockEventDO original = stockEventMapper.selectById(salesResp.getItems().get(i).getEventId());
            Long restoreId = insertHistoricalPurchaseInRestore(
                    original, clientRequestId + "::restore::" + original.getId());
            historicalRestoreIds[i] = restoreId;
        }

        // Call salesReverseRestore — should recognize historical events
        stockBomReverseService.salesReverseRestore(
                buildRestoreReq(TENANT_1, sourceRecordId, referenceNo, clientRequestId));

        // Verify historical PURCHASE_IN events are NOT migrated to RETURN_IN
        for (Long restoreId : historicalRestoreIds) {
            StockEventDO restore = stockEventMapper.selectById(restoreId);
            assertThat(restore.getEventType()).isEqualTo(StockEventTypeEnum.PURCHASE_IN.getCode());
        }
    }

    // ================================================================
    // G2-02V: SEMI_FINISHED CONSUME_OUT deduction tests
    // ================================================================

    @Test
    void semi_finished_product_deducted_on_sales_out() {
        // T-01: Single-layer BOM with SEMI + RAW — SEMI node produces CONSUME_OUT
        SetupResultMultiLayer setup = setupMultiLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm3StockItemId, setup.rm3SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.sfStockItemId, setup.sfSkuCode, new BigDecimal("1000"));

        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("1"), "semi-test-001", "SEMI-SO-001");

        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);

        // Verify SEMI node is in the response
        SalesOutBomReverseItemRespDTO sfItem = resp.getItems().stream()
                .filter(i -> i.getComponentProductId().equals(setup.sfProductId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("SEMI node not found in response"));

        StockEventDO sfEvent = stockEventMapper.selectById(sfItem.getEventId());
        assertThat(sfEvent.getEventType()).isEqualTo("CONSUME_OUT");
        assertThat(sfEvent.getDirection()).isEqualTo("OUT");
    }

    @Test
    void semi_and_raw_both_deducted_on_sales_out() {
        // T-02: Multi-layer BOM — all SEMI nodes + all RAW leaves produce CONSUME_OUT
        SetupResultMultiLayer setup = setupMultiLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm3StockItemId, setup.rm3SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.sfStockItemId, setup.sfSkuCode, new BigDecimal("1000"));

        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("2"), "semi-test-002", "SEMI-SO-002");

        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);

        // 3 RAW leaves + 1 SEMI node = 4 items
        assertThat(resp.getItems()).hasSize(4);

        List<Long> componentIds = resp.getItems().stream()
                .map(SalesOutBomReverseItemRespDTO::getComponentProductId)
                .toList();
        assertThat(componentIds).contains(setup.rm1ProductId, setup.rm2ProductId,
                setup.rm3ProductId, setup.sfProductId);
    }

    @Test
    void semi_consume_out_uses_consume_out_event_type() {
        // T-03: SEMI event_type = CONSUME_OUT (not PRODUCTION_OUT / SALE_OUT)
        SetupResultMultiLayer setup = setupMultiLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm3StockItemId, setup.rm3SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.sfStockItemId, setup.sfSkuCode, new BigDecimal("1000"));

        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("1"), "semi-test-003", "SEMI-SO-003");

        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);

        SalesOutBomReverseItemRespDTO sfItem = resp.getItems().stream()
                .filter(i -> i.getComponentProductId().equals(setup.sfProductId))
                .findFirst()
                .orElseThrow();

        StockEventDO sfEvent = stockEventMapper.selectById(sfItem.getEventId());
        assertThat(sfEvent.getEventType()).isEqualTo("CONSUME_OUT");
        assertThat(sfEvent.getEventType()).isNotEqualTo("PRODUCTION_OUT");
        assertThat(sfEvent.getEventType()).isNotEqualTo("SALE_OUT");
    }

    @Test
    void semi_consume_out_has_null_parent_event_id() {
        // T-04: SEMI CONSUME_OUT parent_event_id = null
        SetupResultMultiLayer setup = setupMultiLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm3StockItemId, setup.rm3SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.sfStockItemId, setup.sfSkuCode, new BigDecimal("1000"));

        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("1"), "semi-test-004", "SEMI-SO-004");

        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);

        SalesOutBomReverseItemRespDTO sfItem = resp.getItems().stream()
                .filter(i -> i.getComponentProductId().equals(setup.sfProductId))
                .findFirst()
                .orElseThrow();

        StockEventDO sfEvent = stockEventMapper.selectById(sfItem.getEventId());
        assertThat(sfEvent.getParentEventId()).isNull();
    }

    @Test
    void semi_finished_stock_unchanged_for_finished_product() {
        // T-05: Finished product stock balance unchanged
        SetupResultMultiLayer setup = setupMultiLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm3StockItemId, setup.rm3SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.sfStockItemId, setup.sfSkuCode, new BigDecimal("1000"));

        // Create a stock item for the finished product
        StockItemDO fpItem = createStockItem(TENANT_1, "SKU-FP-SEMI-" + UUID.randomUUID(), false, false, true);
        giveStock(TENANT_1, fpItem.getId(), fpItem.getSkuCode(), new BigDecimal("100"));

        BigDecimal before = stockBalanceService.getAvailableQty(TENANT_1, fpItem.getId(), LOCATION_ID);
        assertThat(before).isEqualByComparingTo(new BigDecimal("100"));

        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("2"), "semi-test-005", "SEMI-SO-005");
        stockBomReverseService.salesOutWithBomReverse(req);

        BigDecimal after = stockBalanceService.getAvailableQty(TENANT_1, fpItem.getId(), LOCATION_ID);
        assertThat(after).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void semi_stock_correctly_deducted() {
        // T-06: SEMI product stock balance correctly reduced
        SetupResultMultiLayer setup = setupMultiLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm3StockItemId, setup.rm3SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.sfStockItemId, setup.sfSkuCode, new BigDecimal("1000"));

        BigDecimal sfBefore = stockBalanceService.getAvailableQty(TENANT_1, setup.sfStockItemId, LOCATION_ID);

        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("2"), "semi-test-006", "SEMI-SO-006");
        stockBomReverseService.salesOutWithBomReverse(req);

        BigDecimal sfAfter = stockBalanceService.getAvailableQty(TENANT_1, setup.sfStockItemId, LOCATION_ID);
        // SF-B qty=1 per FP, selling 2 FP → 2 * 1 = 2 deducted
        assertThat(sfBefore.subtract(sfAfter)).isEqualByComparingTo(new BigDecimal("2.000000"));
    }

    @Test
    void semi_insufficient_stock_rolls_back_all() {
        // T-10: SEMI stock insufficient → entire transaction rolls back (RAW too)
        SetupResultMultiLayer setup = setupMultiLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm3StockItemId, setup.rm3SkuCode, new BigDecimal("1000"));
        // Give SF only 1 unit — selling 2 FP requires 2 SF
        giveStock(TENANT_1, setup.sfStockItemId, setup.sfSkuCode, new BigDecimal("1"));

        BigDecimal rm1Before = stockBalanceService.getAvailableQty(TENANT_1, setup.rm1StockItemId, LOCATION_ID);
        BigDecimal sfBefore = stockBalanceService.getAvailableQty(TENANT_1, setup.sfStockItemId, LOCATION_ID);

        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("2"), "semi-test-010", "SEMI-SO-010");

        assertThatThrownBy(() -> stockBomReverseService.salesOutWithBomReverse(req))
                .isInstanceOf(StockBusinessException.class);

        // Both RAW and SEMI balances should be unchanged — rollback
        BigDecimal rm1After = stockBalanceService.getAvailableQty(TENANT_1, setup.rm1StockItemId, LOCATION_ID);
        BigDecimal sfAfter = stockBalanceService.getAvailableQty(TENANT_1, setup.sfStockItemId, LOCATION_ID);
        assertThat(rm1After).isEqualByComparingTo(rm1Before);
        assertThat(sfAfter).isEqualByComparingTo(sfBefore);
    }

    @Test
    void sales_reverse_restore_reverses_semi_and_raw() {
        // T-08: restore reverses both RAW + SEMI CONSUME_OUT events
        SetupResultMultiLayer setup = setupMultiLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm3StockItemId, setup.rm3SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.sfStockItemId, setup.sfSkuCode, new BigDecimal("1000"));

        Long sourceRecordId = 9201L;
        String referenceNo = "SEMI-RESTORE-001";
        SalesOutBomReverseReqDTO salesReq = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("1"), "semi-sale-9201", referenceNo);
        salesReq.setSourceRecordId(sourceRecordId);
        SalesOutBomReverseRespDTO salesResp = stockBomReverseService.salesOutWithBomReverse(salesReq);

        // Should have 4 events: 3 RAW + 1 SEMI
        assertThat(salesResp.getItems()).hasSize(4);

        SalesReverseRestoreRespDTO restoreResp = stockBomReverseService.salesReverseRestore(
                buildRestoreReq(TENANT_1, sourceRecordId, referenceNo, "semi-restore-9201"));

        // Should restore all 4 events
        assertThat(restoreResp.getRestoredItemCount()).isEqualTo(4);
        assertThat(restoreResp.getItems()).hasSize(4);

        for (SalesReverseRestoreItemRespDTO item : restoreResp.getItems()) {
            StockEventDO original = stockEventMapper.selectById(item.getOriginalEventId());
            StockEventDO restore = stockEventMapper.selectById(item.getRestoreEventId());

            assertThat(original.getEventType()).isEqualTo(StockEventTypeEnum.CONSUME_OUT.getCode());
            assertThat(restore.getEventType()).isEqualTo(StockEventTypeEnum.RETURN_IN.getCode());
            assertThat(restore.getDirection()).isEqualTo(StockDirectionEnum.IN.getCode());
        }
    }

    @Test
    void sales_reverse_restore_semi_idempotent() {
        // T-09: repeating same clientRequestId for SEMI restore is idempotent
        SetupResultMultiLayer setup = setupMultiLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm3StockItemId, setup.rm3SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.sfStockItemId, setup.sfSkuCode, new BigDecimal("1000"));

        Long sourceRecordId = 9202L;
        String referenceNo = "SEMI-RESTORE-002";
        SalesOutBomReverseReqDTO salesReq = buildReq(TENANT_1, setup.finishedProductId,
                BigDecimal.ONE, "semi-sale-9202", referenceNo);
        salesReq.setSourceRecordId(sourceRecordId);
        stockBomReverseService.salesOutWithBomReverse(salesReq);

        String clientRequestId = "semi-restore-9202";
        SalesReverseRestoreReqDTO restoreReq = buildRestoreReq(TENANT_1, sourceRecordId, referenceNo, clientRequestId);
        SalesReverseRestoreRespDTO first = stockBomReverseService.salesReverseRestore(restoreReq);
        SalesReverseRestoreRespDTO second = stockBomReverseService.salesReverseRestore(restoreReq);

        assertThat(second.getItems())
                .extracting(SalesReverseRestoreItemRespDTO::getRestoreEventId)
                .containsExactlyElementsOf(first.getItems().stream()
                        .map(SalesReverseRestoreItemRespDTO::getRestoreEventId)
                        .toList());
        // 4 CONSUME_OUT + 4 RETURN_IN = 8
        assertThat(countEventsBySource(TENANT_1, "SALES", sourceRecordId)).isEqualTo(8);
    }

    @Test
    void semi_tenant_isolation() {
        // T-12: SEMI CONSUME_OUT events are tenant-isolated
        SetupResultMultiLayer setup1 = setupMultiLayerBom(TENANT_1);
        giveStock(TENANT_1, setup1.rm1StockItemId, setup1.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup1.rm2StockItemId, setup1.rm2SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup1.rm3StockItemId, setup1.rm3SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup1.sfStockItemId, setup1.sfSkuCode, new BigDecimal("1000"));

        TenantContextHolder.setTenantId(TENANT_2);
        SetupResultMultiLayer setup2 = setupMultiLayerBom(TENANT_2);
        giveStock(TENANT_2, setup2.rm1StockItemId, setup2.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_2, setup2.rm2StockItemId, setup2.rm2SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_2, setup2.rm3StockItemId, setup2.rm3SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_2, setup2.sfStockItemId, setup2.sfSkuCode, new BigDecimal("1000"));

        // Tenant 1 sells
        TenantContextHolder.setTenantId(TENANT_1);
        SalesOutBomReverseReqDTO req1 = buildReq(TENANT_1, setup1.finishedProductId,
                new BigDecimal("1"), "semi-t1-order", "SEMI-T1");
        SalesOutBomReverseRespDTO resp1 = stockBomReverseService.salesOutWithBomReverse(req1);

        // Tenant 2 sells
        TenantContextHolder.setTenantId(TENANT_2);
        SalesOutBomReverseReqDTO req2 = buildReq(TENANT_2, setup2.finishedProductId,
                new BigDecimal("1"), "semi-t2-order", "SEMI-T2");
        SalesOutBomReverseRespDTO resp2 = stockBomReverseService.salesOutWithBomReverse(req2);

        // Verify SEMI events are tenant-isolated
        TenantContextHolder.setTenantId(TENANT_1);
        SalesOutBomReverseItemRespDTO sfItem1 = resp1.getItems().stream()
                .filter(i -> i.getComponentProductId().equals(setup1.sfProductId))
                .findFirst().orElseThrow();
        StockEventDO sfEvent1 = stockEventMapper.selectById(sfItem1.getEventId());
        assertThat(sfEvent1.getTenantId()).isEqualTo(TENANT_1);

        TenantContextHolder.setTenantId(TENANT_2);
        SalesOutBomReverseItemRespDTO sfItem2 = resp2.getItems().stream()
                .filter(i -> i.getComponentProductId().equals(setup2.sfProductId))
                .findFirst().orElseThrow();
        StockEventDO sfEvent2 = stockEventMapper.selectById(sfItem2.getEventId());
        assertThat(sfEvent2.getTenantId()).isEqualTo(TENANT_2);
    }

    @Test
    void semi_consume_out_traceability_fields() {
        // Verify SEMI CONSUME_OUT has correct sourceModule / sourceRecordId / referenceNo
        SetupResultMultiLayer setup = setupMultiLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm3StockItemId, setup.rm3SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.sfStockItemId, setup.sfSkuCode, new BigDecimal("1000"));

        String sourceModule = "SALES";
        Long sourceRecordId = 9301L;
        String referenceNo = "SEMI-TRACE-001";

        SalesOutBomReverseReqDTO req = new SalesOutBomReverseReqDTO();
        req.setTenantId(TENANT_1);
        req.setProductId(setup.finishedProductId);
        req.setQuantity(new BigDecimal("1"));
        req.setLocationId(LOCATION_ID);
        req.setSourceModule(sourceModule);
        req.setSourceRecordId(sourceRecordId);
        req.setReferenceNo(referenceNo);
        req.setOperatorUserId(OPERATOR_ID);
        req.setClientRequestId("semi-trace-" + UUID.randomUUID());

        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);

        SalesOutBomReverseItemRespDTO sfItem = resp.getItems().stream()
                .filter(i -> i.getComponentProductId().equals(setup.sfProductId))
                .findFirst().orElseThrow();

        StockEventDO sfEvent = stockEventMapper.selectById(sfItem.getEventId());
        assertThat(sfEvent.getSourceModule()).isEqualTo(sourceModule);
        assertThat(sfEvent.getSourceRecordId()).isEqualTo(sourceRecordId);
        assertThat(sfEvent.getReferenceNo()).isEqualTo(referenceNo);
        assertThat(sfEvent.getRecipeId()).isEqualTo(resp.getRecipeId());
        assertThat(sfEvent.getRecipeVersion()).isEqualTo(resp.getRecipeVersion());
    }

    @Test
    void semi_consume_out_per_component_idempotency_key() {
        // Verify SEMI node gets per-component clientRequestId
        SetupResultMultiLayer setup = setupMultiLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm3StockItemId, setup.rm3SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.sfStockItemId, setup.sfSkuCode, new BigDecimal("1000"));

        String baseRequestId = "semi-idem-" + UUID.randomUUID();
        SalesOutBomReverseReqDTO req = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("1"), baseRequestId, "SEMI-IDEM");
        SalesOutBomReverseRespDTO resp = stockBomReverseService.salesOutWithBomReverse(req);

        SalesOutBomReverseItemRespDTO sfItem = resp.getItems().stream()
                .filter(i -> i.getComponentProductId().equals(setup.sfProductId))
                .findFirst().orElseThrow();

        String expected = baseRequestId + "::" + setup.sfProductId;
        assertThat(sfItem.getClientRequestId()).isEqualTo(expected);
    }

    @Test
    void historical_raw_only_consume_out_still_restores() {
        // T-11: Historical CONSUME_OUT (RAW only, no SEMI) can be restored correctly
        SetupResult setup = setupSingleLayerBom(TENANT_1);
        giveStock(TENANT_1, setup.rm1StockItemId, setup.rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, setup.rm2StockItemId, setup.rm2SkuCode, new BigDecimal("1000"));

        Long sourceRecordId = 9401L;
        String referenceNo = "HIST-RAW-001";
        SalesOutBomReverseReqDTO salesReq = buildReq(TENANT_1, setup.finishedProductId,
                new BigDecimal("2"), "hist-sale-9401", referenceNo);
        salesReq.setSourceRecordId(sourceRecordId);
        stockBomReverseService.salesOutWithBomReverse(salesReq);

        // This single-layer BOM has no SEMI nodes, so only 2 RAW CONSUME_OUT events
        assertThat(countEventsBySource(TENANT_1, "SALES", sourceRecordId)).isEqualTo(2);

        SalesReverseRestoreRespDTO restoreResp = stockBomReverseService.salesReverseRestore(
                buildRestoreReq(TENANT_1, sourceRecordId, referenceNo, "hist-restore-9401"));

        assertThat(restoreResp.getRestoredItemCount()).isEqualTo(2);
        // 2 CONSUME_OUT + 2 RETURN_IN = 4
        assertThat(countEventsBySource(TENANT_1, "SALES", sourceRecordId)).isEqualTo(4);
    }

    // ================================================================
    // Helper methods
    // ================================================================

    private SalesOutBomReverseReqDTO buildReq(Long tenantId, Long productId,
                                               BigDecimal quantity, String clientRequestId,
                                               String referenceNo) {
        SalesOutBomReverseReqDTO req = new SalesOutBomReverseReqDTO();
        req.setTenantId(tenantId);
        req.setProductId(productId);
        req.setQuantity(quantity);
        req.setLocationId(LOCATION_ID);
        req.setSourceModule("SALES");
        req.setSourceRecordId(1001L);
        req.setReferenceNo(referenceNo);
        req.setOperatorUserId(OPERATOR_ID);
        req.setClientRequestId(clientRequestId);
        return req;
    }

    private SalesReverseRestoreReqDTO buildRestoreReq(Long tenantId, Long sourceRecordId,
                                                       String referenceNo, String clientRequestId) {
        SalesReverseRestoreReqDTO req = new SalesReverseRestoreReqDTO();
        req.setTenantId(tenantId);
        req.setSourceModule("SALES");
        req.setSourceRecordId(sourceRecordId);
        req.setReferenceNo(referenceNo);
        req.setOperatorUserId(OPERATOR_ID);
        req.setClientRequestId(clientRequestId);
        return req;
    }

    private void giveStock(Long tenantId, Long stockItemId, String skuCode, BigDecimal qty) {
        StockEventReqDTO req = new StockEventReqDTO();
        req.setTenantId(tenantId);
        req.setEventTime(LocalDateTime.now());
        req.setBusinessDate(java.time.LocalDate.now());
        req.setEventType(StockEventTypeEnum.PURCHASE_IN.getCode());
        req.setDirection(StockDirectionEnum.IN.getCode());
        req.setStockItemId(stockItemId);
        req.setSkuCode(skuCode);
        req.setLocationId(LOCATION_ID);
        req.setQuantity(qty);
        req.setUnit("个");
        req.setOperatorUserId(OPERATOR_ID);
        req.setClientRequestId("stock-in-" + UUID.randomUUID());
        stockEventService.recordEvent(req);
    }

    /**
     * Insert a historical PURCHASE_IN restore event (simulating G2-02E data).
     * Returns the event ID.
     */
    private Long insertHistoricalPurchaseInRestore(StockEventDO original, String clientRequestId) {
        return insertRestoreEvent(original, clientRequestId, StockEventTypeEnum.PURCHASE_IN.getCode());
    }

    /**
     * Insert a RETURN_IN restore event (simulating G2-02R new data).
     * Returns the event ID.
     */
    private Long insertReturnInRestore(StockEventDO original, String clientRequestId) {
        return insertRestoreEvent(original, clientRequestId, StockEventTypeEnum.RETURN_IN.getCode());
    }

    private Long insertRestoreEvent(StockEventDO original, String clientRequestId, String eventType) {
        StockEventReqDTO req = new StockEventReqDTO();
        req.setTenantId(original.getTenantId());
        req.setEventTime(LocalDateTime.now());
        req.setBusinessDate(java.time.LocalDate.now());
        req.setEventType(eventType);
        req.setDirection(StockDirectionEnum.IN.getCode());
        req.setStockItemId(original.getStockItemId());
        req.setSkuCode(original.getSkuCode());
        req.setLocationId(original.getLocationId());
        req.setQuantity(original.getQuantity());
        req.setUnit(original.getUnit());
        req.setSourceModule(original.getSourceModule());
        req.setSourceRecordId(original.getSourceRecordId());
        req.setSourceOrderItemId(original.getSourceOrderItemId());
        req.setReferenceNo(original.getReferenceNo());
        req.setClientRequestId(clientRequestId);
        req.setOperatorUserId(OPERATOR_ID);
        req.setParentEventId(null);
        req.setRecipeId(original.getRecipeId());
        req.setRecipeVersion(original.getRecipeVersion());
        return stockEventService.recordEvent(req);
    }

    private long countEventsBySource(Long tenantId, String sourceModule, Long sourceRecordId) {
        // Use mapper to count — we don't have a direct count method, so query all and filter
        // The stockEventMapper extends BaseMapperX, so we can use selectList with wrapper
        List<StockEventDO> all = stockEventMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockEventDO>()
                        .eq(StockEventDO::getTenantId, tenantId)
                        .eq(StockEventDO::getSourceModule, sourceModule)
                        .eq(StockEventDO::getSourceRecordId, sourceRecordId)
        );
        return all.size();
    }

    private SalesOutBomReverseItemRespDTO findItem(SalesOutBomReverseRespDTO resp, Long componentProductId) {
        return resp.getItems().stream()
                .filter(i -> i.getComponentProductId().equals(componentProductId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Item not found for component " + componentProductId));
    }

    // --- Setup helpers ---

    /**
     * Setup a single-layer BOM:
     *   FP-A (FINISHED) → RM-1 (RAW_MATERIAL, qty=2) + RM-2 (RAW_MATERIAL, qty=3)
     */
    private SetupResult setupSingleLayerBom(Long tenantId) {
        TenantContextHolder.setTenantId(tenantId);

        // Create finished product
        ProductMasterDO fp = new ProductMasterDO();
        fp.setTenantId(tenantId);
        fp.setProductCode("FP-" + tenantId + "-" + UUID.randomUUID().toString().substring(0, 8));
        fp.setProductName("Finished Product " + tenantId);
        fp.setProductType("FINISHED");
        fp.setSkuCode("SKU-FP-" + tenantId + "-" + UUID.randomUUID().toString().substring(0, 8));
        fp.setUnit("个");
        fp.setIsActive(true);
        fp.setCreator("test");
        fp.setCreateTime(LocalDateTime.now());
        fp.setUpdater("test");
        fp.setUpdateTime(LocalDateTime.now());
        fp.setDeleted(false);
        Long fpId = productMasterService.createProduct(fp);

        // Create RM-1
        ProductMasterDO rm1 = new ProductMasterDO();
        rm1.setTenantId(tenantId);
        rm1.setProductCode("RM1-" + tenantId + "-" + UUID.randomUUID().toString().substring(0, 8));
        rm1.setProductName("Raw Material 1");
        rm1.setProductType("RAW_MATERIAL");
        rm1.setSkuCode("SKU-RM1-" + tenantId + "-" + UUID.randomUUID().toString().substring(0, 8));
        rm1.setUnit("个");
        rm1.setIsActive(true);
        rm1.setCreator("test");
        rm1.setCreateTime(LocalDateTime.now());
        rm1.setUpdater("test");
        rm1.setUpdateTime(LocalDateTime.now());
        rm1.setDeleted(false);
        Long rm1Id = productMasterService.createProduct(rm1);

        // Create RM-2
        ProductMasterDO rm2 = new ProductMasterDO();
        rm2.setTenantId(tenantId);
        rm2.setProductCode("RM2-" + tenantId + "-" + UUID.randomUUID().toString().substring(0, 8));
        rm2.setProductName("Raw Material 2");
        rm2.setProductType("RAW_MATERIAL");
        rm2.setSkuCode("SKU-RM2-" + tenantId + "-" + UUID.randomUUID().toString().substring(0, 8));
        rm2.setUnit("个");
        rm2.setIsActive(true);
        rm2.setCreator("test");
        rm2.setCreateTime(LocalDateTime.now());
        rm2.setUpdater("test");
        rm2.setUpdateTime(LocalDateTime.now());
        rm2.setDeleted(false);
        Long rm2Id = productMasterService.createProduct(rm2);

        // Create stock items for RM-1 and RM-2
        StockItemDO rm1Item = new StockItemDO();
        rm1Item.setTenantId(tenantId);
        rm1Item.setSkuCode(rm1.getSkuCode());
        rm1Item.setItemName("Raw Material 1 Item");
        rm1Item.setUnit("个");
        rm1Item.setIsRawMaterial(true);
        rm1Item.setIsActive(true);
        rm1Item.setCreator("test");
        rm1Item.setCreateTime(LocalDateTime.now());
        rm1Item.setUpdater("test");
        rm1Item.setUpdateTime(LocalDateTime.now());
        rm1Item.setDeleted(false);
        Long rm1ItemId = stockItemService.createItem(rm1Item);

        StockItemDO rm2Item = new StockItemDO();
        rm2Item.setTenantId(tenantId);
        rm2Item.setSkuCode(rm2.getSkuCode());
        rm2Item.setItemName("Raw Material 2 Item");
        rm2Item.setUnit("个");
        rm2Item.setIsRawMaterial(true);
        rm2Item.setIsActive(true);
        rm2Item.setCreator("test");
        rm2Item.setCreateTime(LocalDateTime.now());
        rm2Item.setUpdater("test");
        rm2Item.setUpdateTime(LocalDateTime.now());
        rm2Item.setDeleted(false);
        Long rm2ItemId = stockItemService.createItem(rm2Item);

        // Create stock item for FP (to verify it's unchanged)
        StockItemDO fpItem = new StockItemDO();
        fpItem.setTenantId(tenantId);
        fpItem.setSkuCode(fp.getSkuCode());
        fpItem.setItemName("Finished Product Item");
        fpItem.setUnit("个");
        fpItem.setIsFinished(true);
        fpItem.setIsActive(true);
        fpItem.setCreator("test");
        fpItem.setCreateTime(LocalDateTime.now());
        fpItem.setUpdater("test");
        fpItem.setUpdateTime(LocalDateTime.now());
        fpItem.setDeleted(false);
        Long fpItemId = stockItemService.createItem(fpItem);

        // Create BOM recipe: 1 FP = 2 RM-1 + 3 RM-2
        BomRecipeDO recipe = new BomRecipeDO();
        recipe.setTenantId(tenantId);
        recipe.setProductId(fpId);
        recipe.setStatus("DRAFT");
        recipe.setOutputQuantity(BigDecimal.ONE);
        recipe.setOutputUnit("个");
        recipe.setCreator("test");
        recipe.setCreateTime(LocalDateTime.now());
        recipe.setUpdater("test");
        recipe.setUpdateTime(LocalDateTime.now());
        recipe.setDeleted(false);
        Long recipeId = bomRecipeService.createDraftRecipe(recipe);

        // Add items
        BomRecipeItemDO item1 = new BomRecipeItemDO();
        item1.setTenantId(tenantId);
        item1.setRecipeId(recipeId);
        item1.setComponentProductId(rm1Id);
        item1.setQuantity(new BigDecimal("2"));
        item1.setWasteRate(BigDecimal.ZERO);
        item1.setComponentType("RAW_MATERIAL");
        item1.setUnit("个");

        BomRecipeItemDO item2 = new BomRecipeItemDO();
        item2.setTenantId(tenantId);
        item2.setRecipeId(recipeId);
        item2.setComponentProductId(rm2Id);
        item2.setQuantity(new BigDecimal("3"));
        item2.setWasteRate(BigDecimal.ZERO);
        item2.setComponentType("RAW_MATERIAL");
        item2.setUnit("个");

        bomRecipeService.updateDraftItems(tenantId, recipeId, List.of(item1, item2));
        bomRecipeService.activateRecipe(tenantId, recipeId);

        // Get the activated recipe for version snapshot
        BomRecipeDO activeRecipe = bomRecipeService.getActiveRecipe(fpId, tenantId);

        SetupResult result = new SetupResult();
        result.finishedProductId = fpId;
        result.finishedSkuCode = fp.getSkuCode();
        result.finishedStockItemId = fpItemId;
        result.rm1ProductId = rm1Id;
        result.rm1SkuCode = rm1.getSkuCode();
        result.rm1StockItemId = rm1ItemId;
        result.rm2ProductId = rm2Id;
        result.rm2SkuCode = rm2.getSkuCode();
        result.rm2StockItemId = rm2ItemId;
        result.recipeId = activeRecipe.getId();
        result.recipeVersion = activeRecipe.getVersionNo();
        return result;
    }

    /**
     * Setup a multi-layer BOM:
     *   FP-A (FINISHED) → SF-B (SEMI_FINISHED, qty=1) + RM-1 (RAW_MATERIAL, qty=2)
     *   SF-B → RM-2 (RAW_MATERIAL, qty=3) + RM-3 (RAW_MATERIAL, qty=1)
     *   Leaves: RM-1, RM-2, RM-3
     */
    private SetupResultMultiLayer setupMultiLayerBom(Long tenantId) {
        TenantContextHolder.setTenantId(tenantId);

        // Create FP-A
        ProductMasterDO fp = createProduct(tenantId, "FINISHED", "个");
        // Create SF-B
        ProductMasterDO sf = createProduct(tenantId, "SEMI_FINISHED", "个");
        // Create RM-1, RM-2, RM-3
        ProductMasterDO rm1 = createProduct(tenantId, "RAW_MATERIAL", "个");
        ProductMasterDO rm2 = createProduct(tenantId, "RAW_MATERIAL", "个");
        ProductMasterDO rm3 = createProduct(tenantId, "RAW_MATERIAL", "个");

        // Create stock items
        StockItemDO rm1Item = createStockItem(tenantId, rm1.getSkuCode(), true, false, false);
        StockItemDO rm2Item = createStockItem(tenantId, rm2.getSkuCode(), true, false, false);
        StockItemDO rm3Item = createStockItem(tenantId, rm3.getSkuCode(), true, false, false);
        StockItemDO sfItem = createStockItem(tenantId, sf.getSkuCode(), false, true, false);

        // Create SF-B recipe: 1 SF-B = 3 RM-2 + 1 RM-3
        BomRecipeDO sfRecipe = new BomRecipeDO();
        sfRecipe.setTenantId(tenantId);
        sfRecipe.setProductId(sf.getId());
        sfRecipe.setStatus("DRAFT");
        sfRecipe.setOutputQuantity(BigDecimal.ONE);
        sfRecipe.setOutputUnit("个");
        sfRecipe.setCreator("test");
        sfRecipe.setCreateTime(LocalDateTime.now());
        sfRecipe.setUpdater("test");
        sfRecipe.setUpdateTime(LocalDateTime.now());
        sfRecipe.setDeleted(false);
        Long sfRecipeId = bomRecipeService.createDraftRecipe(sfRecipe);

        BomRecipeItemDO sfItem1 = createRecipeItem(tenantId, sfRecipeId, rm2.getId(), new BigDecimal("3"), "RAW_MATERIAL", "个");
        BomRecipeItemDO sfItem2 = createRecipeItem(tenantId, sfRecipeId, rm3.getId(), new BigDecimal("1"), "RAW_MATERIAL", "个");
        bomRecipeService.updateDraftItems(tenantId, sfRecipeId, List.of(sfItem1, sfItem2));
        bomRecipeService.activateRecipe(tenantId, sfRecipeId);

        // Create FP-A recipe: 1 FP-A = 1 SF-B + 2 RM-1
        BomRecipeDO fpRecipe = new BomRecipeDO();
        fpRecipe.setTenantId(tenantId);
        fpRecipe.setProductId(fp.getId());
        fpRecipe.setStatus("DRAFT");
        fpRecipe.setOutputQuantity(BigDecimal.ONE);
        fpRecipe.setOutputUnit("个");
        fpRecipe.setCreator("test");
        fpRecipe.setCreateTime(LocalDateTime.now());
        fpRecipe.setUpdater("test");
        fpRecipe.setUpdateTime(LocalDateTime.now());
        fpRecipe.setDeleted(false);
        Long fpRecipeId = bomRecipeService.createDraftRecipe(fpRecipe);

        BomRecipeItemDO fpItem1 = createRecipeItem(tenantId, fpRecipeId, sf.getId(), new BigDecimal("1"), "SEMI_FINISHED", "个");
        BomRecipeItemDO fpItem2 = createRecipeItem(tenantId, fpRecipeId, rm1.getId(), new BigDecimal("2"), "RAW_MATERIAL", "个");
        bomRecipeService.updateDraftItems(tenantId, fpRecipeId, List.of(fpItem1, fpItem2));
        bomRecipeService.activateRecipe(tenantId, fpRecipeId);

        SetupResultMultiLayer result = new SetupResultMultiLayer();
        result.finishedProductId = fp.getId();
        result.sfProductId = sf.getId();
        result.sfSkuCode = sf.getSkuCode();
        result.sfStockItemId = sfItem.getId();
        result.rm1ProductId = rm1.getId();
        result.rm1SkuCode = rm1.getSkuCode();
        result.rm1StockItemId = rm1Item.getId();
        result.rm2ProductId = rm2.getId();
        result.rm2SkuCode = rm2.getSkuCode();
        result.rm2StockItemId = rm2Item.getId();
        result.rm3ProductId = rm3.getId();
        result.rm3SkuCode = rm3.getSkuCode();
        result.rm3StockItemId = rm3Item.getId();
        return result;
    }

    private ProductMasterDO createProduct(Long tenantId, String type, String unit) {
        ProductMasterDO p = new ProductMasterDO();
        p.setTenantId(tenantId);
        p.setProductCode("P-" + tenantId + "-" + UUID.randomUUID().toString().substring(0, 8));
        p.setProductName(type + "-" + UUID.randomUUID().toString().substring(0, 8));
        p.setProductType(type);
        p.setSkuCode("SKU-" + tenantId + "-" + UUID.randomUUID().toString().substring(0, 8));
        p.setUnit(unit);
        p.setIsActive(true);
        p.setCreator("test");
        p.setCreateTime(LocalDateTime.now());
        p.setUpdater("test");
        p.setUpdateTime(LocalDateTime.now());
        p.setDeleted(false);
        productMasterService.createProduct(p);
        return p;
    }

    private StockItemDO createStockItem(Long tenantId, String skuCode,
                                         boolean isRaw, boolean isSemi, boolean isFinished) {
        StockItemDO item = new StockItemDO();
        item.setTenantId(tenantId);
        item.setSkuCode(skuCode);
        item.setItemName("Item-" + skuCode);
        item.setUnit("个");
        item.setIsRawMaterial(isRaw);
        item.setIsSemiFinished(isSemi);
        item.setIsFinished(isFinished);
        item.setIsActive(true);
        item.setCreator("test");
        item.setCreateTime(LocalDateTime.now());
        item.setUpdater("test");
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleted(false);
        stockItemService.createItem(item);
        return item;
    }

    private BomRecipeItemDO createRecipeItem(Long tenantId, Long recipeId, Long componentProductId,
                                              BigDecimal quantity, String componentType, String unit) {
        BomRecipeItemDO item = new BomRecipeItemDO();
        item.setTenantId(tenantId);
        item.setRecipeId(recipeId);
        item.setComponentProductId(componentProductId);
        item.setQuantity(quantity);
        item.setWasteRate(BigDecimal.ZERO);
        item.setComponentType(componentType);
        item.setUnit(unit);
        return item;
    }

    // --- Inner classes for setup results ---

    private static class SetupResult {
        Long finishedProductId;
        String finishedSkuCode;
        Long finishedStockItemId;
        Long rm1ProductId;
        String rm1SkuCode;
        Long rm1StockItemId;
        Long rm2ProductId;
        String rm2SkuCode;
        Long rm2StockItemId;
        Long recipeId;
        Integer recipeVersion;
    }

    private static class SetupResultMultiLayer {
        Long finishedProductId;
        Long sfProductId;
        String sfSkuCode;
        Long sfStockItemId;
        Long rm1ProductId;
        String rm1SkuCode;
        Long rm1StockItemId;
        Long rm2ProductId;
        String rm2SkuCode;
        Long rm2StockItemId;
        Long rm3ProductId;
        String rm3SkuCode;
        Long rm3StockItemId;
    }
}
