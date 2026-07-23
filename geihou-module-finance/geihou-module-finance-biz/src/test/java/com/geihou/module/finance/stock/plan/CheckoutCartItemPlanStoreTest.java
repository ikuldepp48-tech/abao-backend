package com.geihou.module.finance.stock.plan;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.checkout.CheckoutTestSchemaInitializer;
import com.geihou.module.finance.product.enums.StockStrategyEnum;
import com.geihou.module.finance.stock.plan.dal.dataobject.CheckoutCartItemPlanDO;
import com.geihou.module.finance.stock.plan.dal.mapper.CheckoutCartItemPlanMapper;
import com.geihou.module.finance.stock.plan.enums.CheckoutClassificationReason;
import com.geihou.module.finance.stock.plan.enums.CheckoutStockClassification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link CheckoutCartItemPlanStoreImpl} covering the
 * slice 2B foundation (17 tests) plus slice 2C-2B six-field matrix
 * validation, normalization, persistence, and replay-mismatch tests.
 *
 * <p>Slice 2C-2B coverage:
 * <ul>
 *   <li>Valid matrix: BOM, NON_BOM+UNLIMITED, NON_BOM+TRACK_STOCK,
 *       UNMAPPED+{INVALID_SKU_CODE, INVALID_STOCK_STRATEGY, NO_PRODUCT,
 *       NO_STOCK_ITEM} all create rows and persist the six fields.</li>
 *   <li>Invalid matrix: each classification rule violation throws
 *       {@link IllegalArgumentException} from the compact constructor.</li>
 *   <li>Normalization: blank {@code skuCode} becomes null; invalid
 *       {@code stockStrategy} becomes null (independent of reason).</li>
 *   <li>Replay mismatch: any of the six fields differing on
 *       {@code createOrGet} replay throws {@link IllegalStateException}.</li>
 * </ul>
 *
 * <p>Concurrency tests use {@link CountDownLatch} barriers - no
 * {@code Thread.sleep} timing guesses.
 */
@SpringBootTest(
        classes = CheckoutCartItemPlanTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:ccip_store_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CheckoutCartItemPlanStoreTest {

    @Autowired private CheckoutCartItemPlanStore store;
    @Autowired private CheckoutCartItemPlanMapper mapper;
    @Autowired private DataSource dataSource;

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;
    private static final long SESSION_1 = 1001L;
    private static final long SESSION_2 = 1002L;

    @BeforeEach
    void setUp() throws Exception {
        CheckoutTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ==================== Helpers (slice 2C-2B six-field matrix) ====================

    /** BOM: skuCode non-null, TRACK_STOCK, bomProductId/locationId positive. */
    private CheckoutCartItemPlanCreate buildBomCreate(long tenantId, long sessionId,
                                                      long cartItemId, long skuId) {
        return new CheckoutCartItemPlanCreate(tenantId, sessionId, cartItemId, skuId,
                CheckoutStockClassification.BOM,
                "SKU-" + skuId,
                StockStrategyEnum.TRACK_STOCK.getCode(),
                5000L + skuId,
                null,
                6000L + skuId,
                null);
    }

    /** NON_BOM+UNLIMITED: skuCode non-null, three reference IDs + reason null. */
    private CheckoutCartItemPlanCreate buildNonBomUnlimitedCreate(long tenantId, long sessionId,
                                                                  long cartItemId, long skuId) {
        return new CheckoutCartItemPlanCreate(tenantId, sessionId, cartItemId, skuId,
                CheckoutStockClassification.NON_BOM,
                "SKU-" + skuId,
                StockStrategyEnum.UNLIMITED.getCode(),
                null, null, null, null);
    }

    /** NON_BOM+TRACK_STOCK: skuCode non-null, stockItemId/locationId positive. */
    private CheckoutCartItemPlanCreate buildNonBomTrackStockCreate(long tenantId, long sessionId,
                                                                   long cartItemId, long skuId) {
        return new CheckoutCartItemPlanCreate(tenantId, sessionId, cartItemId, skuId,
                CheckoutStockClassification.NON_BOM,
                "SKU-" + skuId,
                StockStrategyEnum.TRACK_STOCK.getCode(),
                null,
                7000L + skuId,
                8000L + skuId,
                null);
    }

    /** UNMAPPED+INVALID_SKU_CODE: skuCode null, strategy null (dual-invalid allowed). */
    private CheckoutCartItemPlanCreate buildUnmappedInvalidSkuCreate(long tenantId, long sessionId,
                                                                     long cartItemId, long skuId) {
        return new CheckoutCartItemPlanCreate(tenantId, sessionId, cartItemId, skuId,
                CheckoutStockClassification.UNMAPPED,
                null, null, null, null, null,
                CheckoutClassificationReason.INVALID_SKU_CODE);
    }

    /** UNMAPPED+INVALID_STOCK_STRATEGY: skuCode non-null, strategy null. */
    private CheckoutCartItemPlanCreate buildUnmappedInvalidStrategyCreate(long tenantId, long sessionId,
                                                                          long cartItemId, long skuId) {
        return new CheckoutCartItemPlanCreate(tenantId, sessionId, cartItemId, skuId,
                CheckoutStockClassification.UNMAPPED,
                "SKU-" + skuId,
                null,
                null, null, null,
                CheckoutClassificationReason.INVALID_STOCK_STRATEGY);
    }

    /** UNMAPPED+NO_PRODUCT: skuCode non-null, strategy=TRACK_STOCK (representative for the 4 TRACK_STOCK reasons). */
    private CheckoutCartItemPlanCreate buildUnmappedTrackStockReasonCreate(
            long tenantId, long sessionId, long cartItemId, long skuId,
            CheckoutClassificationReason reason) {
        return new CheckoutCartItemPlanCreate(tenantId, sessionId, cartItemId, skuId,
                CheckoutStockClassification.UNMAPPED,
                "SKU-" + skuId,
                StockStrategyEnum.TRACK_STOCK.getCode(),
                null, null, null,
                reason);
    }

    private Runnable withTenant(long tenantId, Runnable action) {
        return () -> {
            TenantContextHolder.clear();
            TenantContextHolder.setTenantId(tenantId);
            try {
                action.run();
            } finally {
                TenantContextHolder.clear();
            }
        };
    }

    // ==================== createOrGet (slice 2B foundation) ====================

    @Test
    void createOrGet_insertsNewPlanAndReturnsIt() {
        CheckoutCartItemPlanCreate cmd = buildBomCreate(TENANT_A, SESSION_1, 10L, 700L);

        CheckoutCartItemPlanDO result = store.createOrGet(cmd);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getTenantId()).isEqualTo(TENANT_A);
        assertThat(result.getCheckoutSessionId()).isEqualTo(SESSION_1);
        assertThat(result.getCartItemId()).isEqualTo(10L);
        assertThat(result.getSkuId()).isEqualTo(700L);
        assertThat(result.getClassification()).isEqualTo("BOM");
        assertThat(result.getCreateTime()).isNotNull();
        assertThat(result.getUpdateTime()).isNotNull();

        // Persisted row matches
        CheckoutCartItemPlanDO found = mapper.selectBySessionAndCartItem(
                TENANT_A, SESSION_1, 10L);
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(result.getId());
        assertThat(found.getClassification()).isEqualTo("BOM");
    }

    @Test
    void createOrGet_returnsExistingPlanWhenSameIdentityAndPayload() {
        CheckoutCartItemPlanCreate cmd = buildNonBomUnlimitedCreate(TENANT_A, SESSION_1, 20L, 800L);

        CheckoutCartItemPlanDO first = store.createOrGet(cmd);
        CheckoutCartItemPlanDO second = store.createOrGet(cmd);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getCheckoutSessionId()).isEqualTo(first.getCheckoutSessionId());
        assertThat(second.getCartItemId()).isEqualTo(first.getCartItemId());
        assertThat(second.getSkuId()).isEqualTo(first.getSkuId());
        assertThat(second.getClassification()).isEqualTo(first.getClassification());

        // Only one row in the table
        List<CheckoutCartItemPlanDO> rows = store.listBySession(TENANT_A, SESSION_1);
        assertThat(rows).hasSize(1);
    }

    @Test
    void createOrGet_throwsWhenSkuIdMismatch() {
        store.createOrGet(buildBomCreate(TENANT_A, SESSION_1, 30L, 900L));

        CheckoutCartItemPlanCreate conflicting = buildBomCreate(TENANT_A, SESSION_1, 30L,
                999L); // different skuId

        assertThatThrownBy(() -> store.createOrGet(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("skuId mismatch");

        // Original row untouched
        CheckoutCartItemPlanDO found = mapper.selectBySessionAndCartItem(
                TENANT_A, SESSION_1, 30L);
        assertThat(found.getSkuId()).isEqualTo(900L);
    }

    @Test
    void createOrGet_throwsWhenClassificationMismatch() {
        store.createOrGet(buildBomCreate(TENANT_A, SESSION_1, 40L, 950L));

        // Same identity + skuId, but classification=NON_BOM (UNLIMITED variant)
        CheckoutCartItemPlanCreate conflicting = buildNonBomUnlimitedCreate(
                TENANT_A, SESSION_1, 40L, 950L);

        assertThatThrownBy(() -> store.createOrGet(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("classification mismatch");

        // Original row untouched
        CheckoutCartItemPlanDO found = mapper.selectBySessionAndCartItem(
                TENANT_A, SESSION_1, 40L);
        assertThat(found.getClassification()).isEqualTo("BOM");
    }

    @Test
    void createOrGet_sameCartItemDifferentTenantsCreatesSeparateRows() {
        TenantContextHolder.setTenantId(TENANT_A);
        CheckoutCartItemPlanCreate cmdA = buildBomCreate(TENANT_A, SESSION_1, 50L, 710L);
        CheckoutCartItemPlanDO rowA = store.createOrGet(cmdA);

        TenantContextHolder.setTenantId(TENANT_B);
        CheckoutCartItemPlanCreate cmdB = buildUnmappedInvalidSkuCreate(
                TENANT_B, SESSION_1, 50L, 710L);
        CheckoutCartItemPlanDO rowB = store.createOrGet(cmdB);

        assertThat(rowA.getId()).isNotEqualTo(rowB.getId());
        assertThat(rowA.getTenantId()).isEqualTo(TENANT_A);
        assertThat(rowB.getTenantId()).isEqualTo(TENANT_B);

        // Tenant A sees only its row; tenant B sees only its row
        TenantContextHolder.setTenantId(TENANT_A);
        assertThat(store.listBySession(TENANT_A, SESSION_1)).hasSize(1);
        TenantContextHolder.setTenantId(TENANT_B);
        assertThat(store.listBySession(TENANT_B, SESSION_1)).hasSize(1);
    }

    @Test
    void createOrGet_concurrentInsertsReturnSameRow() throws Exception {
        int threadCount = 8;
        CheckoutCartItemPlanCreate cmd = buildBomCreate(TENANT_A, SESSION_1, 60L, 720L);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threadCount);
        AtomicReference<Long> firstId = new AtomicReference<>(null);
        AtomicReference<Throwable> error = new AtomicReference<>(null);
        AtomicReference<Integer> idMismatch = new AtomicReference<>(0);

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(withTenant(TENANT_A, () -> {
                try {
                    startGate.await();
                    CheckoutCartItemPlanDO DO = store.createOrGet(cmd);
                    if (!firstId.compareAndSet(null, DO.getId())) {
                        if (!firstId.get().equals(DO.getId())) {
                            idMismatch.updateAndGet(v -> v + 1);
                        }
                    }
                } catch (Throwable th) {
                    error.compareAndSet(null, th);
                } finally {
                    finishGate.countDown();
                }
            }));
            t.start();
        }

        startGate.countDown();
        assertThat(finishGate.await(10, TimeUnit.SECONDS))
                .as("all createOrGet threads must finish").isTrue();
        assertThat(error.get()).as("no thread must throw").isNull();
        assertThat(idMismatch.get()).as("all threads must see the same id").isEqualTo(0);

        // Exactly one row in the table for this identity
        CheckoutCartItemPlanDO found = mapper.selectBySessionAndCartItem(
                TENANT_A, SESSION_1, 60L);
        assertThat(found).isNotNull();
        assertThat(found.getClassification()).isEqualTo("BOM");
        assertThat(store.listBySession(TENANT_A, SESSION_1)).hasSize(1);
    }

    @Test
    void createOrGet_rejectsNullClassification() {
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 70L, 730L, null,
                "SKU-730", StockStrategyEnum.TRACK_STOCK.getCode(),
                5730L, null, 6730L, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("classification must not be null");
    }

    @Test
    void createOrGet_rejectsNonPositiveIds() {
        CheckoutStockClassification c = CheckoutStockClassification.BOM;
        String skuCode = "SKU-730";
        String strategy = StockStrategyEnum.TRACK_STOCK.getCode();
        Long bomProductId = 5730L;
        Long locationId = 6730L;
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                0L, SESSION_1, 70L, 730L, c,
                skuCode, strategy, bomProductId, null, locationId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId must be positive");
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, 0L, 70L, 730L, c,
                skuCode, strategy, bomProductId, null, locationId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkoutSessionId must be positive");
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 0L, 730L, c,
                skuCode, strategy, bomProductId, null, locationId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cartItemId must be positive");
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 70L, 0L, c,
                skuCode, strategy, bomProductId, null, locationId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skuId must be positive");
        // Negative values are also rejected
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                -1L, SESSION_1, 70L, 730L, c,
                skuCode, strategy, bomProductId, null, locationId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId must be positive");
    }

    @Test
    void createOrGet_rejectsTenantContextMismatch() {
        TenantContextHolder.setTenantId(TENANT_B);
        CheckoutCartItemPlanCreate cmd = buildBomCreate(TENANT_A, SESSION_1, 80L, 740L);

        assertThatThrownBy(() -> store.createOrGet(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context mismatch");

        // No row should have been written for tenant A
        TenantContextHolder.setTenantId(TENANT_A);
        assertThat(store.listBySession(TENANT_A, SESSION_1)).isEmpty();
    }

    @Test
    void createOrGet_returnedTimeMatchesPersistedTime() {
        CheckoutCartItemPlanCreate cmd = buildBomCreate(TENANT_A, SESSION_1, 100L, 760L);
        CheckoutCartItemPlanDO result = store.createOrGet(cmd);

        // Re-read from DB to verify the returned value matches the persisted value
        CheckoutCartItemPlanDO persisted = mapper.selectBySessionAndCartItem(
                TENANT_A, SESSION_1, 100L);
        assertThat(persisted).isNotNull();
        assertThat(result.getCreateTime()).isEqualTo(persisted.getCreateTime());
        assertThat(result.getUpdateTime()).isEqualTo(persisted.getUpdateTime());

        // DATETIME(3) stores milliseconds; the in-memory value must be
        // truncated to millis so it round-trips without microsecond drift.
        assertThat(result.getCreateTime().getNano() % 1_000_000)
                .as("createTime must be millisecond-truncated (no microsecond drift)")
                .isEqualTo(0);
        assertThat(result.getUpdateTime().getNano() % 1_000_000)
                .as("updateTime must be millisecond-truncated (no microsecond drift)")
                .isEqualTo(0);
    }

    // ==================== listBySession (slice 2B foundation) ====================

    @Test
    void listBySession_returnsEmptyWhenNoPlans() {
        List<CheckoutCartItemPlanDO> rows = store.listBySession(TENANT_A, SESSION_1);
        assertThat(rows).isEmpty();
    }

    @Test
    void listBySession_returnsSinglePlan() {
        store.createOrGet(buildUnmappedInvalidSkuCreate(TENANT_A, SESSION_1, 80L, 740L));

        List<CheckoutCartItemPlanDO> rows = store.listBySession(TENANT_A, SESSION_1);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCartItemId()).isEqualTo(80L);
        assertThat(rows.get(0).getClassification()).isEqualTo("UNMAPPED");
    }

    @Test
    void listBySession_returnsRowsOrderedByCartItemIdThenId() {
        store.createOrGet(buildBomCreate(TENANT_A, SESSION_1, 300L, 740L));
        store.createOrGet(buildNonBomUnlimitedCreate(TENANT_A, SESSION_1, 100L, 741L));
        store.createOrGet(buildUnmappedInvalidSkuCreate(TENANT_A, SESSION_1, 200L, 742L));

        List<CheckoutCartItemPlanDO> rows = store.listBySession(TENANT_A, SESSION_1);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getCartItemId()).isEqualTo(100L);
        assertThat(rows.get(1).getCartItemId()).isEqualTo(200L);
        assertThat(rows.get(2).getCartItemId()).isEqualTo(300L);
    }

    @Test
    void listBySession_isTenantScoped_doesNotReturnOtherTenantRows() {
        store.createOrGet(buildBomCreate(TENANT_A, SESSION_1, 90L, 750L));

        TenantContextHolder.setTenantId(TENANT_B);
        store.createOrGet(buildNonBomUnlimitedCreate(TENANT_B, SESSION_1, 91L, 751L));

        TenantContextHolder.setTenantId(TENANT_A);
        List<CheckoutCartItemPlanDO> rowsA = store.listBySession(TENANT_A, SESSION_1);
        TenantContextHolder.setTenantId(TENANT_B);
        List<CheckoutCartItemPlanDO> rowsB = store.listBySession(TENANT_B, SESSION_1);

        assertThat(rowsA).hasSize(1);
        assertThat(rowsA.get(0).getTenantId()).isEqualTo(TENANT_A);
        assertThat(rowsA.get(0).getCartItemId()).isEqualTo(90L);

        assertThat(rowsB).hasSize(1);
        assertThat(rowsB.get(0).getTenantId()).isEqualTo(TENANT_B);
        assertThat(rowsB.get(0).getCartItemId()).isEqualTo(91L);
    }

    @Test
    void listBySession_doesNotReturnRowsFromOtherSession() {
        store.createOrGet(buildBomCreate(TENANT_A, SESSION_1, 110L, 760L));
        store.createOrGet(buildNonBomUnlimitedCreate(TENANT_A, SESSION_2, 111L, 761L));

        List<CheckoutCartItemPlanDO> rows1 = store.listBySession(TENANT_A, SESSION_1);
        List<CheckoutCartItemPlanDO> rows2 = store.listBySession(TENANT_A, SESSION_2);

        assertThat(rows1).hasSize(1);
        assertThat(rows1.get(0).getCheckoutSessionId()).isEqualTo(SESSION_1);
        assertThat(rows1.get(0).getCartItemId()).isEqualTo(110L);

        assertThat(rows2).hasSize(1);
        assertThat(rows2.get(0).getCheckoutSessionId()).isEqualTo(SESSION_2);
        assertThat(rows2.get(0).getCartItemId()).isEqualTo(111L);
    }

    @Test
    void listBySession_rejectsTenantContextMismatch() {
        store.createOrGet(buildBomCreate(TENANT_A, SESSION_1, 120L, 770L));

        TenantContextHolder.setTenantId(TENANT_B);
        assertThatThrownBy(() -> store.listBySession(TENANT_A, SESSION_1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context mismatch");
    }

    @Test
    void listBySession_rejectsNonPositiveIds() {
        assertThatThrownBy(() -> store.listBySession(TENANT_A, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkoutSessionId must be positive");
        assertThatThrownBy(() -> store.listBySession(TENANT_A, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkoutSessionId must be positive");
    }

    // ==================== Slice 2C-2B: valid matrix creates + persists six fields ====================

    @Test
    void createOrGet_validMatrix_nonBomUnlimited_persistsSixFields() {
        CheckoutCartItemPlanDO result = store.createOrGet(
                buildNonBomUnlimitedCreate(TENANT_A, SESSION_1, 200L, 800L));

        assertThat(result.getSkuCode()).isEqualTo("SKU-800");
        assertThat(result.getStockStrategy()).isEqualTo("UNLIMITED");
        assertThat(result.getBomProductId()).isNull();
        assertThat(result.getStockItemId()).isNull();
        assertThat(result.getLocationId()).isNull();
        assertThat(result.getClassificationReason()).isNull();

        CheckoutCartItemPlanDO found = mapper.selectBySessionAndCartItem(TENANT_A, SESSION_1, 200L);
        assertThat(found.getSkuCode()).isEqualTo("SKU-800");
        assertThat(found.getStockStrategy()).isEqualTo("UNLIMITED");
        assertThat(found.getBomProductId()).isNull();
        assertThat(found.getStockItemId()).isNull();
        assertThat(found.getLocationId()).isNull();
        assertThat(found.getClassificationReason()).isNull();
    }

    @Test
    void createOrGet_validMatrix_nonBomTrackStock_persistsSixFields() {
        CheckoutCartItemPlanDO result = store.createOrGet(
                buildNonBomTrackStockCreate(TENANT_A, SESSION_1, 201L, 801L));

        assertThat(result.getSkuCode()).isEqualTo("SKU-801");
        assertThat(result.getStockStrategy()).isEqualTo("TRACK_STOCK");
        assertThat(result.getBomProductId()).isNull();
        assertThat(result.getStockItemId()).isEqualTo(7801L);
        assertThat(result.getLocationId()).isEqualTo(8801L);
        assertThat(result.getClassificationReason()).isNull();
    }

    @Test
    void createOrGet_validMatrix_unmappedInvalidSkuCode_persistsSixFields() {
        CheckoutCartItemPlanDO result = store.createOrGet(
                buildUnmappedInvalidSkuCreate(TENANT_A, SESSION_1, 202L, 802L));

        assertThat(result.getSkuCode()).isNull();
        assertThat(result.getStockStrategy()).isNull();
        assertThat(result.getBomProductId()).isNull();
        assertThat(result.getStockItemId()).isNull();
        assertThat(result.getLocationId()).isNull();
        assertThat(result.getClassificationReason()).isEqualTo("INVALID_SKU_CODE");
    }

    @Test
    void createOrGet_validMatrix_unmappedInvalidStockStrategy_persistsSixFields() {
        CheckoutCartItemPlanDO result = store.createOrGet(
                buildUnmappedInvalidStrategyCreate(TENANT_A, SESSION_1, 203L, 803L));

        assertThat(result.getSkuCode()).isEqualTo("SKU-803");
        assertThat(result.getStockStrategy()).isNull();
        assertThat(result.getClassificationReason()).isEqualTo("INVALID_STOCK_STRATEGY");
    }

    @Test
    void createOrGet_validMatrix_unmappedNoProduct_persistsSixFields() {
        CheckoutCartItemPlanDO result = store.createOrGet(
                buildUnmappedTrackStockReasonCreate(TENANT_A, SESSION_1, 204L, 804L,
                        CheckoutClassificationReason.NO_PRODUCT));

        assertThat(result.getSkuCode()).isEqualTo("SKU-804");
        assertThat(result.getStockStrategy()).isEqualTo("TRACK_STOCK");
        assertThat(result.getClassificationReason()).isEqualTo("NO_PRODUCT");
    }

    @Test
    void createOrGet_validMatrix_unmappedNoStockItem_persistsSixFields() {
        CheckoutCartItemPlanDO result = store.createOrGet(
                buildUnmappedTrackStockReasonCreate(TENANT_A, SESSION_1, 205L, 805L,
                        CheckoutClassificationReason.NO_STOCK_ITEM));

        assertThat(result.getSkuCode()).isEqualTo("SKU-805");
        assertThat(result.getStockStrategy()).isEqualTo("TRACK_STOCK");
        assertThat(result.getClassificationReason()).isEqualTo("NO_STOCK_ITEM");
    }

    @Test
    void createOrGet_validMatrix_unmappedAmbiguousProduct_persistsSixFields() {
        CheckoutCartItemPlanDO result = store.createOrGet(
                buildUnmappedTrackStockReasonCreate(TENANT_A, SESSION_1, 206L, 806L,
                        CheckoutClassificationReason.AMBIGUOUS_PRODUCT));

        assertThat(result.getSkuCode()).isEqualTo("SKU-806");
        assertThat(result.getStockStrategy()).isEqualTo("TRACK_STOCK");
        assertThat(result.getBomProductId()).isNull();
        assertThat(result.getStockItemId()).isNull();
        assertThat(result.getLocationId()).isNull();
        assertThat(result.getClassificationReason()).isEqualTo("AMBIGUOUS_PRODUCT");
    }

    @Test
    void createOrGet_validMatrix_unmappedNoLocation_persistsSixFields() {
        CheckoutCartItemPlanDO result = store.createOrGet(
                buildUnmappedTrackStockReasonCreate(TENANT_A, SESSION_1, 207L, 807L,
                        CheckoutClassificationReason.NO_LOCATION));

        assertThat(result.getSkuCode()).isEqualTo("SKU-807");
        assertThat(result.getStockStrategy()).isEqualTo("TRACK_STOCK");
        assertThat(result.getBomProductId()).isNull();
        assertThat(result.getStockItemId()).isNull();
        assertThat(result.getLocationId()).isNull();
        assertThat(result.getClassificationReason()).isEqualTo("NO_LOCATION");
    }

    // ==================== Slice 2C-2B: invalid matrix throws IllegalArgumentException ====================

    @Test
    void createOrGet_invalidMatrix_bomNullSkuCode() {
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 300L, 900L,
                CheckoutStockClassification.BOM,
                null, StockStrategyEnum.TRACK_STOCK.getCode(),
                5900L, null, 6900L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skuCode for BOM must not be null");
    }

    @Test
    void createOrGet_invalidMatrix_bomWrongStrategy() {
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 301L, 901L,
                CheckoutStockClassification.BOM,
                "SKU-901", StockStrategyEnum.UNLIMITED.getCode(),
                5901L, null, 6901L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected stockStrategy=TRACK_STOCK");
    }

    @Test
    void createOrGet_invalidMatrix_bomNullBomProductId() {
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 302L, 902L,
                CheckoutStockClassification.BOM,
                "SKU-902", StockStrategyEnum.TRACK_STOCK.getCode(),
                null, null, 6902L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bomProductId for BOM must be positive");
    }

    @Test
    void createOrGet_invalidMatrix_bomNegativeBomProductId() {
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 303L, 903L,
                CheckoutStockClassification.BOM,
                "SKU-903", StockStrategyEnum.TRACK_STOCK.getCode(),
                -5L, null, 6903L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bomProductId must be positive when present");
    }

    @Test
    void createOrGet_invalidMatrix_bomNonNullStockItemId() {
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 304L, 904L,
                CheckoutStockClassification.BOM,
                "SKU-904", StockStrategyEnum.TRACK_STOCK.getCode(),
                5904L, 7904L, 6904L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stockItemId for BOM must be null");
    }

    @Test
    void createOrGet_invalidMatrix_nonBomNullStrategy() {
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 305L, 905L,
                CheckoutStockClassification.NON_BOM,
                "SKU-905", null,
                null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NON_BOM requires TRACK_STOCK or UNLIMITED");
    }

    @Test
    void createOrGet_invalidMatrix_nonBomTrackStockNonNullBomProductId() {
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 306L, 906L,
                CheckoutStockClassification.NON_BOM,
                "SKU-906", StockStrategyEnum.TRACK_STOCK.getCode(),
                5906L, 7906L, 8906L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bomProductId for NON_BOM+TRACK_STOCK must be null");
    }

    @Test
    void createOrGet_invalidMatrix_unmappedNullReason() {
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 307L, 907L,
                CheckoutStockClassification.UNMAPPED,
                "SKU-907", StockStrategyEnum.TRACK_STOCK.getCode(),
                null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("classificationReason for UNMAPPED must not be null");
    }

    @Test
    void createOrGet_invalidMatrix_unmappedNonNullBomProductId() {
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 308L, 908L,
                CheckoutStockClassification.UNMAPPED,
                "SKU-908", StockStrategyEnum.TRACK_STOCK.getCode(),
                5908L, null, null,
                CheckoutClassificationReason.NO_PRODUCT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bomProductId for UNMAPPED must be null");
    }

    @Test
    void createOrGet_invalidMatrix_invalidSkuCodeReasonWithNonNullSkuCode() {
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 309L, 909L,
                CheckoutStockClassification.UNMAPPED,
                "SKU-909", null, null, null, null,
                CheckoutClassificationReason.INVALID_SKU_CODE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skuCode for INVALID_SKU_CODE must be null");
    }

    @Test
    void createOrGet_invalidMatrix_invalidStockStrategyReasonWithNullSkuCode() {
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 310L, 910L,
                CheckoutStockClassification.UNMAPPED,
                null, null, null, null, null,
                CheckoutClassificationReason.INVALID_STOCK_STRATEGY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skuCode for INVALID_STOCK_STRATEGY must not be null");
    }

    @Test
    void createOrGet_invalidMatrix_noProductWithWrongStrategy() {
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 311L, 911L,
                CheckoutStockClassification.UNMAPPED,
                "SKU-911", StockStrategyEnum.UNLIMITED.getCode(),
                null, null, null,
                CheckoutClassificationReason.NO_PRODUCT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected stockStrategy=TRACK_STOCK");
    }

    // ==================== Slice 2C-2B: normalization (independent per field) ====================

    @Test
    void createOrGet_normalization_blankSkuCodeBecomesNullForInvalidSkuCode() {
        // Blank skuCode normalizes to null, satisfying INVALID_SKU_CODE matrix rule
        CheckoutCartItemPlanCreate cmd = new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 400L, 400L,
                CheckoutStockClassification.UNMAPPED,
                "   ", null, null, null, null,
                CheckoutClassificationReason.INVALID_SKU_CODE);

        CheckoutCartItemPlanDO result = store.createOrGet(cmd);
        assertThat(result.getSkuCode()).isNull();
        assertThat(result.getClassificationReason()).isEqualTo("INVALID_SKU_CODE");
    }

    @Test
    void createOrGet_normalization_invalidStockStrategyBecomesNullForInvalidStockStrategy() {
        // Invalid stockStrategy normalizes to null, satisfying INVALID_STOCK_STRATEGY matrix rule
        CheckoutCartItemPlanCreate cmd = new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 401L, 401L,
                CheckoutStockClassification.UNMAPPED,
                "SKU-401", "BOGUS_STRATEGY", null, null, null,
                CheckoutClassificationReason.INVALID_STOCK_STRATEGY);

        CheckoutCartItemPlanDO result = store.createOrGet(cmd);
        assertThat(result.getStockStrategy()).isNull();
        assertThat(result.getSkuCode()).isEqualTo("SKU-401");
        assertThat(result.getClassificationReason()).isEqualTo("INVALID_STOCK_STRATEGY");
    }

    // ==================== Slice 2C-2B: six-field replay mismatch ====================

    @Test
    void createOrGet_throwsWhenSkuCodeMismatch() {
        store.createOrGet(buildBomCreate(TENANT_A, SESSION_1, 500L, 500L));

        // Same identity + skuId, but different skuCode (still valid BOM matrix)
        CheckoutCartItemPlanCreate conflicting = new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 500L, 500L,
                CheckoutStockClassification.BOM,
                "SKU-DIFFERENT", StockStrategyEnum.TRACK_STOCK.getCode(),
                5500L, null, 6500L, null);

        assertThatThrownBy(() -> store.createOrGet(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("skuCode mismatch");
    }

    @Test
    void createOrGet_throwsWhenBomProductIdMismatch() {
        store.createOrGet(buildBomCreate(TENANT_A, SESSION_1, 501L, 501L));

        CheckoutCartItemPlanCreate conflicting = new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 501L, 501L,
                CheckoutStockClassification.BOM,
                "SKU-501", StockStrategyEnum.TRACK_STOCK.getCode(),
                9999L, null, 6501L, null);

        assertThatThrownBy(() -> store.createOrGet(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bomProductId mismatch");
    }

    @Test
    void createOrGet_throwsWhenClassificationReasonMismatch() {
        store.createOrGet(buildUnmappedTrackStockReasonCreate(
                TENANT_A, SESSION_1, 502L, 502L,
                CheckoutClassificationReason.NO_PRODUCT));

        CheckoutCartItemPlanCreate conflicting = new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 502L, 502L,
                CheckoutStockClassification.UNMAPPED,
                "SKU-502", StockStrategyEnum.TRACK_STOCK.getCode(),
                null, null, null,
                CheckoutClassificationReason.AMBIGUOUS_PRODUCT);

        assertThatThrownBy(() -> store.createOrGet(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("classificationReason mismatch");
    }

    @Test
    void createOrGet_throwsWhenStockStrategyMismatch() {
        // First: NON_BOM+UNLIMITED (strategy=UNLIMITED, all reference IDs null)
        store.createOrGet(buildNonBomUnlimitedCreate(TENANT_A, SESSION_1, 503L, 503L));

        // Replay: NON_BOM+TRACK_STOCK (strategy=TRACK_STOCK, stockItemId/locationId positive)
        // verifyIdentity checks stockStrategy before stockItemId/locationId, so the
        // mismatch is reported on stockStrategy.
        CheckoutCartItemPlanCreate conflicting = new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 503L, 503L,
                CheckoutStockClassification.NON_BOM,
                "SKU-503", StockStrategyEnum.TRACK_STOCK.getCode(),
                null, 7503L, 8503L, null);

        assertThatThrownBy(() -> store.createOrGet(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stockStrategy mismatch");
    }

    @Test
    void createOrGet_throwsWhenStockItemIdMismatch() {
        // First: NON_BOM+TRACK_STOCK with stockItemId=7504L
        store.createOrGet(buildNonBomTrackStockCreate(TENANT_A, SESSION_1, 504L, 504L));

        // Replay: same identity + skuId + strategy, but different stockItemId
        CheckoutCartItemPlanCreate conflicting = new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 504L, 504L,
                CheckoutStockClassification.NON_BOM,
                "SKU-504", StockStrategyEnum.TRACK_STOCK.getCode(),
                null, 9999L, 8504L, null);

        assertThatThrownBy(() -> store.createOrGet(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stockItemId mismatch");
    }

    @Test
    void createOrGet_throwsWhenLocationIdMismatch() {
        // First: BOM with locationId=6505L
        store.createOrGet(buildBomCreate(TENANT_A, SESSION_1, 505L, 505L));

        // Replay: same identity + skuId + strategy + bomProductId, but different locationId
        CheckoutCartItemPlanCreate conflicting = new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 505L, 505L,
                CheckoutStockClassification.BOM,
                "SKU-505", StockStrategyEnum.TRACK_STOCK.getCode(),
                5505L, null, 9999L, null);

        assertThatThrownBy(() -> store.createOrGet(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locationId mismatch");
    }
}
