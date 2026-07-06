package com.geihou.module.supplychain.production.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeItemDO;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.dal.mapper.BomRecipeItemMapper;
import com.geihou.module.supplychain.bom.dal.mapper.BomRecipeMapper;
import com.geihou.module.supplychain.bom.dal.mapper.ProductMasterMapper;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderCreateReqVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderStageTransitionReqVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderUpdateReqVO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderDO;
import com.geihou.module.supplychain.production.dal.mapper.ProductionOrderMapper;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLocationDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockLocationMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import com.geihou.module.supplychain.stock.service.StockBalanceService;
import com.geihou.module.supplychain.stock.service.StockEventService;
import com.geihou.module.supplychain.stock.service.StockItemService;
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
 * Tests for {@link ProductionOrderService}.
 *
 * <p>Covers: create with validation, state transitions (CREATED → MATERIAL_REQUEST → IN_PROGRESS
 * → QUALITY_CHECK → COMPLETED / REWORK → IN_PROGRESS), cancel, invalid transitions,
 * update in CREATED only, tenant isolation, list/page queries.
 *
 * <p>G2-02L: Tests now include stock_event integration (PRODUCTION_OUT + PRODUCTION_IN)
 * when transitioning to COMPLETED. Setup includes BOM recipe items, stock items, and
 * stock balances for proper integration testing.
 *
 * <p>Source: TASK-G2-02K Section 11.1, TASK-G2-02L.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:production_order_service_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ProductionOrderServiceTest {

    @Autowired
    private ProductionOrderService productionOrderService;
    @Autowired
    private ProductionOrderMapper productionOrderMapper;
    @Autowired
    private ProductMasterMapper productMasterMapper;
    @Autowired
    private BomRecipeMapper bomRecipeMapper;
    @Autowired
    private BomRecipeItemMapper bomRecipeItemMapper;
    @Autowired
    private StockLocationMapper stockLocationMapper;
    @Autowired
    private StockItemService stockItemService;
    @Autowired
    private StockEventService stockEventService;
    @Autowired
    private StockEventMapper stockEventMapper;
    @Autowired
    private StockBalanceService stockBalanceService;
    @Autowired
    private DataSource dataSource;

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long OPERATOR_ID = 100L;

    private Long semiFinishedProductId;
    private Long finishedProductId;
    private Long rawMaterialProductId;
    private Long rawMaterialProductId2;
    private Long activeRecipeId;
    private Long draftRecipeId;
    private Long archivedRecipeId;
    private Long activeLocationId;
    private Long inactiveLocationId;

    // Stock item IDs for stock_event integration
    private Long semiFinishedStockItemId;
    private Long rawMaterialStockItemId;
    private Long rawMaterialStockItemId2;

    private String semiFinishedSkuCode;
    private String rawMaterialSkuCode;
    private String rawMaterialSkuCode2;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(TENANT_A);

        // Seed product_master
        semiFinishedProductId = seedProduct(TENANT_A, "P_SEMI_001", "SEMI_FINISHED", true);
        finishedProductId = seedProduct(TENANT_A, "P_FINISHED_001", "FINISHED", true);
        rawMaterialProductId = seedProduct(TENANT_A, "P_RAW_001", "RAW_MATERIAL", true);
        rawMaterialProductId2 = seedProduct(TENANT_A, "P_RAW_002", "RAW_MATERIAL", true);

        // Get skuCodes (seedProduct sets them as SKU_{code})
        semiFinishedSkuCode = "SKU_P_SEMI_001";
        rawMaterialSkuCode = "SKU_P_RAW_001";
        rawMaterialSkuCode2 = "SKU_P_RAW_002";

        // Seed bom_recipe
        activeRecipeId = seedRecipe(TENANT_A, semiFinishedProductId, "ACTIVE");
        draftRecipeId = seedRecipe(TENANT_A, semiFinishedProductId, "DRAFT");
        archivedRecipeId = seedRecipe(TENANT_A, semiFinishedProductId, "ARCHIVED");

        // Seed BOM recipe items for the active recipe:
        // 1 semi-finished = 2 * raw_1 + 3 * raw_2
        seedRecipeItem(TENANT_A, activeRecipeId, rawMaterialProductId, new BigDecimal("2"), "RAW_MATERIAL", "KG");
        seedRecipeItem(TENANT_A, activeRecipeId, rawMaterialProductId2, new BigDecimal("3"), "RAW_MATERIAL", "KG");

        // Seed stock_location
        activeLocationId = seedLocation(TENANT_A, "LOC_ACTIVE_001", true);
        inactiveLocationId = seedLocation(TENANT_A, "LOC_INACTIVE_001", false);

        // Seed stock_items (mapped via product_master.skuCode → stock_item)
        semiFinishedStockItemId = seedStockItem(TENANT_A, semiFinishedSkuCode, "Semi-Finished Item", false, true, false);
        rawMaterialStockItemId = seedStockItem(TENANT_A, rawMaterialSkuCode, "Raw Material 1 Item", true, false, false);
        rawMaterialStockItemId2 = seedStockItem(TENANT_A, rawMaterialSkuCode2, "Raw Material 2 Item", true, false, false);

        // Give stock to raw materials (enough for tests)
        giveStock(TENANT_A, rawMaterialStockItemId, rawMaterialSkuCode, activeLocationId, new BigDecimal("1000"));
        giveStock(TENANT_A, rawMaterialStockItemId2, rawMaterialSkuCode2, activeLocationId, new BigDecimal("1000"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // =========================================================================
    // Create tests
    // =========================================================================

    @Test
    void testCreateSuccess() {
        ProductionOrderCreateReqVO req = buildCreateReq();
        ProductionOrderDO order = productionOrderService.createProductionOrder(req);

        assertThat(order.getId()).isNotNull();
        assertThat(order.getTenantId()).isEqualTo(TENANT_A);
        assertThat(order.getOrderNo()).isNotBlank();
        assertThat(order.getOrderNo()).startsWith("PO");
        assertThat(order.getProductionStage()).isEqualTo("CREATED");
        assertThat(order.getProductId()).isEqualTo(semiFinishedProductId);
        assertThat(order.getRecipeId()).isEqualTo(activeRecipeId);
        assertThat(order.getLocationId()).isEqualTo(activeLocationId);
        assertThat(order.getPlannedQty()).isEqualByComparingTo(new BigDecimal("100.0000"));
        assertThat(order.getActualQty()).isNull();
        assertThat(order.getDeleted()).isFalse();
    }

    @Test
    void testCreateProductNotSemiFinished() {
        ProductionOrderCreateReqVO req = buildCreateReq();
        req.setProductId(finishedProductId);

        assertThatThrownBy(() -> productionOrderService.createProductionOrder(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("SEMI_FINISHED");
    }

    @Test
    void testCreateProductNotSemi() {
        ProductionOrderCreateReqVO req = buildCreateReq();
        req.setProductId(rawMaterialProductId);

        assertThatThrownBy(() -> productionOrderService.createProductionOrder(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("SEMI_FINISHED");
    }

    @Test
    void testCreateRecipeNotActive() {
        ProductionOrderCreateReqVO req = buildCreateReq();
        req.setRecipeId(draftRecipeId);

        assertThatThrownBy(() -> productionOrderService.createProductionOrder(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void testCreateRecipeArchived() {
        ProductionOrderCreateReqVO req = buildCreateReq();
        req.setRecipeId(archivedRecipeId);

        assertThatThrownBy(() -> productionOrderService.createProductionOrder(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void testCreateLocationNotActive() {
        ProductionOrderCreateReqVO req = buildCreateReq();
        req.setLocationId(inactiveLocationId);

        assertThatThrownBy(() -> productionOrderService.createProductionOrder(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void testCreateLocationNotFound() {
        ProductionOrderCreateReqVO req = buildCreateReq();
        req.setLocationId(999999L);

        assertThatThrownBy(() -> productionOrderService.createProductionOrder(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void testCreatePlannedQtyZero() {
        ProductionOrderCreateReqVO req = buildCreateReq();
        req.setPlannedQty(BigDecimal.ZERO);

        assertThatThrownBy(() -> productionOrderService.createProductionOrder(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void testCreatePlannedQtyNegative() {
        ProductionOrderCreateReqVO req = buildCreateReq();
        req.setPlannedQty(new BigDecimal("-1"));

        assertThatThrownBy(() -> productionOrderService.createProductionOrder(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("positive");
    }

    // =========================================================================
    // Transition tests
    // =========================================================================

    @Test
    void testTransitionCreatedToMaterialRequest() {
        ProductionOrderDO order = createOrder();
        ProductionOrderDO result = transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);

        assertThat(result.getProductionStage()).isEqualTo("MATERIAL_REQUEST");
    }

    @Test
    void testTransitionMaterialRequestToInProgress() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);

        ProductionOrderDO result = transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        assertThat(result.getProductionStage()).isEqualTo("IN_PROGRESS");
        assertThat(result.getActualStartTime()).isNotNull();
    }

    @Test
    void testTransitionInProgressToCompleted() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        ProductionOrderDO result = transition(order.getId(), TENANT_A, "COMPLETED",
                new BigDecimal("98.5000"), OPERATOR_ID, null);

        assertThat(result.getProductionStage()).isEqualTo("COMPLETED");
        assertThat(result.getActualEndTime()).isNotNull();
        assertThat(result.getActualQty()).isEqualByComparingTo(new BigDecimal("98.5000"));

        // G2-02L: Verify stock events were written
        long prodOutEvents = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_OUT");
        long prodInEvents = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_IN");
        // 2 raw material components → 2 PRODUCTION_OUT events
        assertThat(prodOutEvents).isEqualTo(2);
        // 1 semi-finished product → 1 PRODUCTION_IN event
        assertThat(prodInEvents).isEqualTo(1);
    }

    @Test
    void testTransitionNonTerminalToCancelled() {
        // CREATED → CANCELLED
        ProductionOrderDO order = createOrder();
        ProductionOrderDO result = transition(order.getId(), TENANT_A, "CANCELLED",
                null, null, "cancel reason");
        assertThat(result.getProductionStage()).isEqualTo("CANCELLED");
        assertThat(result.getRemark()).isEqualTo("cancel reason");

        // IN_PROGRESS → CANCELLED
        ProductionOrderDO order2 = createOrder();
        transition(order2.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order2.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        ProductionOrderDO result2 = transition(order2.getId(), TENANT_A, "CANCELLED",
                null, null, "cancel from in_progress");
        assertThat(result2.getProductionStage()).isEqualTo("CANCELLED");
    }

    @Test
    void testTransitionInvalidCreatedToCompleted() {
        ProductionOrderDO order = createOrder();

        // CREATED → COMPLETED is invalid; G2-02M uses 2002045 for invalid → COMPLETED
        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "COMPLETED",
                new BigDecimal("100"), OPERATOR_ID, null))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_QUALITY_CHECK_PASS_NOT_ALLOWED);
                });
    }

    @Test
    void testTransitionInvalidCompletedToCancelled() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "COMPLETED", new BigDecimal("100"), OPERATOR_ID, null);

        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "CANCELLED",
                null, null, "try cancel completed"))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Invalid production stage transition");
    }

    // =========================================================================
    // G2-02M: Quality check / rework transition tests
    // =========================================================================

    @Test
    void testTransitionInProgressToQualityCheck() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        ProductionOrderDO result = transition(order.getId(), TENANT_A, "QUALITY_CHECK",
                null, OPERATOR_ID, null);

        assertThat(result.getProductionStage()).isEqualTo("QUALITY_CHECK");

        // Verify no stock events written
        long events = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), null);
        assertThat(events).isEqualTo(0);
    }

    @Test
    void testTransitionQualityCheckInvalidSource() {
        // CREATED → QUALITY_CHECK should fail
        ProductionOrderDO order = createOrder();

        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "QUALITY_CHECK",
                null, OPERATOR_ID, null))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_QUALITY_CHECK_SUBMIT_NOT_ALLOWED);
                });

        // Verify state unchanged
        ProductionOrderDO unchanged = productionOrderService.getProductionOrder(order.getId(), TENANT_A);
        assertThat(unchanged.getProductionStage()).isEqualTo("CREATED");
    }

    @Test
    void testTransitionQualityCheckToCompleted() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);

        ProductionOrderDO result = transition(order.getId(), TENANT_A, "COMPLETED",
                new BigDecimal("10"), OPERATOR_ID, null);

        assertThat(result.getProductionStage()).isEqualTo("COMPLETED");
        assertThat(result.getActualQty()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(result.getQualityCheckResult()).isEqualTo("PASS");
        assertThat(result.getQualityCheckedBy()).isEqualTo(OPERATOR_ID);
        assertThat(result.getQualityCheckedTime()).isNotNull();

        // Verify stock events were written (PRODUCTION_OUT + PRODUCTION_IN)
        long prodOutEvents = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_OUT");
        long prodInEvents = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_IN");
        assertThat(prodOutEvents).isEqualTo(2);
        assertThat(prodInEvents).isEqualTo(1);
    }

    @Test
    void testTransitionQualityCheckToCompletedInsufficientStockRollback() {
        // Use low stock location
        Long lowStockLocation = seedLocation(TENANT_A, "LOC_QC_LOW_STOCK", true);
        giveStock(TENANT_A, rawMaterialStockItemId, rawMaterialSkuCode, lowStockLocation, new BigDecimal("5"));
        giveStock(TENANT_A, rawMaterialStockItemId2, rawMaterialSkuCode2, lowStockLocation, new BigDecimal("1000"));

        ProductionOrderCreateReqVO req = buildCreateReq();
        req.setLocationId(lowStockLocation);
        ProductionOrderDO order = productionOrderService.createProductionOrder(req);

        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);

        // Should fail: insufficient stock
        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "COMPLETED",
                new BigDecimal("10"), OPERATOR_ID, null))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(2002004);
                    assertThat(e.getErrorCode()).isEqualTo(StockErrorCodeConstants.INSUFFICIENT_STOCK);
                });

        // Verify order stage rolled back to QUALITY_CHECK
        ProductionOrderDO rolledBack = productionOrderService.getProductionOrder(order.getId(), TENANT_A);
        assertThat(rolledBack.getProductionStage()).isEqualTo("QUALITY_CHECK");

        // Verify no stock events were written
        long events = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), null);
        assertThat(events).isEqualTo(0);
    }

    @Test
    void testTransitionQualityCheckToCompletedInvalidSource() {
        // IN_PROGRESS → COMPLETED is valid (direct), but MATERIAL_REQUEST → COMPLETED is not
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);

        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "COMPLETED",
                new BigDecimal("10"), OPERATOR_ID, null))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_QUALITY_CHECK_PASS_NOT_ALLOWED);
                });
    }

    @Test
    void testTransitionQualityCheckToRework() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);

        ProductionOrderDO result = transition(order.getId(), TENANT_A, "REWORK",
                null, OPERATOR_ID, "quality check failed: defects found");

        assertThat(result.getProductionStage()).isEqualTo("REWORK");
        assertThat(result.getQualityCheckResult()).isEqualTo("FAIL");
        assertThat(result.getQualityCheckRemark()).isEqualTo("quality check failed: defects found");
        assertThat(result.getQualityCheckedBy()).isEqualTo(OPERATOR_ID);
        assertThat(result.getQualityCheckedTime()).isNotNull();
        assertThat(result.getReworkCount()).isEqualTo(1);

        // Verify no stock events written
        long events = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), null);
        assertThat(events).isEqualTo(0);
    }

    @Test
    void testTransitionQualityCheckToReworkMissingRemarkFails() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);

        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "REWORK",
                null, OPERATOR_ID, null))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_REWORK_REASON_REQUIRED);
                });

        // Verify state unchanged
        ProductionOrderDO unchanged = productionOrderService.getProductionOrder(order.getId(), TENANT_A);
        assertThat(unchanged.getProductionStage()).isEqualTo("QUALITY_CHECK");
    }

    @Test
    void testTransitionQualityCheckToReworkMissingOperatorFails() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);

        // operatorUserId is null and existing.operatorUserId is... wait, existing was set to OPERATOR_ID during IN_PROGRESS
        // We need to pass null explicitly and ensure existing has null too
        // Actually the operator was set during IN_PROGRESS transition. Let's test with both null.
        // Since existing.getOperatorUserId() = OPERATOR_ID (set during IN_PROGRESS), we can't easily test null.
        // Instead, create a new order where operator is never set
        ProductionOrderCreateReqVO req = buildCreateReq();
        req.setOperatorUserId(null);
        ProductionOrderDO order2 = productionOrderService.createProductionOrder(req);
        transition(order2.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order2.getId(), TENANT_A, "IN_PROGRESS", null, null, null);
        transition(order2.getId(), TENANT_A, "QUALITY_CHECK", null, null, null);

        assertThatThrownBy(() -> transition(order2.getId(), TENANT_A, "REWORK",
                null, null, "fail reason"))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_QUALITY_CHECK_OPERATOR_REQUIRED);
                });
    }

    @Test
    void testTransitionReworkInvalidSource() {
        // IN_PROGRESS → REWORK should fail
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "REWORK",
                null, OPERATOR_ID, "fail reason"))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_QUALITY_CHECK_REJECT_NOT_ALLOWED);
                });
    }

    @Test
    void testTransitionReworkToInProgress() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "REWORK", null, OPERATOR_ID, "defects found");

        ProductionOrderDO result = transition(order.getId(), TENANT_A, "IN_PROGRESS",
                null, OPERATOR_ID, null);

        assertThat(result.getProductionStage()).isEqualTo("IN_PROGRESS");
        assertThat(result.getReworkCount()).isEqualTo(1); // preserved

        // Verify no stock events written
        long events = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), null);
        assertThat(events).isEqualTo(0);
    }

    @Test
    void testTransitionReworkToInProgressInvalidSource() {
        // MATERIAL_REQUEST → IN_PROGRESS is valid, but CREATED → IN_PROGRESS is not (for rework resume)
        ProductionOrderDO order = createOrder();

        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "IN_PROGRESS",
                null, OPERATOR_ID, null))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_REWORK_RESUME_NOT_ALLOWED);
                });
    }

    @Test
    void testTransitionInProgressToCompletedDirectRegression() {
        // G2-02L regression: IN_PROGRESS → COMPLETED direct completion still works
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        ProductionOrderDO result = transition(order.getId(), TENANT_A, "COMPLETED",
                new BigDecimal("10"), OPERATOR_ID, null);

        assertThat(result.getProductionStage()).isEqualTo("COMPLETED");
        assertThat(result.getActualQty()).isEqualByComparingTo(new BigDecimal("10"));

        // Verify stock events
        long prodOutEvents = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_OUT");
        long prodInEvents = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_IN");
        assertThat(prodOutEvents).isEqualTo(2);
        assertThat(prodInEvents).isEqualTo(1);
    }

    @Test
    void testTransitionQualityCheckToCancelled() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);

        ProductionOrderDO result = transition(order.getId(), TENANT_A, "CANCELLED",
                null, null, "cancel from quality check");

        assertThat(result.getProductionStage()).isEqualTo("CANCELLED");

        // Verify no stock events written
        long events = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), null);
        assertThat(events).isEqualTo(0);
    }

    @Test
    void testTransitionReworkToCancelled() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "REWORK", null, OPERATOR_ID, "defects");

        ProductionOrderDO result = transition(order.getId(), TENANT_A, "CANCELLED",
                null, null, "cancel from rework");

        assertThat(result.getProductionStage()).isEqualTo("CANCELLED");

        // Verify no stock events written
        long events = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), null);
        assertThat(events).isEqualTo(0);
    }

    @Test
    void testTerminalCompletedCannotTransition() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "COMPLETED", new BigDecimal("10"), OPERATOR_ID, null);

        // Try various targets
        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "QUALITY_CHECK",
                null, OPERATOR_ID, null))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void testTerminalCancelledCannotTransition() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "CANCELLED", null, null, "cancel reason");

        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "IN_PROGRESS",
                null, OPERATOR_ID, null))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void testMultipleReworkCyclesReworkCountAccumulates() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        // Cycle 1: QUALITY_CHECK → REWORK → IN_PROGRESS
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "REWORK", null, OPERATOR_ID, "cycle 1 defects");
        ProductionOrderDO afterCycle1 = transition(order.getId(), TENANT_A, "IN_PROGRESS",
                null, OPERATOR_ID, null);
        assertThat(afterCycle1.getReworkCount()).isEqualTo(1);

        // Cycle 2: QUALITY_CHECK → REWORK → IN_PROGRESS
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "REWORK", null, OPERATOR_ID, "cycle 2 defects");
        ProductionOrderDO afterCycle2 = transition(order.getId(), TENANT_A, "IN_PROGRESS",
                null, OPERATOR_ID, null);
        assertThat(afterCycle2.getReworkCount()).isEqualTo(2);

        // Final: QUALITY_CHECK → COMPLETED
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);
        ProductionOrderDO completed = transition(order.getId(), TENANT_A, "COMPLETED",
                new BigDecimal("10"), OPERATOR_ID, null);
        assertThat(completed.getProductionStage()).isEqualTo("COMPLETED");
        assertThat(completed.getReworkCount()).isEqualTo(2); // preserved from cycles
        assertThat(completed.getQualityCheckResult()).isEqualTo("PASS");

        // Verify stock events only written on final completion
        long prodOutEvents = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_OUT");
        long prodInEvents = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_IN");
        assertThat(prodOutEvents).isEqualTo(2);
        assertThat(prodInEvents).isEqualTo(1);
    }

    @Test
    void testQualityCheckReworkTenantIsolation() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);

        // Tenant B cannot transition Tenant A's order
        assertThatThrownBy(() -> transition(order.getId(), TENANT_B, "REWORK",
                null, OPERATOR_ID, "cross-tenant attempt"))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("not found");

        // Verify state unchanged for Tenant A
        ProductionOrderDO unchanged = productionOrderService.getProductionOrder(order.getId(), TENANT_A);
        assertThat(unchanged.getProductionStage()).isEqualTo("QUALITY_CHECK");
    }

    @Test
    void testTransitionOrderNotFound() {
        assertThatThrownBy(() -> transition(999999L, TENANT_A, "MATERIAL_REQUEST",
                null, null, null))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void testTransitionCrossTenant() {
        ProductionOrderDO order = createOrder();

        assertThatThrownBy(() -> transition(order.getId(), TENANT_B, "MATERIAL_REQUEST",
                null, null, null))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void testTransitionCancelledNoRemarkFails() {
        ProductionOrderDO order = createOrder();

        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "CANCELLED",
                null, null, null))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("remark");
    }

    @Test
    void testTransitionCompletedNoActualQtyFails() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "COMPLETED",
                null, OPERATOR_ID, null))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("actualQty");
    }

    // =========================================================================
    // Update tests
    // =========================================================================

    @Test
    void testUpdateInCreatedStage() {
        ProductionOrderDO order = createOrder();

        ProductionOrderUpdateReqVO updateReq = new ProductionOrderUpdateReqVO();
        updateReq.setId(order.getId());
        updateReq.setTenantId(TENANT_A);
        updateReq.setPlannedQty(new BigDecimal("200.0000"));
        updateReq.setRemark("updated remark");
        updateReq.setOperatorUserId(OPERATOR_ID);

        ProductionOrderDO updated = productionOrderService.updateProductionOrder(updateReq);

        assertThat(updated.getPlannedQty()).isEqualByComparingTo(new BigDecimal("200.0000"));
        assertThat(updated.getRemark()).isEqualTo("updated remark");
        assertThat(updated.getProductionStage()).isEqualTo("CREATED");
    }

    @Test
    void testUpdateInInProgressStage() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        ProductionOrderUpdateReqVO updateReq = new ProductionOrderUpdateReqVO();
        updateReq.setId(order.getId());
        updateReq.setTenantId(TENANT_A);
        updateReq.setPlannedQty(new BigDecimal("200.0000"));

        assertThatThrownBy(() -> productionOrderService.updateProductionOrder(updateReq))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("CREATED");
    }

    // =========================================================================
    // List / Page tests
    // =========================================================================

    @Test
    void testListByTenantAll() {
        createOrder();
        createOrder();

        var page = productionOrderService.listProductionOrders(TENANT_A, null, 1, 10);

        assertThat(page.getList()).hasSize(2);
        assertThat(page.getTotal()).isEqualTo(2L);
    }

    @Test
    void testListByTenantAndStage() {
        ProductionOrderDO order1 = createOrder();
        createOrder(); // order2 stays CREATED

        // Move order1 to MATERIAL_REQUEST
        transition(order1.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);

        var pageCreated = productionOrderService.listProductionOrders(TENANT_A, "CREATED", 1, 10);
        var pageMaterialRequest = productionOrderService.listProductionOrders(TENANT_A, "MATERIAL_REQUEST", 1, 10);

        assertThat(pageCreated.getList()).hasSize(1);
        assertThat(pageMaterialRequest.getList()).hasSize(1);
    }

    @Test
    void testListCrossTenant() {
        createOrder();

        TenantContextHolder.setTenantId(TENANT_B);
        var page = productionOrderService.listProductionOrders(TENANT_B, null, 1, 10);

        assertThat(page.getList()).isEmpty();
        assertThat(page.getTotal()).isEqualTo(0L);
    }

    // =========================================================================
    // G2-02L: Stock event integration tests
    // =========================================================================

    @Test
    void testCompleteOrderWithSingleComponentDeduction() {
        // This test uses the default 2-component BOM, but we verify the basic flow
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        BigDecimal actualQty = new BigDecimal("10");
        ProductionOrderDO result = transition(order.getId(), TENANT_A, "COMPLETED",
                actualQty, OPERATOR_ID, null);

        assertThat(result.getProductionStage()).isEqualTo("COMPLETED");

        // Verify PRODUCTION_OUT events (2 components)
        List<StockEventDO> outEvents = getEventsBySourceAndType(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_OUT");
        assertThat(outEvents).hasSize(2);

        // Verify PRODUCTION_IN event (1 semi-finished)
        List<StockEventDO> inEvents = getEventsBySourceAndType(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_IN");
        assertThat(inEvents).hasSize(1);
        assertThat(inEvents.get(0).getQuantity()).isEqualByComparingTo(actualQty);

        // Verify stock balance: raw materials decreased, semi-finished increased
        BigDecimal rm1After = stockBalanceService.getAvailableQty(TENANT_A, rawMaterialStockItemId, activeLocationId);
        BigDecimal rm2After = stockBalanceService.getAvailableQty(TENANT_A, rawMaterialStockItemId2, activeLocationId);
        BigDecimal semiAfter = stockBalanceService.getAvailableQty(TENANT_A, semiFinishedStockItemId, activeLocationId);

        // 1000 - (10 * 2) = 980
        assertThat(rm1After).isEqualByComparingTo(new BigDecimal("980.000000"));
        // 1000 - (10 * 3) = 970
        assertThat(rm2After).isEqualByComparingTo(new BigDecimal("970.000000"));
        // 0 + 10 = 10
        assertThat(semiAfter).isEqualByComparingTo(new BigDecimal("10.000000"));
    }

    @Test
    void testCompleteOrderActualQtyDiffersFromPlannedQty() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        // plannedQty = 100, actualQty = 8
        BigDecimal actualQty = new BigDecimal("8");
        ProductionOrderDO result = transition(order.getId(), TENANT_A, "COMPLETED",
                actualQty, OPERATOR_ID, null);

        assertThat(result.getProductionStage()).isEqualTo("COMPLETED");

        // Verify deduction uses actualQty=8, not plannedQty=100
        // 8 * 2 = 16 for raw_1, 8 * 3 = 24 for raw_2
        BigDecimal rm1After = stockBalanceService.getAvailableQty(TENANT_A, rawMaterialStockItemId, activeLocationId);
        BigDecimal rm2After = stockBalanceService.getAvailableQty(TENANT_A, rawMaterialStockItemId2, activeLocationId);

        // 1000 - 16 = 984 (NOT 1000 - 200 = 800)
        assertThat(rm1After).isEqualByComparingTo(new BigDecimal("984.000000"));
        // 1000 - 24 = 976 (NOT 1000 - 300 = 700)
        assertThat(rm2After).isEqualByComparingTo(new BigDecimal("976.000000"));
    }

    @Test
    void testCompleteOrderInsufficientStockRollback() {
        // Drain raw material 1 to insufficient level: need 10*2=20, give only 5
        // Current stock is 1000. We need to reduce it.
        // We can't directly update stock_balance, so we'll create a new location with low stock.
        Long lowStockLocation = seedLocation(TENANT_A, "LOC_LOW_STOCK", true);
        giveStock(TENANT_A, rawMaterialStockItemId, rawMaterialSkuCode, lowStockLocation, new BigDecimal("5"));
        giveStock(TENANT_A, rawMaterialStockItemId2, rawMaterialSkuCode2, lowStockLocation, new BigDecimal("1000"));

        ProductionOrderCreateReqVO req = buildCreateReq();
        req.setLocationId(lowStockLocation);
        ProductionOrderDO order = productionOrderService.createProductionOrder(req);

        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        // Should fail: insufficient stock for raw material 1 (need 20, have 5)
        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "COMPLETED",
                new BigDecimal("10"), OPERATOR_ID, null))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(2002004);
                    assertThat(e.getErrorCode()).isEqualTo(StockErrorCodeConstants.INSUFFICIENT_STOCK);
                });

        // Verify order stage rolled back to IN_PROGRESS
        ProductionOrderDO rolledBack = productionOrderService.getProductionOrder(order.getId(), TENANT_A);
        assertThat(rolledBack.getProductionStage()).isEqualTo("IN_PROGRESS");

        // Verify no stock events were written
        long events = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), null);
        assertThat(events).isEqualTo(0);
    }

    @Test
    void testCompleteOrderComponentSkuCodeMissing() {
        // Create a raw material product with null skuCode
        Long noSkuProductId = seedProductWithoutSkuCode(TENANT_A, "P_NO_SKU_001", "RAW_MATERIAL", true);

        // Create a new active recipe with this product as a component
        Long noSkuRecipeId = seedRecipe(TENANT_A, semiFinishedProductId, "ACTIVE");
        seedRecipeItem(TENANT_A, noSkuRecipeId, noSkuProductId, new BigDecimal("1"), "RAW_MATERIAL", "KG");

        // Create production order using this recipe
        ProductionOrderCreateReqVO req = buildCreateReq();
        req.setRecipeId(noSkuRecipeId);
        ProductionOrderDO order = productionOrderService.createProductionOrder(req);

        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        // Should fail: component product has no skuCode for BOM mapping
        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "COMPLETED",
                new BigDecimal("10"), OPERATOR_ID, null))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(2002039);
                    assertThat(e.getErrorCode()).isEqualTo(StockErrorCodeConstants.PRODUCTION_COMPONENT_SKU_CODE_MISSING);
                });

        // Verify order stage rolled back to IN_PROGRESS
        ProductionOrderDO rolledBack = productionOrderService.getProductionOrder(order.getId(), TENANT_A);
        assertThat(rolledBack.getProductionStage()).isEqualTo("IN_PROGRESS");

        // Verify no stock events were written
        long events = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), null);
        assertThat(events).isEqualTo(0);
    }

    @Test
    void testCompleteOrderStockItemMissing() {
        // Create a product with skuCode but no stock_item
        Long orphanProductId = seedProduct(TENANT_A, "P_ORPHAN_001", "RAW_MATERIAL", true);
        Long orphanRecipeId = seedRecipe(TENANT_A, semiFinishedProductId, "ACTIVE");
        // Clear existing recipe items and add orphan component
        seedRecipeItem(TENANT_A, orphanRecipeId, orphanProductId, new BigDecimal("1"), "RAW_MATERIAL", "KG");

        ProductionOrderCreateReqVO req = buildCreateReq();
        req.setRecipeId(orphanRecipeId);
        ProductionOrderDO order = productionOrderService.createProductionOrder(req);

        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        // Should fail: stock_item not found for orphan product (no stock_item mapped)
        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "COMPLETED",
                new BigDecimal("10"), OPERATOR_ID, null))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Stock item not found");

        // Verify order stage rolled back
        ProductionOrderDO rolledBack = productionOrderService.getProductionOrder(order.getId(), TENANT_A);
        assertThat(rolledBack.getProductionStage()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void testDuplicateCompleteNoDoubleWrite() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "COMPLETED", new BigDecimal("10"), OPERATOR_ID, null);

        // Try to complete again — should fail (terminal state)
        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "COMPLETED",
                new BigDecimal("10"), OPERATOR_ID, null))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("terminal");

        // Verify no duplicate events
        long outEvents = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_OUT");
        long inEvents = countEventsBySource(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_IN");
        assertThat(outEvents).isEqualTo(2);
        assertThat(inEvents).isEqualTo(1);
    }

    @Test
    void testBomTraceabilityFieldsPersisted() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "COMPLETED", new BigDecimal("10"), OPERATOR_ID, null);

        List<StockEventDO> allEvents = getEventsBySourceAndType(TENANT_A, "PRODUCTION", order.getId(), null);
        for (StockEventDO event : allEvents) {
            assertThat(event.getRecipeId()).isEqualTo(activeRecipeId);
            assertThat(event.getRecipeVersion()).isNotNull();
            assertThat(event.getSourceModule()).isEqualTo("PRODUCTION");
            assertThat(event.getSourceRecordId()).isEqualTo(order.getId());
            assertThat(event.getReferenceNo()).isEqualTo(order.getOrderNo());
        }
    }

    @Test
    void testProductionOutUsesActualQtyNotPlannedQty() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        BigDecimal actualQty = new BigDecimal("8");
        transition(order.getId(), TENANT_A, "COMPLETED", actualQty, OPERATOR_ID, null);

        // Verify PRODUCTION_OUT quantities: 8 * 2 = 16, 8 * 3 = 24
        List<StockEventDO> outEvents = getEventsBySourceAndType(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_OUT");
        assertThat(outEvents).hasSize(2);

        // Find event for raw material 1 (quantity should be 16, not 200)
        StockEventDO rm1Event = outEvents.stream()
                .filter(e -> e.getStockItemId().equals(rawMaterialStockItemId))
                .findFirst().orElseThrow();
        assertThat(rm1Event.getQuantity()).isEqualByComparingTo(new BigDecimal("16.000000"));

        // Find event for raw material 2 (quantity should be 24, not 300)
        StockEventDO rm2Event = outEvents.stream()
                .filter(e -> e.getStockItemId().equals(rawMaterialStockItemId2))
                .findFirst().orElseThrow();
        assertThat(rm2Event.getQuantity()).isEqualByComparingTo(new BigDecimal("24.000000"));
    }

    @Test
    void testProductionInQuantityEqualsActualQty() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        BigDecimal actualQty = new BigDecimal("42");
        transition(order.getId(), TENANT_A, "COMPLETED", actualQty, OPERATOR_ID, null);

        List<StockEventDO> inEvents = getEventsBySourceAndType(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_IN");
        assertThat(inEvents).hasSize(1);
        assertThat(inEvents.get(0).getQuantity()).isEqualByComparingTo(actualQty);
    }

    @Test
    void testClientRequestIdFormatCorrect() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "COMPLETED", new BigDecimal("10"), OPERATOR_ID, null);

        // Verify PRODUCTION_OUT clientRequestId format: PROD-OUT-{orderId}-{componentProductId}
        List<StockEventDO> outEvents = getEventsBySourceAndType(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_OUT");
        for (StockEventDO event : outEvents) {
            assertThat(event.getClientRequestId()).startsWith("PROD-OUT-" + order.getId() + "-");
        }

        // Verify PRODUCTION_IN clientRequestId format: PROD-IN-{orderId}
        List<StockEventDO> inEvents = getEventsBySourceAndType(TENANT_A, "PRODUCTION", order.getId(), "PRODUCTION_IN");
        assertThat(inEvents.get(0).getClientRequestId()).isEqualTo("PROD-IN-" + order.getId());
    }

    @Test
    void testCompleteOrderTenantIsolation() {
        // Tenant A completes order
        ProductionOrderDO orderA = createOrder();
        transition(orderA.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(orderA.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(orderA.getId(), TENANT_A, "COMPLETED", new BigDecimal("10"), OPERATOR_ID, null);

        // Tenant B has its own stock — should be unaffected
        TenantContextHolder.setTenantId(TENANT_B);
        // Seed tenant B data
        Long semiB = seedProduct(TENANT_B, "P_SEMI_B", "SEMI_FINISHED", true);
        Long rawB1 = seedProduct(TENANT_B, "P_RAW_B1", "RAW_MATERIAL", true);
        Long rawB2 = seedProduct(TENANT_B, "P_RAW_B2", "RAW_MATERIAL", true);
        Long recipeB = seedRecipe(TENANT_B, semiB, "ACTIVE");
        seedRecipeItem(TENANT_B, recipeB, rawB1, new BigDecimal("2"), "RAW_MATERIAL", "KG");
        seedRecipeItem(TENANT_B, recipeB, rawB2, new BigDecimal("3"), "RAW_MATERIAL", "KG");
        Long locB = seedLocation(TENANT_B, "LOC_B", true);
        Long semiItemB = seedStockItem(TENANT_B, "SKU_P_SEMI_B", "Semi B", false, true, false);
        Long rawItemB1 = seedStockItem(TENANT_B, "SKU_P_RAW_B1", "Raw B1", true, false, false);
        Long rawItemB2 = seedStockItem(TENANT_B, "SKU_P_RAW_B2", "Raw B2", true, false, false);
        giveStock(TENANT_B, rawItemB1, "SKU_P_RAW_B1", locB, new BigDecimal("1000"));
        giveStock(TENANT_B, rawItemB2, "SKU_P_RAW_B2", locB, new BigDecimal("1000"));

        ProductionOrderCreateReqVO reqB = new ProductionOrderCreateReqVO();
        reqB.setTenantId(TENANT_B);
        reqB.setProductId(semiB);
        reqB.setRecipeId(recipeB);
        reqB.setLocationId(locB);
        reqB.setPlannedQty(new BigDecimal("100"));
        reqB.setOperatorUserId(OPERATOR_ID);
        ProductionOrderDO orderB = productionOrderService.createProductionOrder(reqB);
        transition(orderB.getId(), TENANT_B, "MATERIAL_REQUEST", null, null, null);
        transition(orderB.getId(), TENANT_B, "IN_PROGRESS", null, OPERATOR_ID, null);
        transition(orderB.getId(), TENANT_B, "COMPLETED", new BigDecimal("5"), OPERATOR_ID, null);

        // Verify tenant A stock is independent
        TenantContextHolder.setTenantId(TENANT_A);
        BigDecimal rm1A = stockBalanceService.getAvailableQty(TENANT_A, rawMaterialStockItemId, activeLocationId);
        // 1000 - 10*2 = 980 (only tenant A's order affected it)
        assertThat(rm1A).isEqualByComparingTo(new BigDecimal("980.000000"));

        // Verify tenant B stock
        TenantContextHolder.setTenantId(TENANT_B);
        BigDecimal rm1B = stockBalanceService.getAvailableQty(TENANT_B, rawItemB1, locB);
        // 1000 - 5*2 = 990
        assertThat(rm1B).isEqualByComparingTo(new BigDecimal("990.000000"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ProductionOrderCreateReqVO buildCreateReq() {
        ProductionOrderCreateReqVO req = new ProductionOrderCreateReqVO();
        req.setTenantId(TENANT_A);
        req.setProductId(semiFinishedProductId);
        req.setRecipeId(activeRecipeId);
        req.setLocationId(activeLocationId);
        req.setPlannedQty(new BigDecimal("100.0000"));
        req.setOperatorUserId(OPERATOR_ID);
        req.setRemark("test production order");
        return req;
    }

    private ProductionOrderDO createOrder() {
        return productionOrderService.createProductionOrder(buildCreateReq());
    }

    private ProductionOrderDO transition(Long id, Long tenantId, String targetStage,
                                          BigDecimal actualQty, Long operatorUserId, String remark) {
        ProductionOrderStageTransitionReqVO req = new ProductionOrderStageTransitionReqVO();
        req.setId(id);
        req.setTenantId(tenantId);
        req.setTargetStage(targetStage);
        req.setActualQty(actualQty);
        req.setOperatorUserId(operatorUserId);
        req.setRemark(remark);
        return productionOrderService.transitionStage(req);
    }

    private Long seedProduct(Long tenantId, String code, String productType, boolean isActive) {
        ProductMasterDO product = new ProductMasterDO();
        product.setTenantId(tenantId);
        product.setProductCode(code);
        product.setProductName("Test Product " + code);
        product.setProductType(productType);
        product.setSkuCode("SKU_" + code);
        product.setUnit("KG");
        product.setIsActive(isActive);
        product.setCreator("system");
        product.setCreateTime(LocalDateTime.now());
        product.setUpdater("system");
        product.setUpdateTime(LocalDateTime.now());
        product.setDeleted(false);
        productMasterMapper.insert(product);
        return product.getId();
    }

    private Long seedProductWithoutSkuCode(Long tenantId, String code, String productType, boolean isActive) {
        ProductMasterDO product = new ProductMasterDO();
        product.setTenantId(tenantId);
        product.setProductCode(code);
        product.setProductName("Test Product " + code);
        product.setProductType(productType);
        product.setSkuCode(null); // ★ null skuCode to trigger PRODUCTION_COMPONENT_SKU_CODE_MISSING
        product.setUnit("KG");
        product.setIsActive(isActive);
        product.setCreator("system");
        product.setCreateTime(LocalDateTime.now());
        product.setUpdater("system");
        product.setUpdateTime(LocalDateTime.now());
        product.setDeleted(false);
        productMasterMapper.insert(product);
        return product.getId();
    }

    private Long seedRecipe(Long tenantId, Long productId, String status) {
        BomRecipeDO recipe = new BomRecipeDO();
        recipe.setTenantId(tenantId);
        recipe.setProductId(productId);
        recipe.setVersionNo(1);
        recipe.setStatus(status);
        recipe.setOutputQuantity(BigDecimal.ONE);
        recipe.setOutputUnit("KG");
        recipe.setCreator("system");
        recipe.setCreateTime(LocalDateTime.now());
        recipe.setUpdater("system");
        recipe.setUpdateTime(LocalDateTime.now());
        recipe.setDeleted(false);
        bomRecipeMapper.insert(recipe);
        return recipe.getId();
    }

    private void seedRecipeItem(Long tenantId, Long recipeId, Long componentProductId,
                                 BigDecimal quantity, String componentType, String unit) {
        BomRecipeItemDO item = new BomRecipeItemDO();
        item.setTenantId(tenantId);
        item.setRecipeId(recipeId);
        item.setComponentProductId(componentProductId);
        item.setQuantity(quantity);
        item.setWasteRate(BigDecimal.ZERO);
        item.setComponentType(componentType);
        item.setUnit(unit);
        item.setCreator("system");
        item.setCreateTime(LocalDateTime.now());
        item.setUpdater("system");
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleted(false);
        bomRecipeItemMapper.insert(item);
    }

    private Long seedLocation(Long tenantId, String code, boolean isActive) {
        StockLocationDO location = new StockLocationDO();
        location.setTenantId(tenantId);
        location.setLocationCode(code);
        location.setLocationName("Test Location " + code);
        location.setLocationType("CENTRAL_KITCHEN_" + code);
        location.setStoreId(System.identityHashCode(code) % 1000 + 1L);
        location.setIsActive(isActive);
        location.setCreator("system");
        location.setCreateTime(LocalDateTime.now());
        location.setUpdater("system");
        location.setUpdateTime(LocalDateTime.now());
        location.setDeleted(false);
        stockLocationMapper.insert(location);
        return location.getId();
    }

    private Long seedStockItem(Long tenantId, String skuCode, String itemName,
                                boolean isRaw, boolean isSemi, boolean isFinished) {
        StockItemDO item = new StockItemDO();
        item.setTenantId(tenantId);
        item.setSkuCode(skuCode);
        item.setItemName(itemName);
        item.setUnit("KG");
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
        return item.getId();
    }

    private void giveStock(Long tenantId, Long stockItemId, String skuCode,
                           Long locationId, BigDecimal qty) {
        StockEventReqDTO req = new StockEventReqDTO();
        req.setTenantId(tenantId);
        req.setEventTime(LocalDateTime.now());
        req.setBusinessDate(java.time.LocalDate.now());
        req.setEventType(StockEventTypeEnum.PURCHASE_IN.getCode());
        req.setDirection(StockDirectionEnum.IN.getCode());
        req.setStockItemId(stockItemId);
        req.setSkuCode(skuCode);
        req.setLocationId(locationId);
        req.setQuantity(qty);
        req.setUnit("KG");
        req.setOperatorUserId(OPERATOR_ID);
        req.setClientRequestId("stock-in-" + UUID.randomUUID());
        stockEventService.recordEvent(req);
    }

    private long countEventsBySource(Long tenantId, String sourceModule,
                                      Long sourceRecordId, String eventType) {
        List<StockEventDO> events = getEventsBySourceAndType(tenantId, sourceModule, sourceRecordId, eventType);
        return events.size();
    }

    private List<StockEventDO> getEventsBySourceAndType(Long tenantId, String sourceModule,
                                                          Long sourceRecordId, String eventType) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockEventDO> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockEventDO>()
                        .eq(StockEventDO::getTenantId, tenantId)
                        .eq(StockEventDO::getSourceModule, sourceModule)
                        .eq(StockEventDO::getSourceRecordId, sourceRecordId);
        if (eventType != null) {
            wrapper.eq(StockEventDO::getEventType, eventType);
        }
        return stockEventMapper.selectList(wrapper);
    }
}
