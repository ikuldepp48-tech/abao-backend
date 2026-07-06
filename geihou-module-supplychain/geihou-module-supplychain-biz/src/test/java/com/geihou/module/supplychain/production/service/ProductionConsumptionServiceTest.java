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
import com.geihou.module.supplychain.production.controller.admin.vo.ScanPickReqVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ScanOutputReqVO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderConsumptionDO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderDO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderOutputDO;
import com.geihou.module.supplychain.production.dal.mapper.ProductionOrderConsumptionMapper;
import com.geihou.module.supplychain.production.dal.mapper.ProductionOrderMapper;
import com.geihou.module.supplychain.production.dal.mapper.ProductionOrderOutputMapper;
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
 * Tests for {@link ProductionConsumptionService} (scan-pick / 领料记录).
 *
 * <p>Covers: scan-pick success/failure, BOM component validation, insufficient stock,
 * stock_item missing, duplicate pickSeq idempotency, multiple picks accumulation,
 * tenant isolation, 方案 A completion behavior, cancel blocking, rework with consumption.
 *
 * <p>Source: TASK-G2-02N §8.1, §8.3, §8.4.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:prod_consumption_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ProductionConsumptionServiceTest {

    @Autowired
    private ProductionConsumptionService productionConsumptionService;
    @Autowired
    private ProductionOutputService productionOutputService;
    @Autowired
    private ProductionOrderService productionOrderService;
    @Autowired
    private ProductionOrderMapper productionOrderMapper;
    @Autowired
    private ProductionOrderConsumptionMapper consumptionMapper;
    @Autowired
    private ProductionOrderOutputMapper outputMapper;
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
    private Long rawMaterialProductId;
    private Long rawMaterialProductId2;
    private Long activeRecipeId;
    private Long activeLocationId;
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

        semiFinishedProductId = seedProduct(TENANT_A, "P_SEMI_001", "SEMI_FINISHED", true);
        rawMaterialProductId = seedProduct(TENANT_A, "P_RAW_001", "RAW_MATERIAL", true);
        rawMaterialProductId2 = seedProduct(TENANT_A, "P_RAW_002", "RAW_MATERIAL", true);

        semiFinishedSkuCode = "SKU_P_SEMI_001";
        rawMaterialSkuCode = "SKU_P_RAW_001";
        rawMaterialSkuCode2 = "SKU_P_RAW_002";

        activeRecipeId = seedRecipe(TENANT_A, semiFinishedProductId, "ACTIVE");
        // 1 semi-finished = 2 * raw_1 + 3 * raw_2
        seedRecipeItem(TENANT_A, activeRecipeId, rawMaterialProductId, new BigDecimal("2"), "RAW_MATERIAL", "KG");
        seedRecipeItem(TENANT_A, activeRecipeId, rawMaterialProductId2, new BigDecimal("3"), "RAW_MATERIAL", "KG");

        activeLocationId = seedLocation(TENANT_A, "LOC_ACTIVE_001", true);

        semiFinishedStockItemId = seedStockItem(TENANT_A, semiFinishedSkuCode, "Semi-Finished Item", false, true, false);
        rawMaterialStockItemId = seedStockItem(TENANT_A, rawMaterialSkuCode, "Raw Material 1 Item", true, false, false);
        rawMaterialStockItemId2 = seedStockItem(TENANT_A, rawMaterialSkuCode2, "Raw Material 2 Item", true, false, false);

        giveStock(TENANT_A, rawMaterialStockItemId, rawMaterialSkuCode, activeLocationId, new BigDecimal("1000"));
        giveStock(TENANT_A, rawMaterialStockItemId2, rawMaterialSkuCode2, activeLocationId, new BigDecimal("1000"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // =========================================================================
    // §8.1 scan-pick 领料记录
    // =========================================================================

    @Test
    void testScanPickSuccess() {
        ProductionOrderDO order = createInProgressOrder();
        ScanPickReqVO req = buildScanPickReq(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("20"), 1);

        ProductionOrderConsumptionDO result = productionConsumptionService.scanPick(req);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getTenantId()).isEqualTo(TENANT_A);
        assertThat(result.getProductionOrderId()).isEqualTo(order.getId());
        assertThat(result.getInputSkuId()).isEqualTo(rawMaterialProductId);
        assertThat(result.getPickSeq()).isEqualTo(1);
        assertThat(result.getActualQty()).isEqualByComparingTo(new BigDecimal("20"));
        // plannedQty for order plannedQty=100: 100 * 2 = 200
        assertThat(result.getPlannedQty()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(result.getDiffQty()).isEqualByComparingTo(new BigDecimal("-180"));
        assertThat(result.getStockEventId()).isNotNull();

        // Verify PRODUCTION_OUT stock_event written
        long outEvents = countEvents(TENANT_A, order.getId(), "PRODUCTION_OUT");
        assertThat(outEvents).isEqualTo(1);

        // Verify stock balance decreased
        BigDecimal rm1After = stockBalanceService.getAvailableQty(TENANT_A, rawMaterialStockItemId, activeLocationId);
        assertThat(rm1After).isEqualByComparingTo(new BigDecimal("980.000000"));
    }

    @Test
    void testScanPickFailWrongStage() {
        ProductionOrderDO order = createOrder(); // CREATED stage

        ScanPickReqVO req = buildScanPickReq(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("20"), 1);

        assertThatThrownBy(() -> productionConsumptionService.scanPick(req))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_SCAN_NOT_ALLOWED);
                });

        // Verify no consumption record, no stock_event
        assertThat(consumptionMapper.countByOrder(TENANT_A, order.getId())).isEqualTo(0);
        assertThat(countEvents(TENANT_A, order.getId(), null)).isEqualTo(0);
    }

    @Test
    void testScanPickFailComponentNotInBom() {
        ProductionOrderDO order = createInProgressOrder();
        // Create a product not in the BOM
        Long notInBomProductId = seedProduct(TENANT_A, "P_NOT_BOM", "RAW_MATERIAL", true);

        ScanPickReqVO req = buildScanPickReq(order.getId(), TENANT_A, notInBomProductId, new BigDecimal("5"), 1);

        assertThatThrownBy(() -> productionConsumptionService.scanPick(req))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_PICK_COMPONENT_NOT_IN_BOM);
                });
    }

    @Test
    void testScanPickFailQtyZero() {
        ProductionOrderDO order = createInProgressOrder();

        ScanPickReqVO req = buildScanPickReq(order.getId(), TENANT_A, rawMaterialProductId, BigDecimal.ZERO, 1);

        assertThatThrownBy(() -> productionConsumptionService.scanPick(req))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.QUANTITY_MUST_BE_POSITIVE);
                });
    }

    @Test
    void testScanPickFailQtyNegative() {
        ProductionOrderDO order = createInProgressOrder();

        ScanPickReqVO req = buildScanPickReq(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("-1"), 1);

        assertThatThrownBy(() -> productionConsumptionService.scanPick(req))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.QUANTITY_MUST_BE_POSITIVE);
                });
    }

    @Test
    void testScanPickFailInsufficientStock() {
        ProductionOrderDO order = createInProgressOrder();

        // Pick more than available stock
        ScanPickReqVO req = buildScanPickReq(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("2000"), 1);

        assertThatThrownBy(() -> productionConsumptionService.scanPick(req))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.INSUFFICIENT_STOCK);
                });

        // Verify no consumption record written
        assertThat(consumptionMapper.countByOrder(TENANT_A, order.getId())).isEqualTo(0);
    }

    @Test
    void testScanPickFailStockItemNotFound() {
        // Seed a raw material product that is in the BOM but has no stock_item mapping
        Long rawMaterialNoStockId = seedProduct(TENANT_A, "P_RAW_NO_STOCK", "RAW_MATERIAL", true);
        // Create a separate recipe that uses this product
        Long recipeNoStockId = seedRecipe(TENANT_A, semiFinishedProductId, "ACTIVE");
        seedRecipeItem(TENANT_A, recipeNoStockId, rawMaterialNoStockId, new BigDecimal("1"), "RAW_MATERIAL", "KG");

        ProductionOrderCreateReqVO req = new ProductionOrderCreateReqVO();
        req.setTenantId(TENANT_A);
        req.setProductId(semiFinishedProductId);
        req.setRecipeId(recipeNoStockId);
        req.setLocationId(activeLocationId);
        req.setPlannedQty(new BigDecimal("10"));
        req.setOperatorUserId(OPERATOR_ID);
        ProductionOrderDO order = productionOrderService.createProductionOrder(req);
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        ScanPickReqVO pickReq = buildScanPickReq(order.getId(), TENANT_A, rawMaterialNoStockId, new BigDecimal("5"), 1);

        // stock_item not found for this product's skuCode → PRODUCTION_STOCK_ITEM_NOT_FOUND
        assertThatThrownBy(() -> productionConsumptionService.scanPick(pickReq))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_STOCK_ITEM_NOT_FOUND);
                });

        // Verify no consumption record written
        assertThat(consumptionMapper.countByOrder(TENANT_A, order.getId())).isEqualTo(0);
    }

    @Test
    void testScanPickFailDuplicatePickSeq() {
        ProductionOrderDO order = createInProgressOrder();

        // First pick succeeds
        ScanPickReqVO req1 = buildScanPickReq(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("10"), 1);
        productionConsumptionService.scanPick(req1);

        // Second pick with same pickSeq for same component fails
        ScanPickReqVO req2 = buildScanPickReq(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("10"), 1);

        assertThatThrownBy(() -> productionConsumptionService.scanPick(req2))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_SEQ_ALREADY_EXISTS);
                });

        // Verify only one consumption record
        List<ProductionOrderConsumptionDO> records = consumptionMapper.listByOrder(TENANT_A, order.getId());
        assertThat(records).hasSize(1);
    }

    @Test
    void testScanPickMultiplePicksSameComponent() {
        ProductionOrderDO order = createInProgressOrder();

        // Pick 1: 10 units
        ScanPickReqVO req1 = buildScanPickReq(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("10"), 1);
        productionConsumptionService.scanPick(req1);

        // Pick 2: 10 units (different pickSeq)
        ScanPickReqVO req2 = buildScanPickReq(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("10"), 2);
        productionConsumptionService.scanPick(req2);

        List<ProductionOrderConsumptionDO> records = consumptionMapper.listByOrder(TENANT_A, order.getId());
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getPickSeq()).isEqualTo(1);
        assertThat(records.get(1).getPickSeq()).isEqualTo(2);

        // Cumulative actual_qty = 20
        BigDecimal cumulative = records.stream()
                .map(ProductionOrderConsumptionDO::getActualQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(cumulative).isEqualByComparingTo(new BigDecimal("20"));

        // 2 PRODUCTION_OUT events
        assertThat(countEvents(TENANT_A, order.getId(), "PRODUCTION_OUT")).isEqualTo(2);
    }

    @Test
    void testScanPickTenantIsolation() {
        ProductionOrderDO order = createInProgressOrder();

        // Tenant B cannot scan-pick Tenant A's order
        ScanPickReqVO req = buildScanPickReq(order.getId(), TENANT_B, rawMaterialProductId, new BigDecimal("10"), 1);

        assertThatThrownBy(() -> productionConsumptionService.scanPick(req))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_ORDER_NOT_FOUND);
                });

        // Verify no consumption record
        assertThat(consumptionMapper.countByOrder(TENANT_A, order.getId())).isEqualTo(0);
    }

    // =========================================================================
    // §8.3 方案 A 完工行为
    // =========================================================================

    @Test
    void testPlanACompletionWithConsumptionNoOutputWritesOnlyProductionIn() {
        ProductionOrderDO order = createInProgressOrder();

        // Pick all required materials for actualQty=10: raw_1 needs 20, raw_2 needs 30
        scanPick(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("20"), 1);
        scanPick(order.getId(), TENANT_A, rawMaterialProductId2, new BigDecimal("30"), 1);

        // Complete the order
        ProductionOrderDO result = transition(order.getId(), TENANT_A, "COMPLETED", new BigDecimal("10"), OPERATOR_ID, null);

        assertThat(result.getProductionStage()).isEqualTo("COMPLETED");

        // 方案 A: no bulk PRODUCTION_OUT (picks already wrote 2 PRODUCTION_OUT)
        // Only 1 PRODUCTION_IN written at completion (no output records)
        long outEvents = countEvents(TENANT_A, order.getId(), "PRODUCTION_OUT");
        long inEvents = countEvents(TENANT_A, order.getId(), "PRODUCTION_IN");
        assertThat(outEvents).isEqualTo(2); // from scan-pick only
        assertThat(inEvents).isEqualTo(1);  // from completion
    }

    @Test
    void testPlanACompletionInsufficientPicksFails() {
        ProductionOrderDO order = createInProgressOrder();

        // Pick insufficient raw_1: need 20 (10*2), only pick 10
        scanPick(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("10"), 1);
        // Pick sufficient raw_2: need 30 (10*3), pick 30
        scanPick(order.getId(), TENANT_A, rawMaterialProductId2, new BigDecimal("30"), 1);

        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "COMPLETED",
                new BigDecimal("10"), OPERATOR_ID, null))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_PICK_INSUFFICIENT_FOR_COMPLETION);
                });

        // Verify stage unchanged
        ProductionOrderDO unchanged = productionOrderService.getProductionOrder(order.getId(), TENANT_A);
        assertThat(unchanged.getProductionStage()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void testPlanACompletionNoConsumptionKeepsBulkBehavior() {
        ProductionOrderDO order = createInProgressOrder();

        // Complete without any scan-pick — bulk behavior
        ProductionOrderDO result = transition(order.getId(), TENANT_A, "COMPLETED",
                new BigDecimal("10"), OPERATOR_ID, null);

        assertThat(result.getProductionStage()).isEqualTo("COMPLETED");

        // Bulk: 2 PRODUCTION_OUT + 1 PRODUCTION_IN
        assertThat(countEvents(TENANT_A, order.getId(), "PRODUCTION_OUT")).isEqualTo(2);
        assertThat(countEvents(TENANT_A, order.getId(), "PRODUCTION_IN")).isEqualTo(1);
    }

    @Test
    void testPlanACompletionWithConsumptionAndOutputNoDuplicateProductionIn() {
        ProductionOrderDO order = createInProgressOrder();

        // Pick all required materials for actualQty=10: raw_1 needs 20, raw_2 needs 30
        scanPick(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("20"), 1);
        scanPick(order.getId(), TENANT_A, rawMaterialProductId2, new BigDecimal("30"), 1);

        // scan-output: writes PRODUCTION_IN at scan-output time
        scanOutput(order.getId(), TENANT_A, new BigDecimal("10"), 1);

        // Complete the order
        ProductionOrderDO result = transition(order.getId(), TENANT_A, "COMPLETED",
                new BigDecimal("10"), OPERATOR_ID, null);

        assertThat(result.getProductionStage()).isEqualTo("COMPLETED");

        // 方案 A: 2 PRODUCTION_OUT (from scan-pick), 1 PRODUCTION_IN (from scan-output)
        // COMPLETED must NOT write an additional PRODUCTION_IN since output records exist
        long outEvents = countEvents(TENANT_A, order.getId(), "PRODUCTION_OUT");
        long inEvents = countEvents(TENANT_A, order.getId(), "PRODUCTION_IN");
        assertThat(outEvents).isEqualTo(2); // from scan-pick only
        assertThat(inEvents).isEqualTo(1);  // from scan-output only, no duplicate
    }

    // =========================================================================
    // §8.4 取消阻断
    // =========================================================================

    @Test
    void testCancelBlockedByConsumptionInProgress() {
        ProductionOrderDO order = createInProgressOrder();
        scanPick(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("10"), 1);

        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "CANCELLED",
                null, null, "cancel reason"))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_CANCEL_BLOCKED_BY_CONSUMPTION);
                });

        // Verify stage unchanged
        ProductionOrderDO unchanged = productionOrderService.getProductionOrder(order.getId(), TENANT_A);
        assertThat(unchanged.getProductionStage()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void testCancelBlockedByConsumptionQualityCheck() {
        ProductionOrderDO order = createInProgressOrder();
        scanPick(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("10"), 1);
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);

        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "CANCELLED",
                null, null, "cancel from qc"))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_CANCEL_BLOCKED_BY_CONSUMPTION);
                });
    }

    @Test
    void testCancelBlockedByConsumptionRework() {
        ProductionOrderDO order = createInProgressOrder();
        scanPick(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("10"), 1);
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "REWORK", null, OPERATOR_ID, "defects");

        assertThatThrownBy(() -> transition(order.getId(), TENANT_A, "CANCELLED",
                null, null, "cancel from rework"))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_CANCEL_BLOCKED_BY_CONSUMPTION);
                });
    }

    @Test
    void testCancelWithoutConsumptionSucceeds() {
        // IN_PROGRESS → CANCELLED with no consumption
        ProductionOrderDO order = createInProgressOrder();

        ProductionOrderDO result = transition(order.getId(), TENANT_A, "CANCELLED",
                null, null, "cancel reason");

        assertThat(result.getProductionStage()).isEqualTo("CANCELLED");
    }

    @Test
    void testReworkAllowedWithConsumption() {
        ProductionOrderDO order = createInProgressOrder();
        scanPick(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("10"), 1);
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);

        // REWORK should succeed even with consumption
        ProductionOrderDO result = transition(order.getId(), TENANT_A, "REWORK",
                null, OPERATOR_ID, "defects found");

        assertThat(result.getProductionStage()).isEqualTo("REWORK");

        // Consumption records preserved
        assertThat(consumptionMapper.countByOrder(TENANT_A, order.getId())).isEqualTo(1);
    }

    @Test
    void testReworkToInProgressCanContinueScanPick() {
        ProductionOrderDO order = createInProgressOrder();
        scanPick(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("10"), 1);
        transition(order.getId(), TENANT_A, "QUALITY_CHECK", null, OPERATOR_ID, null);
        transition(order.getId(), TENANT_A, "REWORK", null, OPERATOR_ID, "defects");
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);

        // Continue scan-pick with pickSeq=2
        ProductionOrderConsumptionDO result = scanPick(order.getId(), TENANT_A,
                rawMaterialProductId, new BigDecimal("10"), 2);

        assertThat(result.getPickSeq()).isEqualTo(2);

        List<ProductionOrderConsumptionDO> records = consumptionMapper.listByOrder(TENANT_A, order.getId());
        assertThat(records).hasSize(2);
    }

    // =========================================================================
    // §8.6 幂等 — clientRequestId idempotency
    // =========================================================================

    @Test
    void testScanPickClientRequestIdIdempotency() {
        ProductionOrderDO order = createInProgressOrder();

        // First pick succeeds
        scanPick(order.getId(), TENANT_A, rawMaterialProductId, new BigDecimal("10"), 1);

        // Same pickSeq should fail (duplicate) — idempotent
        assertThatThrownBy(() -> scanPick(order.getId(), TENANT_A,
                rawMaterialProductId, new BigDecimal("10"), 1))
                .isInstanceOf(StockBusinessException.class);

        // Only 1 PRODUCTION_OUT event
        assertThat(countEvents(TENANT_A, order.getId(), "PRODUCTION_OUT")).isEqualTo(1);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ProductionOrderDO createOrder() {
        ProductionOrderCreateReqVO req = new ProductionOrderCreateReqVO();
        req.setTenantId(TENANT_A);
        req.setProductId(semiFinishedProductId);
        req.setRecipeId(activeRecipeId);
        req.setLocationId(activeLocationId);
        req.setPlannedQty(new BigDecimal("100"));
        req.setOperatorUserId(OPERATOR_ID);
        req.setRemark("test production order");
        return productionOrderService.createProductionOrder(req);
    }

    private ProductionOrderDO createInProgressOrder() {
        ProductionOrderDO order = createOrder();
        transition(order.getId(), TENANT_A, "MATERIAL_REQUEST", null, null, null);
        transition(order.getId(), TENANT_A, "IN_PROGRESS", null, OPERATOR_ID, null);
        return order;
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

    private ScanPickReqVO buildScanPickReq(Long orderId, Long tenantId, Long componentProductId,
                                            BigDecimal actualQty, Integer pickSeq) {
        ScanPickReqVO req = new ScanPickReqVO();
        req.setOrderId(orderId);
        req.setTenantId(tenantId);
        req.setComponentProductId(componentProductId);
        req.setActualQty(actualQty);
        req.setPickSeq(pickSeq);
        req.setOperatorUserId(OPERATOR_ID);
        return req;
    }

    private ProductionOrderConsumptionDO scanPick(Long orderId, Long tenantId,
                                                   Long componentProductId, BigDecimal actualQty, Integer pickSeq) {
        return productionConsumptionService.scanPick(buildScanPickReq(orderId, tenantId, componentProductId, actualQty, pickSeq));
    }

    private ProductionOrderOutputDO scanOutput(Long orderId, Long tenantId,
                                                BigDecimal actualOutputQty, Integer outputSeq) {
        ScanOutputReqVO req = new ScanOutputReqVO();
        req.setOrderId(orderId);
        req.setTenantId(tenantId);
        req.setActualOutputQty(actualOutputQty);
        req.setOutputSeq(outputSeq);
        req.setOperatorUserId(OPERATOR_ID);
        return productionOutputService.scanOutput(req);
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

    private long countEvents(Long tenantId, Long sourceRecordId, String eventType) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockEventDO> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockEventDO>()
                        .eq(StockEventDO::getTenantId, tenantId)
                        .eq(StockEventDO::getSourceModule, "PRODUCTION")
                        .eq(StockEventDO::getSourceRecordId, sourceRecordId);
        if (eventType != null) {
            wrapper.eq(StockEventDO::getEventType, eventType);
        }
        return stockEventMapper.selectList(wrapper).size();
    }
}
