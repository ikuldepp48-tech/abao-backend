package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseReqDTO;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrent idempotency test: 10 threads call salesOutWithBomReverse
 * with the same clientRequestId simultaneously.
 *
 * <p>Covers verification points V8–V9:
 * <ul>
 *   <li>V8: all 10 calls succeed (idempotent return, no exceptions)</li>
 *   <li>V9: stock_event count = raw material count (2), no duplicates</li>
 * </ul>
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:bom_concurrent_idempotent;DB_CLOSE_DELAY=-1;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ConcurrentBomReverseIdempotentTest {

    @Autowired
    private StockBomReverseService stockBomReverseService;
    @Autowired
    private StockEventService stockEventService;
    @Autowired
    private StockEventMapper stockEventMapper;
    @Autowired
    private StockBalanceService stockBalanceService;
    @Autowired
    private StockItemService stockItemService;
    @Autowired
    private ProductMasterService productMasterService;
    @Autowired
    private BomRecipeService bomRecipeService;
    @Autowired
    private DataSource dataSource;

    private static final Long TENANT_1 = 1L;
    private static final Long LOCATION_ID = 10L;
    private static final Long OPERATOR_ID = 999L;
    private static final int THREAD_COUNT = 10;

    private Long finishedProductId;
    private Long rm1StockItemId;
    private Long rm2StockItemId;
    private String rm1SkuCode;
    private String rm2SkuCode;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(TENANT_1);

        SetupResult setup = setupSingleLayerBom(TENANT_1);
        finishedProductId = setup.finishedProductId;
        rm1StockItemId = setup.rm1StockItemId;
        rm2StockItemId = setup.rm2StockItemId;
        rm1SkuCode = setup.rm1SkuCode;
        rm2SkuCode = setup.rm2SkuCode;

        // Give plenty of stock
        giveStock(TENANT_1, rm1StockItemId, rm1SkuCode, new BigDecimal("1000"));
        giveStock(TENANT_1, rm2StockItemId, rm2SkuCode, new BigDecimal("1000"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void concurrentIdempotentSameClientRequestId() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // All threads use the SAME clientRequestId
        String sharedClientRequestId = "idempotent-concurrent-" + UUID.randomUUID();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContextHolder.setTenantId(TENANT_1);

                    SalesOutBomReverseReqDTO req = new SalesOutBomReverseReqDTO();
                    req.setTenantId(TENANT_1);
                    req.setProductId(finishedProductId);
                    req.setQuantity(BigDecimal.ONE);
                    req.setLocationId(LOCATION_ID);
                    req.setSourceModule("SALES");
                    req.setSourceRecordId(6001L);
                    req.setReferenceNo("IDEMPOTENT-CONCURRENT");
                    req.setOperatorUserId(OPERATOR_ID);
                    req.setClientRequestId(sharedClientRequestId);

                    stockBomReverseService.salesOutWithBomReverse(req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    TenantContextHolder.clear();
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // V8: all 10 concurrent calls with the same clientRequestId must succeed
        // (idempotent return, no exceptions). The read-then-write race in
        // recordEvent is fixed via JDBC savepoint (G2-02I-1A): when a
        // DuplicateKeyException occurs on event insert, the balance changes
        // are rolled back to the savepoint and the existing event id is returned.
        assertThat(successCount.get())
                .as("all 10 concurrent calls should succeed (idempotent)")
                .isEqualTo(THREAD_COUNT);
        assertThat(failureCount.get())
                .as("no failures expected for idempotent concurrent calls")
                .isEqualTo(0);

        // V9: stock_event count = 2 (one per raw material, no duplicates).
        // All concurrent calls return idempotently, and no duplicate events are created.
        List<StockEventDO> rm1Events = stockEventMapper.selectByTenantItemLocation(TENANT_1, rm1StockItemId, LOCATION_ID);
        List<StockEventDO> rm2Events = stockEventMapper.selectByTenantItemLocation(TENANT_1, rm2StockItemId, LOCATION_ID);

        long rm1ConsumeCount = rm1Events.stream()
                .filter(e -> StockEventTypeEnum.CONSUME_OUT.getCode().equals(e.getEventType()))
                .count();
        long rm2ConsumeCount = rm2Events.stream()
                .filter(e -> StockEventTypeEnum.CONSUME_OUT.getCode().equals(e.getEventType()))
                .count();

        assertThat(rm1ConsumeCount)
                .as("RM-1 should have exactly 1 CONSUME_OUT event (idempotent dedup)")
                .isEqualTo(1L);
        assertThat(rm2ConsumeCount)
                .as("RM-2 should have exactly 1 CONSUME_OUT event (idempotent dedup)")
                .isEqualTo(1L);

        // Verify balance: only 1 deduction of quantity 1 per RM (idempotent — no double deduction)
        BigDecimal rm1Balance = stockBalanceService.getAvailableQty(TENANT_1, rm1StockItemId, LOCATION_ID);
        BigDecimal rm2Balance = stockBalanceService.getAvailableQty(TENANT_1, rm2StockItemId, LOCATION_ID);
        assertThat(rm1Balance)
                .as("RM-1 balance should be 999 (1000 - 1, single idempotent deduction)")
                .isEqualByComparingTo(new BigDecimal("999"));
        assertThat(rm2Balance)
                .as("RM-2 balance should be 999 (1000 - 1, single idempotent deduction)")
                .isEqualByComparingTo(new BigDecimal("999"));
    }

    // ================================================================
    // Helper methods
    // ================================================================

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

    private SetupResult setupSingleLayerBom(Long tenantId) {
        TenantContextHolder.setTenantId(tenantId);

        ProductMasterDO fp = createProduct(tenantId, "FINISHED", "个");
        ProductMasterDO rm1 = createProduct(tenantId, "RAW_MATERIAL", "个");
        ProductMasterDO rm2 = createProduct(tenantId, "RAW_MATERIAL", "个");

        StockItemDO rm1Item = createStockItem(tenantId, rm1.getSkuCode(), true);
        StockItemDO rm2Item = createStockItem(tenantId, rm2.getSkuCode(), true);

        BomRecipeDO recipe = new BomRecipeDO();
        recipe.setTenantId(tenantId);
        recipe.setProductId(fp.getId());
        recipe.setStatus("DRAFT");
        recipe.setOutputQuantity(BigDecimal.ONE);
        recipe.setOutputUnit("个");
        recipe.setCreator("test");
        recipe.setCreateTime(LocalDateTime.now());
        recipe.setUpdater("test");
        recipe.setUpdateTime(LocalDateTime.now());
        recipe.setDeleted(false);
        Long recipeId = bomRecipeService.createDraftRecipe(recipe);

        BomRecipeItemDO item1 = new BomRecipeItemDO();
        item1.setTenantId(tenantId);
        item1.setRecipeId(recipeId);
        item1.setComponentProductId(rm1.getId());
        item1.setQuantity(BigDecimal.ONE);
        item1.setWasteRate(BigDecimal.ZERO);
        item1.setComponentType("RAW_MATERIAL");
        item1.setUnit("个");

        BomRecipeItemDO item2 = new BomRecipeItemDO();
        item2.setTenantId(tenantId);
        item2.setRecipeId(recipeId);
        item2.setComponentProductId(rm2.getId());
        item2.setQuantity(BigDecimal.ONE);
        item2.setWasteRate(BigDecimal.ZERO);
        item2.setComponentType("RAW_MATERIAL");
        item2.setUnit("个");

        bomRecipeService.updateDraftItems(tenantId, recipeId, List.of(item1, item2));
        bomRecipeService.activateRecipe(tenantId, recipeId);

        SetupResult result = new SetupResult();
        result.finishedProductId = fp.getId();
        result.rm1StockItemId = rm1Item.getId();
        result.rm1SkuCode = rm1.getSkuCode();
        result.rm2StockItemId = rm2Item.getId();
        result.rm2SkuCode = rm2.getSkuCode();
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

    private StockItemDO createStockItem(Long tenantId, String skuCode, boolean isRaw) {
        StockItemDO item = new StockItemDO();
        item.setTenantId(tenantId);
        item.setSkuCode(skuCode);
        item.setItemName("Item-" + skuCode);
        item.setUnit("个");
        item.setIsRawMaterial(isRaw);
        item.setIsActive(true);
        item.setCreator("test");
        item.setCreateTime(LocalDateTime.now());
        item.setUpdater("test");
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleted(false);
        stockItemService.createItem(item);
        return item;
    }

    private static class SetupResult {
        Long finishedProductId;
        Long rm1StockItemId;
        String rm1SkuCode;
        Long rm2StockItemId;
        String rm2SkuCode;
    }
}
