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
 * Concurrent tenant isolation test: tenant A (50 threads) + tenant B (50 threads)
 * call salesOutWithBomReverse simultaneously.
 *
 * <p>Covers verification points V10–V11:
 * <ul>
 *   <li>V10: tenant A stock_events contain no tenant B events</li>
 *   <li>V11: tenant A balance changes are independent of tenant B</li>
 * </ul>
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:bom_concurrent_tenant;DB_CLOSE_DELAY=-1;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ConcurrentBomReverseTenantIsolationTest {

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

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long LOCATION_ID = 10L;
    private static final Long OPERATOR_ID = 999L;
    private static final int THREADS_PER_TENANT = 50;
    private static final BigDecimal INITIAL_STOCK = new BigDecimal("50");

    private TenantSetup setupA;
    private TenantSetup setupB;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();

        // Setup tenant A
        TenantContextHolder.setTenantId(TENANT_A);
        setupA = setupTenant(TENANT_A);

        // Setup tenant B
        TenantContextHolder.setTenantId(TENANT_B);
        setupB = setupTenant(TENANT_B);

        TenantContextHolder.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void concurrentTenantIsolation() throws InterruptedException {
        int totalThreads = THREADS_PER_TENANT * 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalThreads);
        AtomicInteger successA = new AtomicInteger(0);
        AtomicInteger failureA = new AtomicInteger(0);
        AtomicInteger successB = new AtomicInteger(0);
        AtomicInteger failureB = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);

        // Tenant A threads
        for (int i = 0; i < THREADS_PER_TENANT; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContextHolder.setTenantId(TENANT_A);

                    SalesOutBomReverseReqDTO req = new SalesOutBomReverseReqDTO();
                    req.setTenantId(TENANT_A);
                    req.setProductId(setupA.finishedProductId);
                    req.setQuantity(BigDecimal.ONE);
                    req.setLocationId(LOCATION_ID);
                    req.setSourceModule("SALES");
                    req.setSourceRecordId(7000L);
                    req.setReferenceNo("TENANT-A-SO");
                    req.setOperatorUserId(OPERATOR_ID);
                    req.setClientRequestId("tenant-a-" + index + "-" + UUID.randomUUID());

                    stockBomReverseService.salesOutWithBomReverse(req);
                    successA.incrementAndGet();
                } catch (Exception e) {
                    failureA.incrementAndGet();
                } finally {
                    TenantContextHolder.clear();
                    endLatch.countDown();
                }
            });
        }

        // Tenant B threads
        for (int i = 0; i < THREADS_PER_TENANT; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContextHolder.setTenantId(TENANT_B);

                    SalesOutBomReverseReqDTO req = new SalesOutBomReverseReqDTO();
                    req.setTenantId(TENANT_B);
                    req.setProductId(setupB.finishedProductId);
                    req.setQuantity(BigDecimal.ONE);
                    req.setLocationId(LOCATION_ID);
                    req.setSourceModule("SALES");
                    req.setSourceRecordId(7000L);
                    req.setReferenceNo("TENANT-B-SO");
                    req.setOperatorUserId(OPERATOR_ID);
                    req.setClientRequestId("tenant-b-" + index + "-" + UUID.randomUUID());

                    stockBomReverseService.salesOutWithBomReverse(req);
                    successB.incrementAndGet();
                } catch (Exception e) {
                    failureB.incrementAndGet();
                } finally {
                    TenantContextHolder.clear();
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // All threads should succeed (each tenant has 50 stock, 50 threads)
        assertThat(successA.get()).isEqualTo(THREADS_PER_TENANT);
        assertThat(successB.get()).isEqualTo(THREADS_PER_TENANT);
        assertThat(failureA.get()).isEqualTo(0);
        assertThat(failureB.get()).isEqualTo(0);

        // V10: tenant A events contain no tenant B events
        TenantContextHolder.setTenantId(TENANT_A);
        List<StockEventDO> tenantAEvents = stockEventMapper.selectByTenantItemLocation(
                TENANT_A, setupA.rm1StockItemId, LOCATION_ID);
        for (StockEventDO event : tenantAEvents) {
            assertThat(event.getTenantId())
                    .as("tenant A events should not contain tenant B events")
                    .isEqualTo(TENANT_A);
        }

        TenantContextHolder.setTenantId(TENANT_B);
        List<StockEventDO> tenantBEvents = stockEventMapper.selectByTenantItemLocation(
                TENANT_B, setupB.rm1StockItemId, LOCATION_ID);
        for (StockEventDO event : tenantBEvents) {
            assertThat(event.getTenantId())
                    .as("tenant B events should not contain tenant A events")
                    .isEqualTo(TENANT_B);
        }

        // V11: balances are independent
        // Tenant A: started with 50, consumed 50 (50 threads * 1 per unit), balance = 0
        TenantContextHolder.setTenantId(TENANT_A);
        BigDecimal tABalance = stockBalanceService.getAvailableQty(TENANT_A, setupA.rm1StockItemId, LOCATION_ID);
        assertThat(tABalance).isEqualByComparingTo(BigDecimal.ZERO);

        // Tenant B: started with 50, consumed 50, balance = 0
        TenantContextHolder.setTenantId(TENANT_B);
        BigDecimal tBBalance = stockBalanceService.getAvailableQty(TENANT_B, setupB.rm1StockItemId, LOCATION_ID);
        assertThat(tBBalance).isEqualByComparingTo(BigDecimal.ZERO);

        // Verify event counts are independent
        TenantContextHolder.setTenantId(TENANT_A);
        long tAConsumeCount = stockEventMapper.selectByTenantItemLocation(
                TENANT_A, setupA.rm1StockItemId, LOCATION_ID).stream()
                .filter(e -> StockEventTypeEnum.CONSUME_OUT.getCode().equals(e.getEventType()))
                .count();
        assertThat(tAConsumeCount).isEqualTo(50L);

        TenantContextHolder.setTenantId(TENANT_B);
        long tBConsumeCount = stockEventMapper.selectByTenantItemLocation(
                TENANT_B, setupB.rm1StockItemId, LOCATION_ID).stream()
                .filter(e -> StockEventTypeEnum.CONSUME_OUT.getCode().equals(e.getEventType()))
                .count();
        assertThat(tBConsumeCount).isEqualTo(50L);
    }

    // ================================================================
    // Helper methods
    // ================================================================

    private TenantSetup setupTenant(Long tenantId) {
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

        // Give initial stock
        giveStock(tenantId, rm1Item.getId(), rm1.getSkuCode(), INITIAL_STOCK);
        giveStock(tenantId, rm2Item.getId(), rm2.getSkuCode(), INITIAL_STOCK);

        TenantSetup setup = new TenantSetup();
        setup.finishedProductId = fp.getId();
        setup.rm1StockItemId = rm1Item.getId();
        setup.rm1SkuCode = rm1.getSkuCode();
        setup.rm2StockItemId = rm2Item.getId();
        setup.rm2SkuCode = rm2.getSkuCode();
        return setup;
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
        req.setClientRequestId("stock-in-" + tenantId + "-" + UUID.randomUUID());
        stockEventService.recordEvent(req);
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

    private static class TenantSetup {
        Long finishedProductId;
        Long rm1StockItemId;
        String rm1SkuCode;
        Long rm2StockItemId;
        String rm2SkuCode;
    }
}
