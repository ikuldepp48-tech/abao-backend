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
import com.geihou.module.supplychain.production.controller.admin.vo.ScanOutputReqVO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderDO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderOutputDO;
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
 * Tests for {@link ProductionOutputService} (scan-output / 产出登记).
 *
 * <p>Covers: scan-output success/failure, stage validation, output SKU mismatch,
 * qty validation, duplicate outputSeq idempotency, multiple outputs aggregation,
 * tenant isolation, default outputProductId behavior.
 *
 * <p>Source: TASK-G2-02N §8.2.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:prod_output_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ProductionOutputServiceTest {

    @Autowired
    private ProductionOutputService productionOutputService;
    @Autowired
    private ProductionOrderService productionOrderService;
    @Autowired
    private ProductionOrderMapper productionOrderMapper;
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
    // §8.2 scan-output 产出登记
    // =========================================================================

    @Test
    void testScanOutputSuccess() {
        ProductionOrderDO order = createInProgressOrder();

        ProductionOrderOutputDO result = scanOutput(order.getId(), TENANT_A, new BigDecimal("10"), 1);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getTenantId()).isEqualTo(TENANT_A);
        assertThat(result.getProductionOrderId()).isEqualTo(order.getId());
        assertThat(result.getOutputSkuId()).isEqualTo(semiFinishedProductId);
        assertThat(result.getOutputSeq()).isEqualTo(1);
        assertThat(result.getActualOutputQty()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(result.getStockEventId()).isNotNull();

        // Verify PRODUCTION_IN stock_event written
        long inEvents = countEvents(TENANT_A, order.getId(), "PRODUCTION_IN");
        assertThat(inEvents).isEqualTo(1);

        // Verify semi-finished stock balance increased
        BigDecimal semiAfter = stockBalanceService.getAvailableQty(TENANT_A, semiFinishedStockItemId, activeLocationId);
        assertThat(semiAfter).isEqualByComparingTo(new BigDecimal("10.000000"));
    }

    @Test
    void testScanOutputFailWrongStage() {
        ProductionOrderDO order = createOrder(); // CREATED stage

        ScanOutputReqVO req = buildScanOutputReq(order.getId(), TENANT_A, new BigDecimal("10"), 1);

        assertThatThrownBy(() -> productionOutputService.scanOutput(req))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_SCAN_NOT_ALLOWED);
                });

        // Verify no output record, no stock_event
        assertThat(outputMapper.countByOrder(TENANT_A, order.getId())).isEqualTo(0);
        assertThat(countEvents(TENANT_A, order.getId(), null)).isEqualTo(0);
    }

    @Test
    void testScanOutputFailProductIdMismatch() {
        ProductionOrderDO order = createInProgressOrder();

        // Create a different semi-finished product
        Long wrongProductId = seedProduct(TENANT_A, "P_SEMI_WRONG", "SEMI_FINISHED", true);

        ScanOutputReqVO req = buildScanOutputReq(order.getId(), TENANT_A, new BigDecimal("10"), 1);
        req.setOutputProductId(wrongProductId);

        assertThatThrownBy(() -> productionOutputService.scanOutput(req))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_OUTPUT_SKU_MISMATCH);
                });

        // Verify no output record, no stock_event
        assertThat(outputMapper.countByOrder(TENANT_A, order.getId())).isEqualTo(0);
        assertThat(countEvents(TENANT_A, order.getId(), null)).isEqualTo(0);
    }

    @Test
    void testScanOutputFailQtyZero() {
        ProductionOrderDO order = createInProgressOrder();

        ScanOutputReqVO req = buildScanOutputReq(order.getId(), TENANT_A, BigDecimal.ZERO, 1);

        assertThatThrownBy(() -> productionOutputService.scanOutput(req))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.QUANTITY_MUST_BE_POSITIVE);
                });
    }

    @Test
    void testScanOutputFailQtyNegative() {
        ProductionOrderDO order = createInProgressOrder();

        ScanOutputReqVO req = buildScanOutputReq(order.getId(), TENANT_A, new BigDecimal("-1"), 1);

        assertThatThrownBy(() -> productionOutputService.scanOutput(req))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.QUANTITY_MUST_BE_POSITIVE);
                });
    }

    @Test
    void testScanOutputFailDuplicateOutputSeq() {
        ProductionOrderDO order = createInProgressOrder();

        // First output succeeds
        scanOutput(order.getId(), TENANT_A, new BigDecimal("10"), 1);

        // Second output with same outputSeq fails
        ScanOutputReqVO req2 = buildScanOutputReq(order.getId(), TENANT_A, new BigDecimal("5"), 1);

        assertThatThrownBy(() -> productionOutputService.scanOutput(req2))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_SEQ_ALREADY_EXISTS);
                });

        // Verify only one output record and one PRODUCTION_IN event (no duplicate)
        List<ProductionOrderOutputDO> records = outputMapper.listByOrder(TENANT_A, order.getId());
        assertThat(records).hasSize(1);
        assertThat(countEvents(TENANT_A, order.getId(), "PRODUCTION_IN")).isEqualTo(1);
    }

    @Test
    void testScanOutputMultipleOutputsAggregation() {
        ProductionOrderDO order = createInProgressOrder();

        // Output 1: 10 units
        scanOutput(order.getId(), TENANT_A, new BigDecimal("10"), 1);
        // Output 2: 15 units (different outputSeq)
        scanOutput(order.getId(), TENANT_A, new BigDecimal("15"), 2);

        List<ProductionOrderOutputDO> records = outputMapper.listByOrder(TENANT_A, order.getId());
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getOutputSeq()).isEqualTo(1);
        assertThat(records.get(1).getOutputSeq()).isEqualTo(2);

        // Cumulative actual_output_qty = 25
        BigDecimal cumulative = records.stream()
                .map(ProductionOrderOutputDO::getActualOutputQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(cumulative).isEqualByComparingTo(new BigDecimal("25"));

        // 2 PRODUCTION_IN events
        assertThat(countEvents(TENANT_A, order.getId(), "PRODUCTION_IN")).isEqualTo(2);

        // Semi-finished stock balance = 25
        BigDecimal semiAfter = stockBalanceService.getAvailableQty(TENANT_A, semiFinishedStockItemId, activeLocationId);
        assertThat(semiAfter).isEqualByComparingTo(new BigDecimal("25.000000"));
    }

    @Test
    void testScanOutputTenantIsolation() {
        ProductionOrderDO order = createInProgressOrder();

        // Tenant B cannot scan-output Tenant A's order
        ScanOutputReqVO req = buildScanOutputReq(order.getId(), TENANT_B, new BigDecimal("10"), 1);

        assertThatThrownBy(() -> productionOutputService.scanOutput(req))
                .isInstanceOfSatisfying(StockBusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(StockErrorCodeConstants.PRODUCTION_ORDER_NOT_FOUND);
                });

        // Verify no output record
        assertThat(outputMapper.countByOrder(TENANT_A, order.getId())).isEqualTo(0);
    }

    @Test
    void testScanOutputDefaultProductIdSuccess() {
        ProductionOrderDO order = createInProgressOrder();

        // Do not set outputProductId — should default to order.productId
        ScanOutputReqVO req = new ScanOutputReqVO();
        req.setOrderId(order.getId());
        req.setTenantId(TENANT_A);
        req.setActualOutputQty(new BigDecimal("10"));
        req.setOutputSeq(1);
        req.setOperatorUserId(OPERATOR_ID);

        ProductionOrderOutputDO result = productionOutputService.scanOutput(req);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getOutputSkuId()).isEqualTo(semiFinishedProductId);
        assertThat(result.getActualOutputQty()).isEqualByComparingTo(new BigDecimal("10"));

        // Verify PRODUCTION_IN stock_event written
        assertThat(countEvents(TENANT_A, order.getId(), "PRODUCTION_IN")).isEqualTo(1);
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

    private ScanOutputReqVO buildScanOutputReq(Long orderId, Long tenantId,
                                                BigDecimal actualOutputQty, Integer outputSeq) {
        ScanOutputReqVO req = new ScanOutputReqVO();
        req.setOrderId(orderId);
        req.setTenantId(tenantId);
        req.setActualOutputQty(actualOutputQty);
        req.setOutputSeq(outputSeq);
        req.setOperatorUserId(OPERATOR_ID);
        return req;
    }

    private ProductionOrderOutputDO scanOutput(Long orderId, Long tenantId,
                                                BigDecimal actualOutputQty, Integer outputSeq) {
        return productionOutputService.scanOutput(buildScanOutputReq(orderId, tenantId, actualOutputQty, outputSeq));
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
