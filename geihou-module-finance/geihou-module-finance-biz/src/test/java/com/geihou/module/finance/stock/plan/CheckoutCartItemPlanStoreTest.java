package com.geihou.module.finance.stock.plan;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.checkout.CheckoutTestSchemaInitializer;
import com.geihou.module.finance.stock.plan.dal.dataobject.CheckoutCartItemPlanDO;
import com.geihou.module.finance.stock.plan.dal.mapper.CheckoutCartItemPlanMapper;
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
 * 17-item test matrix for slice 2B (checkout classification plan
 * foundation).
 *
 * <p>Coverage:
 * <ul>
 *   <li>createOrGet (11 tests): insert, idempotent re-call, immutable-field
 *       mismatch (skuId, classification), tenant isolation, concurrent
 *       insert via {@code DuplicateKeyException}, null/non-positive ID
 *       rejection, tenant context mismatch fail-closed, returned time
 *       matches persisted time at {@code DATETIME(3)} millisecond
 *       precision.</li>
 *   <li>listBySession (6 tests): empty, single, ordering by
 *       {@code cart_item_id ASC, id ASC}, tenant isolation, session
 *       isolation, tenant context mismatch fail-closed, non-positive ID
 *       rejection.</li>
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

    // ==================== Helpers ====================

    private CheckoutCartItemPlanCreate buildCreate(long tenantId, long sessionId,
                                                   long cartItemId, long skuId,
                                                   CheckoutStockClassification c) {
        return new CheckoutCartItemPlanCreate(tenantId, sessionId, cartItemId, skuId, c);
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

    // ==================== createOrGet ====================

    @Test
    void createOrGet_insertsNewPlanAndReturnsIt() {
        CheckoutCartItemPlanCreate cmd = buildCreate(TENANT_A, SESSION_1, 10L, 700L,
                CheckoutStockClassification.BOM);

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
        CheckoutCartItemPlanCreate cmd = buildCreate(TENANT_A, SESSION_1, 20L, 800L,
                CheckoutStockClassification.NON_BOM);

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
        store.createOrGet(buildCreate(TENANT_A, SESSION_1, 30L, 900L,
                CheckoutStockClassification.BOM));

        CheckoutCartItemPlanCreate conflicting = buildCreate(TENANT_A, SESSION_1, 30L,
                999L, // different skuId
                CheckoutStockClassification.BOM);

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
        store.createOrGet(buildCreate(TENANT_A, SESSION_1, 40L, 950L,
                CheckoutStockClassification.BOM));

        CheckoutCartItemPlanCreate conflicting = buildCreate(TENANT_A, SESSION_1, 40L,
                950L,
                CheckoutStockClassification.NON_BOM); // different classification

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
        // Create cmdA with tenant A context first, then switch to B for cmdB.
        TenantContextHolder.setTenantId(TENANT_A);
        CheckoutCartItemPlanCreate cmdA = buildCreate(TENANT_A, SESSION_1, 50L, 710L,
                CheckoutStockClassification.BOM);
        CheckoutCartItemPlanDO rowA = store.createOrGet(cmdA);

        TenantContextHolder.setTenantId(TENANT_B);
        CheckoutCartItemPlanCreate cmdB = buildCreate(TENANT_B, SESSION_1, 50L, 710L,
                CheckoutStockClassification.UNMAPPED);
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
        CheckoutCartItemPlanCreate cmd = buildCreate(TENANT_A, SESSION_1, 60L, 720L,
                CheckoutStockClassification.BOM);

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
                TENANT_A, SESSION_1, 70L, 730L, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("classification must not be null");
    }

    @Test
    void createOrGet_rejectsNonPositiveIds() {
        CheckoutStockClassification c = CheckoutStockClassification.BOM;
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                0L, SESSION_1, 70L, 730L, c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId must be positive");
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, 0L, 70L, 730L, c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkoutSessionId must be positive");
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 0L, 730L, c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cartItemId must be positive");
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                TENANT_A, SESSION_1, 70L, 0L, c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skuId must be positive");
        // Negative values are also rejected
        assertThatThrownBy(() -> new CheckoutCartItemPlanCreate(
                -1L, SESSION_1, 70L, 730L, c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId must be positive");
    }

    @Test
    void createOrGet_rejectsTenantContextMismatch() {
        // Context is tenant B, but command targets tenant A: must fail-closed
        // before any SQL is issued (no INSERT, no SELECT with wrong tenant scope).
        TenantContextHolder.setTenantId(TENANT_B);
        CheckoutCartItemPlanCreate cmd = buildCreate(TENANT_A, SESSION_1, 80L, 740L,
                CheckoutStockClassification.BOM);

        assertThatThrownBy(() -> store.createOrGet(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context mismatch");

        // No row should have been written for tenant A
        TenantContextHolder.setTenantId(TENANT_A);
        assertThat(store.listBySession(TENANT_A, SESSION_1)).isEmpty();
    }

    @Test
    void createOrGet_returnedTimeMatchesPersistedTime() {
        CheckoutCartItemPlanCreate cmd = buildCreate(TENANT_A, SESSION_1, 100L, 760L,
                CheckoutStockClassification.BOM);
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

    // ==================== listBySession ====================

    @Test
    void listBySession_returnsEmptyWhenNoPlans() {
        List<CheckoutCartItemPlanDO> rows = store.listBySession(TENANT_A, SESSION_1);
        assertThat(rows).isEmpty();
    }

    @Test
    void listBySession_returnsSinglePlan() {
        store.createOrGet(buildCreate(TENANT_A, SESSION_1, 80L, 740L,
                CheckoutStockClassification.UNMAPPED));

        List<CheckoutCartItemPlanDO> rows = store.listBySession(TENANT_A, SESSION_1);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCartItemId()).isEqualTo(80L);
        assertThat(rows.get(0).getClassification()).isEqualTo("UNMAPPED");
    }

    @Test
    void listBySession_returnsRowsOrderedByCartItemIdThenId() {
        // Insert in non-sorted cart_item_id order; the SELECT must sort by
        // cart_item_id ASC, id ASC.
        store.createOrGet(buildCreate(TENANT_A, SESSION_1, 300L, 740L,
                CheckoutStockClassification.BOM));
        store.createOrGet(buildCreate(TENANT_A, SESSION_1, 100L, 741L,
                CheckoutStockClassification.NON_BOM));
        store.createOrGet(buildCreate(TENANT_A, SESSION_1, 200L, 742L,
                CheckoutStockClassification.UNMAPPED));

        List<CheckoutCartItemPlanDO> rows = store.listBySession(TENANT_A, SESSION_1);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getCartItemId()).isEqualTo(100L);
        assertThat(rows.get(1).getCartItemId()).isEqualTo(200L);
        assertThat(rows.get(2).getCartItemId()).isEqualTo(300L);
    }

    @Test
    void listBySession_isTenantScoped_doesNotReturnOtherTenantRows() {
        store.createOrGet(buildCreate(TENANT_A, SESSION_1, 90L, 750L,
                CheckoutStockClassification.BOM));

        TenantContextHolder.setTenantId(TENANT_B);
        store.createOrGet(buildCreate(TENANT_B, SESSION_1, 91L, 751L,
                CheckoutStockClassification.NON_BOM));

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
        store.createOrGet(buildCreate(TENANT_A, SESSION_1, 110L, 760L,
                CheckoutStockClassification.BOM));
        store.createOrGet(buildCreate(TENANT_A, SESSION_2, 111L, 761L,
                CheckoutStockClassification.NON_BOM));

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
        // Pre-populate tenant A
        store.createOrGet(buildCreate(TENANT_A, SESSION_1, 120L, 770L,
                CheckoutStockClassification.BOM));

        // Switch to tenant B; asking for tenant A's session must fail-closed
        // before the SELECT is issued (the MyBatis-Plus tenant interceptor
        // would otherwise silently scope the query to tenant B).
        TenantContextHolder.setTenantId(TENANT_B);
        assertThatThrownBy(() -> store.listBySession(TENANT_A, SESSION_1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context mismatch");
    }

    @Test
    void listBySession_rejectsNonPositiveIds() {
        // Tenant context matches (TENANT_A), but checkoutSessionId is non-positive.
        // validateTenantContext passes, requirePositive must reject.
        assertThatThrownBy(() -> store.listBySession(TENANT_A, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkoutSessionId must be positive");
        assertThatThrownBy(() -> store.listBySession(TENANT_A, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkoutSessionId must be positive");
    }
}
