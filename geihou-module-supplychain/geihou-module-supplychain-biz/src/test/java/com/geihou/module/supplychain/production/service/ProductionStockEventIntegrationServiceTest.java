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
import com.geihou.module.supplychain.bom.service.BomRecipeService;
import com.geihou.module.supplychain.bom.service.ProductMasterService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Standalone tests for {@link ProductionStockEventIntegrationService}.
 *
 * <p>Tests the BOM explosion → aggregation → PRODUCTION_OUT/PRODUCTION_IN
 * integration logic independently from the ProductionOrderService state machine.
 *
 * <p>Key test: multi-layer BOM with duplicate component aggregation — verifies
 * that the same raw material appearing in multiple leaves is aggregated into
 * a single PRODUCTION_OUT event with summed quantity.
 *
 * <p>Source: TASK-G2-02L Section 5.1.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:prod_stock_event_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ProductionStockEventIntegrationServiceTest {

    @Autowired
    private ProductionStockEventIntegrationService integrationService;
    @Autowired
    private ProductMasterService productMasterService;
    @Autowired
    private BomRecipeService bomRecipeService;
    @Autowired
    private StockItemService stockItemService;
    @Autowired
    private StockEventService stockEventService;
    @Autowired
    private StockEventMapper stockEventMapper;
    @Autowired
    private StockBalanceService stockBalanceService;
    @Autowired
    private ProductMasterMapper productMasterMapper;
    @Autowired
    private BomRecipeMapper bomRecipeMapper;
    @Autowired
    private BomRecipeItemMapper bomRecipeItemMapper;
    @Autowired
    private StockLocationMapper stockLocationMapper;
    @Autowired
    private ProductionOrderMapper productionOrderMapper;
    @Autowired
    private DataSource dataSource;

    private static final Long TENANT = 1L;
    private static final Long OPERATOR_ID = 100L;
    private static final Long LOCATION_ID = 10L;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(TENANT);

        // Seed a stock location
        StockLocationDO loc = new StockLocationDO();
        loc.setTenantId(TENANT);
        loc.setLocationCode("LOC_TEST");
        loc.setLocationName("Test Location");
        loc.setLocationType("CENTRAL_KITCHEN");
        loc.setStoreId(1L);
        loc.setIsActive(true);
        loc.setCreator("test");
        loc.setCreateTime(LocalDateTime.now());
        loc.setUpdater("test");
        loc.setUpdateTime(LocalDateTime.now());
        loc.setDeleted(false);
        stockLocationMapper.insert(loc);
        // LOCATION_ID is hardcoded to 10L in tests; we need the actual ID
        // We'll use the actual inserted ID instead
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ================================================================
    // Multi-layer BOM with duplicate component aggregation
    // ================================================================

    @Test
    void testMultiLayerBomDuplicateComponentAggregation() {
        // Setup: SF-A (semi-finished) → SF-B (semi-finished, qty=1) + RM-1 (raw, qty=2)
        //         SF-B → RM-1 (raw, qty=3) + RM-2 (raw, qty=1)
        // Leaves: RM-1 (appears twice: once from SF-A direct, once from SF-B's children)
        //          RM-2 (appears once)
        // Aggregated: RM-1 = 1*2 + 1*3 = 5, RM-2 = 1*1 = 1
        // Expected: 2 PRODUCTION_OUT events (one per unique componentProductId) + 1 PRODUCTION_IN

        SetupMultiLayer setup = setupMultiLayerBomWithDuplicateComponent(TENANT);

        // Give stock
        giveStock(TENANT, setup.rm1StockItemId, setup.rm1SkuCode, setup.locationId, new BigDecimal("1000"));
        giveStock(TENANT, setup.rm2StockItemId, setup.rm2SkuCode, setup.locationId, new BigDecimal("1000"));

        // Create and complete order
        ProductionOrderDO order = createAndCompleteOrder(setup.semiProductId, setup.recipeId,
                setup.locationId, new BigDecimal("1"));

        // Verify PRODUCTION_OUT events: should be 2 (RM-1 aggregated, RM-2)
        List<StockEventDO> outEvents = getEvents(order.getId(), "PRODUCTION_OUT");
        assertThat(outEvents).hasSize(2);

        // Verify clientRequestId uniqueness — no conflicts
        Set<String> clientRequestIds = new HashSet<>();
        for (StockEventDO event : outEvents) {
            assertThat(clientRequestIds.add(event.getClientRequestId()))
                    .as("clientRequestId should be unique").isTrue();
        }

        // Find RM-1 event and verify aggregated quantity
        StockEventDO rm1Event = outEvents.stream()
                .filter(e -> e.getStockItemId().equals(setup.rm1StockItemId))
                .findFirst().orElseThrow(() -> new AssertionError("RM-1 event not found"));
        // RM-1 total: 1 * 2 (direct) + 1 * 1 * 3 (via SF-B) = 2 + 3 = 5
        assertThat(rm1Event.getQuantity()).isEqualByComparingTo(new BigDecimal("5.000000"));

        // Find RM-2 event
        StockEventDO rm2Event = outEvents.stream()
                .filter(e -> e.getStockItemId().equals(setup.rm2StockItemId))
                .findFirst().orElseThrow(() -> new AssertionError("RM-2 event not found"));
        // RM-2 total: 1 * 1 * 1 (via SF-B) = 1
        assertThat(rm2Event.getQuantity()).isEqualByComparingTo(new BigDecimal("1.000000"));

        // Verify PRODUCTION_IN event
        List<StockEventDO> inEvents = getEvents(order.getId(), "PRODUCTION_IN");
        assertThat(inEvents).hasSize(1);
        assertThat(inEvents.get(0).getQuantity()).isEqualByComparingTo(new BigDecimal("1"));

        // Total events = 2 (PRODUCTION_OUT) + 1 (PRODUCTION_IN) = 3
        List<StockEventDO> allEvents = getEvents(order.getId(), null);
        assertThat(allEvents).hasSize(3);
    }

    // ================================================================
    // BOM explosion empty
    // ================================================================

    @Test
    void testBomExplosionEmptyThrows() {
        // Create a semi-finished product with a recipe that has no items
        ProductMasterDO semi = createProduct(TENANT, "SEMI_FINISHED", "KG");
        Long recipeId = createEmptyRecipe(TENANT, semi.getId());
        StockItemDO semiItem = createStockItem(TENANT, semi.getSkuCode(), false, true, false);
        Long locId = createLocation(TENANT);

        ProductionOrderDO order = buildOrder(TENANT, semi.getId(), recipeId, locId, new BigDecimal("10"));

        assertThatThrownBy(() -> integrationService.integrateStockEvents(order))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("BOM explosion produced no raw-material leaves");
    }

    // ================================================================
    // Stock item missing
    // ================================================================

    @Test
    void testStockItemMissingThrows() {
        // Create semi-finished with a recipe pointing to a raw material with no stock_item
        ProductMasterDO semi = createProduct(TENANT, "SEMI_FINISHED", "KG");
        ProductMasterDO raw = createProduct(TENANT, "RAW_MATERIAL", "KG");
        // Create stock item for semi but NOT for raw
        StockItemDO semiItem = createStockItem(TENANT, semi.getSkuCode(), false, true, false);

        Long recipeId = createRecipeWithItems(TENANT, semi.getId(),
                List.of(recipeItem(raw.getId(), new BigDecimal("2"), "RAW_MATERIAL", "KG")));
        Long locId = createLocation(TENANT);

        ProductionOrderDO order = buildOrder(TENANT, semi.getId(), recipeId, locId, new BigDecimal("10"));

        assertThatThrownBy(() -> integrationService.integrateStockEvents(order))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Stock item not found");
    }

    // ================================================================
    // Insufficient stock propagates original error code
    // ================================================================

    @Test
    void testInsufficientStockPropagatesOriginalErrorCode() {
        ProductMasterDO semi = createProduct(TENANT, "SEMI_FINISHED", "KG");
        ProductMasterDO raw = createProduct(TENANT, "RAW_MATERIAL", "KG");

        StockItemDO semiItem = createStockItem(TENANT, semi.getSkuCode(), false, true, false);
        StockItemDO rawItem = createStockItem(TENANT, raw.getSkuCode(), true, false, false);

        Long recipeId = createRecipeWithItems(TENANT, semi.getId(),
                List.of(recipeItem(raw.getId(), new BigDecimal("2"), "RAW_MATERIAL", "KG")));
        Long locId = createLocation(TENANT);

        // Give only 5 stock, need 20 (10 * 2)
        giveStock(TENANT, rawItem.getId(), raw.getSkuCode(), locId, new BigDecimal("5"));

        ProductionOrderDO order = buildOrder(TENANT, semi.getId(), recipeId, locId, new BigDecimal("10"));

        // Should throw with INSUFFICIENT_STOCK (2002004), NOT 2002041
        assertThatThrownBy(() -> integrationService.integrateStockEvents(order))
                .isInstanceOf(StockBusinessException.class)
                .satisfies(ex -> {
                    StockBusinessException sbe = (StockBusinessException) ex;
                    assertThat(sbe.getCode()).isEqualTo(2002004); // INSUFFICIENT_STOCK, not 2002041
                });
    }

    // ================================================================
    // Normal flow with multiple components
    // ================================================================

    @Test
    void testNormalFlowMultipleComponents() {
        ProductMasterDO semi = createProduct(TENANT, "SEMI_FINISHED", "KG");
        ProductMasterDO raw1 = createProduct(TENANT, "RAW_MATERIAL", "KG");
        ProductMasterDO raw2 = createProduct(TENANT, "RAW_MATERIAL", "KG");
        ProductMasterDO raw3 = createProduct(TENANT, "RAW_MATERIAL", "KG");

        StockItemDO semiItem = createStockItem(TENANT, semi.getSkuCode(), false, true, false);
        StockItemDO raw1Item = createStockItem(TENANT, raw1.getSkuCode(), true, false, false);
        StockItemDO raw2Item = createStockItem(TENANT, raw2.getSkuCode(), true, false, false);
        StockItemDO raw3Item = createStockItem(TENANT, raw3.getSkuCode(), true, false, false);

        Long recipeId = createRecipeWithItems(TENANT, semi.getId(), List.of(
                recipeItem(raw1.getId(), new BigDecimal("2"), "RAW_MATERIAL", "KG"),
                recipeItem(raw2.getId(), new BigDecimal("3"), "RAW_MATERIAL", "KG"),
                recipeItem(raw3.getId(), new BigDecimal("1"), "RAW_MATERIAL", "KG")
        ));
        Long locId = createLocation(TENANT);

        giveStock(TENANT, raw1Item.getId(), raw1.getSkuCode(), locId, new BigDecimal("1000"));
        giveStock(TENANT, raw2Item.getId(), raw2.getSkuCode(), locId, new BigDecimal("1000"));
        giveStock(TENANT, raw3Item.getId(), raw3.getSkuCode(), locId, new BigDecimal("1000"));

        ProductionOrderDO order = buildOrder(TENANT, semi.getId(), recipeId, locId, new BigDecimal("10"));

        integrationService.integrateStockEvents(order);

        // 3 PRODUCTION_OUT + 1 PRODUCTION_IN = 4 total
        List<StockEventDO> allEvents = getEvents(order.getId(), null);
        assertThat(allEvents).hasSize(4);

        List<StockEventDO> outEvents = getEvents(order.getId(), "PRODUCTION_OUT");
        assertThat(outEvents).hasSize(3);

        List<StockEventDO> inEvents = getEvents(order.getId(), "PRODUCTION_IN");
        assertThat(inEvents).hasSize(1);
        assertThat(inEvents.get(0).getQuantity()).isEqualByComparingTo(new BigDecimal("10"));
    }

    // ================================================================
    // Helpers
    // ================================================================

    private ProductionOrderDO createAndCompleteOrder(Long productId, Long recipeId,
                                                      Long locationId, BigDecimal actualQty) {
        ProductionOrderDO order = buildOrder(TENANT, productId, recipeId, locationId, actualQty);
        integrationService.integrateStockEvents(order);
        return order;
    }

    private ProductionOrderDO buildOrder(Long tenantId, Long productId, Long recipeId,
                                          Long locationId, BigDecimal actualQty) {
        ProductionOrderDO order = new ProductionOrderDO();
        order.setTenantId(tenantId);
        order.setOrderNo("PO-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        order.setProductId(productId);
        order.setRecipeId(recipeId);
        order.setLocationId(locationId);
        order.setPlannedQty(new BigDecimal("100"));
        order.setActualQty(actualQty);
        order.setProductionStage("IN_PROGRESS");
        order.setOperatorUserId(OPERATOR_ID);
        order.setCreator("test");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdater("test");
        order.setUpdateTime(LocalDateTime.now());
        order.setDeleted(false);
        productionOrderMapper.insert(order);
        return order;
    }

    private ProductMasterDO createProduct(Long tenantId, String type, String unit) {
        ProductMasterDO p = new ProductMasterDO();
        p.setTenantId(tenantId);
        p.setProductCode("P-" + UUID.randomUUID().toString().substring(0, 8));
        p.setProductName(type + "-" + UUID.randomUUID().toString().substring(0, 8));
        p.setProductType(type);
        p.setSkuCode("SKU-" + UUID.randomUUID().toString().substring(0, 8));
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
        return item;
    }

    private Long createLocation(Long tenantId) {
        StockLocationDO loc = new StockLocationDO();
        loc.setTenantId(tenantId);
        loc.setLocationCode("LOC-" + UUID.randomUUID().toString().substring(0, 8));
        loc.setLocationName("Test Location");
        loc.setLocationType("CENTRAL_KITCHEN");
        loc.setStoreId(System.identityHashCode(loc) % 1000 + 1L);
        loc.setIsActive(true);
        loc.setCreator("test");
        loc.setCreateTime(LocalDateTime.now());
        loc.setUpdater("test");
        loc.setUpdateTime(LocalDateTime.now());
        loc.setDeleted(false);
        stockLocationMapper.insert(loc);
        return loc.getId();
    }

    private Long createEmptyRecipe(Long tenantId, Long productId) {
        BomRecipeDO recipe = new BomRecipeDO();
        recipe.setTenantId(tenantId);
        recipe.setProductId(productId);
        recipe.setStatus("DRAFT");
        recipe.setOutputQuantity(BigDecimal.ONE);
        recipe.setOutputUnit("KG");
        recipe.setCreator("test");
        recipe.setCreateTime(LocalDateTime.now());
        recipe.setUpdater("test");
        recipe.setUpdateTime(LocalDateTime.now());
        recipe.setDeleted(false);
        Long recipeId = bomRecipeService.createDraftRecipe(recipe);
        bomRecipeService.activateRecipe(tenantId, recipeId);
        // Get the activated recipe (may be a new ID after activation)
        BomRecipeDO active = bomRecipeService.getActiveRecipe(productId, tenantId);
        return active.getId();
    }

    private Long createRecipeWithItems(Long tenantId, Long productId,
                                        List<BomRecipeItemDO> items) {
        BomRecipeDO recipe = new BomRecipeDO();
        recipe.setTenantId(tenantId);
        recipe.setProductId(productId);
        recipe.setStatus("DRAFT");
        recipe.setOutputQuantity(BigDecimal.ONE);
        recipe.setOutputUnit("KG");
        recipe.setCreator("test");
        recipe.setCreateTime(LocalDateTime.now());
        recipe.setUpdater("test");
        recipe.setUpdateTime(LocalDateTime.now());
        recipe.setDeleted(false);
        Long recipeId = bomRecipeService.createDraftRecipe(recipe);
        bomRecipeService.updateDraftItems(tenantId, recipeId, items);
        bomRecipeService.activateRecipe(tenantId, recipeId);
        BomRecipeDO active = bomRecipeService.getActiveRecipe(productId, tenantId);
        return active.getId();
    }

    private BomRecipeItemDO recipeItem(Long componentProductId, BigDecimal quantity,
                                        String componentType, String unit) {
        BomRecipeItemDO item = new BomRecipeItemDO();
        item.setComponentProductId(componentProductId);
        item.setQuantity(quantity);
        item.setWasteRate(BigDecimal.ZERO);
        item.setComponentType(componentType);
        item.setUnit(unit);
        return item;
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

    private List<StockEventDO> getEvents(Long sourceRecordId, String eventType) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockEventDO> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockEventDO>()
                        .eq(StockEventDO::getTenantId, TENANT)
                        .eq(StockEventDO::getSourceModule, "PRODUCTION")
                        .eq(StockEventDO::getSourceRecordId, sourceRecordId);
        if (eventType != null) {
            wrapper.eq(StockEventDO::getEventType, eventType);
        }
        return stockEventMapper.selectList(wrapper);
    }

    // --- Setup result for multi-layer BOM ---

    /**
     * Setup multi-layer BOM with duplicate component:
     *   SF-A (semi-finished) → SF-B (semi-finished, qty=1) + RM-1 (raw, qty=2)
     *   SF-B → RM-1 (raw, qty=3) + RM-2 (raw, qty=1)
     *   Leaves: RM-1 (twice), RM-2 (once)
     *   Aggregated: RM-1 = 1*2 + 1*1*3 = 5, RM-2 = 1*1*1 = 1
     */
    private SetupMultiLayer setupMultiLayerBomWithDuplicateComponent(Long tenantId) {
        TenantContextHolder.setTenantId(tenantId);

        ProductMasterDO semiA = createProduct(tenantId, "SEMI_FINISHED", "KG");
        ProductMasterDO semiB = createProduct(tenantId, "SEMI_FINISHED", "KG");
        ProductMasterDO rm1 = createProduct(tenantId, "RAW_MATERIAL", "KG");
        ProductMasterDO rm2 = createProduct(tenantId, "RAW_MATERIAL", "KG");

        StockItemDO semiAItem = createStockItem(tenantId, semiA.getSkuCode(), false, true, false);
        StockItemDO rm1Item = createStockItem(tenantId, rm1.getSkuCode(), true, false, false);
        StockItemDO rm2Item = createStockItem(tenantId, rm2.getSkuCode(), true, false, false);

        Long locId = createLocation(tenantId);

        // SF-B recipe: 1 SF-B = 3 RM-1 + 1 RM-2
        Long sfBRecipeId = createRecipeWithItems(tenantId, semiB.getId(), List.of(
                recipeItem(rm1.getId(), new BigDecimal("3"), "RAW_MATERIAL", "KG"),
                recipeItem(rm2.getId(), new BigDecimal("1"), "RAW_MATERIAL", "KG")
        ));

        // SF-A recipe: 1 SF-A = 1 SF-B + 2 RM-1
        Long sfARecipeId = createRecipeWithItems(tenantId, semiA.getId(), List.of(
                recipeItem(semiB.getId(), new BigDecimal("1"), "SEMI_FINISHED", "KG"),
                recipeItem(rm1.getId(), new BigDecimal("2"), "RAW_MATERIAL", "KG")
        ));

        SetupMultiLayer result = new SetupMultiLayer();
        result.semiProductId = semiA.getId();
        result.semiSkuCode = semiA.getSkuCode();
        result.semiStockItemId = semiAItem.getId();
        result.rm1ProductId = rm1.getId();
        result.rm1SkuCode = rm1.getSkuCode();
        result.rm1StockItemId = rm1Item.getId();
        result.rm2ProductId = rm2.getId();
        result.rm2SkuCode = rm2.getSkuCode();
        result.rm2StockItemId = rm2Item.getId();
        result.recipeId = sfARecipeId;
        result.locationId = locId;
        return result;
    }

    private static class SetupMultiLayer {
        Long semiProductId;
        String semiSkuCode;
        Long semiStockItemId;
        Long rm1ProductId;
        String rm1SkuCode;
        Long rm1StockItemId;
        Long rm2ProductId;
        String rm2SkuCode;
        Long rm2StockItemId;
        Long recipeId;
        Long locationId;
    }
}
