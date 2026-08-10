package com.geihou.module.finance.stock.saga;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.cart.enums.CartEventTypeEnum;
import com.geihou.module.finance.checkout.CheckoutTestSchemaInitializer;
import com.geihou.module.finance.checkout.framework.CheckoutStatusEnum;
import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockSagaIntentDO;
import com.geihou.module.finance.stock.saga.dal.mapper.FinanceStockSagaIntentMapper;
import com.geihou.module.finance.stock.saga.enums.FinanceStockSagaType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link FinanceStockSagaIntentStoreImpl}
 * (G0-04H185 FIN-CONSISTENCY slice 2C-2D).
 *
 * <p>Covers createOrGet (freeze + idempotency + frozen-field conflict),
 * selectByIdentity (row/dirty-row revalidation), finalizePending CAS, the
 * three deterministic tenant-isolation tests (A/A, B/A guard throws before
 * SQL, B/B null/false). The MySQL-specific concurrent createOrGet contract is
 * covered by {@link FinanceStockSagaIntentMySqlConcurrencyTest}.
 */
@SpringBootTest(
        classes = FinanceStockCommandTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fsi_store_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class FinanceStockSagaIntentStoreTest {

    @Autowired private FinanceStockSagaIntentStore store;
    @Autowired private FinanceStockSagaIntentMapper mapper;
    @Autowired private DataSource dataSource;

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;

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

    private FinanceStockSagaIntentCreate createAbandoned(long tenantId, long sagaId,
                                                         long cartId, long operatorUserId) {
        return new FinanceStockSagaIntentCreate(
                tenantId,
                FinanceStockSagaType.CHECKOUT,
                sagaId,
                cartId,
                operatorUserId,
                "CUSTOMER",
                CheckoutStatusEnum.INITIATED.getCode(),
                CheckoutStatusEnum.ABANDONED.getCode(),
                CartEventTypeEnum.CHECKOUT_ABANDONED.getCode());
    }

    private FinanceStockSagaIntentDO existingPending(long tenantId, long sagaId,
                                                      long cartId, long operatorUserId) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        FinanceStockSagaIntentDO intent = new FinanceStockSagaIntentDO();
        intent.setId(42L);
        intent.setTenantId(tenantId);
        intent.setSagaType(FinanceStockSagaType.CHECKOUT);
        intent.setSagaId(sagaId);
        intent.setCartId(cartId);
        intent.setOperatorUserId(operatorUserId);
        intent.setOperatorRole("CUSTOMER");
        intent.setExpectedCheckoutStatus(CheckoutStatusEnum.INITIATED.getCode());
        intent.setTargetCheckoutStatus(CheckoutStatusEnum.ABANDONED.getCode());
        intent.setCartEventType(CartEventTypeEnum.CHECKOUT_ABANDONED.getCode());
        intent.setFinalizationStatus(FinanceStockSagaIntentDO.FINALIZATION_STATUS_PENDING);
        intent.setFinalizedAt(null);
        intent.setCreateTime(now);
        intent.setUpdateTime(now);
        return intent;
    }

    // ==================== createOrGet: freeze ====================

    @Test
    void createOrGet_createsPendingWithFrozenFields() {
        long sagaId = 100L;
        long cartId = 300L;
        FinanceStockSagaIntentCreate create = createAbandoned(TENANT_A, sagaId, cartId, 9001L);
        FinanceStockSagaIntentDO DO = store.createOrGet(create);

        assertThat(DO.getId()).isNotNull();
        assertThat(DO.getTenantId()).isEqualTo(TENANT_A);
        assertThat(DO.getSagaType()).isEqualTo(FinanceStockSagaType.CHECKOUT);
        assertThat(DO.getSagaId()).isEqualTo(sagaId);
        assertThat(DO.getCartId()).isEqualTo(cartId);
        assertThat(DO.getExpectedCheckoutStatus()).isEqualTo("INITIATED");
        assertThat(DO.getTargetCheckoutStatus()).isEqualTo("ABANDONED");
        assertThat(DO.getCartEventType()).isEqualTo("CHECKOUT_ABANDONED");
        assertThat(DO.getOperatorUserId()).isEqualTo(9001L);
        assertThat(DO.getOperatorRole()).isEqualTo("CUSTOMER");
        assertThat(DO.getFinalizationStatus())
                .isEqualTo(FinanceStockSagaIntentStore.FINALIZATION_STATUS_PENDING);
        assertThat(DO.getFinalizedAt()).isNull();
        assertThat(DO.getCreateTime()).isNotNull();
        assertThat(DO.getUpdateTime()).isNotNull();
        assertThat(DO.getCreateTime().getNano() % 1_000_000).isZero();
    }

    // ==================== createOrGet: idempotency ====================

    @Test
    void createOrGet_sameIdentity_returnsExisting() {
        FinanceStockSagaIntentCreate create = createAbandoned(TENANT_A, 100L, 300L, 9001L);
        FinanceStockSagaIntentDO first = store.createOrGet(create);
        FinanceStockSagaIntentDO second = store.createOrGet(create);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getCartId()).isEqualTo(first.getCartId());
    }

    @Test
    void createOrGet_insertMustAffectOneRowAndGenerateId() {
        FinanceStockSagaIntentCreate create =
                createAbandoned(TENANT_A, 100L, 300L, 9001L);

        FinanceStockSagaIntentMapper zeroMapper = mock(FinanceStockSagaIntentMapper.class);
        when(zeroMapper.selectByIdentity(TENANT_A, FinanceStockSagaType.CHECKOUT.name(), 100L))
                .thenReturn(null);
        when(zeroMapper.insert(any(FinanceStockSagaIntentDO.class))).thenReturn(0);
        FinanceStockSagaIntentStoreImpl zeroStore =
                new FinanceStockSagaIntentStoreImpl(zeroMapper);
        assertThatThrownBy(() -> zeroStore.createOrGet(create))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one durable intent insert");

        FinanceStockSagaIntentMapper noIdMapper = mock(FinanceStockSagaIntentMapper.class);
        when(noIdMapper.selectByIdentity(TENANT_A, FinanceStockSagaType.CHECKOUT.name(), 100L))
                .thenReturn(null);
        when(noIdMapper.insert(any(FinanceStockSagaIntentDO.class))).thenReturn(1);
        FinanceStockSagaIntentStoreImpl noIdStore =
                new FinanceStockSagaIntentStoreImpl(noIdMapper);
        assertThatThrownBy(() -> noIdStore.createOrGet(create))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("generated id");
    }

    @Test
    void createOrGet_duplicateKey_reconcilesWithCurrentRead() {
        FinanceStockSagaIntentCreate create =
                createAbandoned(TENANT_A, 100L, 300L, 9001L);
        FinanceStockSagaIntentDO winner =
                existingPending(TENANT_A, 100L, 300L, 9001L);
        FinanceStockSagaIntentMapper raceMapper = mock(FinanceStockSagaIntentMapper.class);
        when(raceMapper.selectByIdentity(
                TENANT_A, FinanceStockSagaType.CHECKOUT.name(), 100L))
                .thenReturn(null);
        when(raceMapper.insert(any(FinanceStockSagaIntentDO.class)))
                .thenThrow(new DuplicateKeyException("concurrent winner"));
        when(raceMapper.selectByIdentityForShare(
                TENANT_A, FinanceStockSagaType.CHECKOUT.name(), 100L))
                .thenReturn(winner);

        FinanceStockSagaIntentStoreImpl raceStore =
                new FinanceStockSagaIntentStoreImpl(raceMapper);

        assertThat(raceStore.createOrGet(create)).isSameAs(winner);
        verify(raceMapper).selectByIdentityForShare(
                TENANT_A, FinanceStockSagaType.CHECKOUT.name(), 100L);
        verify(raceMapper, never()).selectByIdentityForUpdate(
                TENANT_A, FinanceStockSagaType.CHECKOUT.name(), 100L);
    }

    @Test
    void createOrGet_duplicateKeyWinnerMissing_throws() {
        FinanceStockSagaIntentCreate create =
                createAbandoned(TENANT_A, 100L, 300L, 9001L);
        FinanceStockSagaIntentMapper raceMapper = mock(FinanceStockSagaIntentMapper.class);
        when(raceMapper.selectByIdentity(
                TENANT_A, FinanceStockSagaType.CHECKOUT.name(), 100L))
                .thenReturn(null);
        when(raceMapper.insert(any(FinanceStockSagaIntentDO.class)))
                .thenThrow(new DuplicateKeyException("concurrent winner"));
        when(raceMapper.selectByIdentityForShare(
                TENANT_A, FinanceStockSagaType.CHECKOUT.name(), 100L))
                .thenReturn(null);

        FinanceStockSagaIntentStoreImpl raceStore =
                new FinanceStockSagaIntentStoreImpl(raceMapper);

        assertThatThrownBy(() -> raceStore.createOrGet(create))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Concurrent insert vanished");
    }

    @Test
    void createOrGet_duplicateKeyWinnerFrozenFieldsConflict_throws() {
        FinanceStockSagaIntentCreate create =
                createAbandoned(TENANT_A, 100L, 300L, 9001L);
        FinanceStockSagaIntentDO conflictingWinner =
                existingPending(TENANT_A, 100L, 301L, 9001L);
        FinanceStockSagaIntentMapper raceMapper = mock(FinanceStockSagaIntentMapper.class);
        when(raceMapper.selectByIdentity(
                TENANT_A, FinanceStockSagaType.CHECKOUT.name(), 100L))
                .thenReturn(null);
        when(raceMapper.insert(any(FinanceStockSagaIntentDO.class)))
                .thenThrow(new DuplicateKeyException("concurrent winner"));
        when(raceMapper.selectByIdentityForShare(
                TENANT_A, FinanceStockSagaType.CHECKOUT.name(), 100L))
                .thenReturn(conflictingWinner);

        FinanceStockSagaIntentStoreImpl raceStore =
                new FinanceStockSagaIntentStoreImpl(raceMapper);

        assertThatThrownBy(() -> raceStore.createOrGet(create))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different cartId");
    }

    @Test
    void createOrGet_sameIdentityDifferentFrozenFields_throws() {
        store.createOrGet(createAbandoned(TENANT_A, 100L, 300L, 9001L));

        FinanceStockSagaIntentCreate differentRole = new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, 300L,
                9001L, "STAFF", CheckoutStatusEnum.INITIATED.getCode(),
                CheckoutStatusEnum.ABANDONED.getCode(),
                CartEventTypeEnum.CHECKOUT_ABANDONED.getCode());
        assertThatThrownBy(() -> store.createOrGet(differentRole))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("operatorRole");
    }

    @Test
    void createOrGet_eachFrozenFieldConflict_throws() {
        store.createOrGet(createAbandoned(TENANT_A, 100L, 300L, 9001L));

        FinanceStockSagaIntentCreate differentCart = new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, 301L,
                9001L, "CUSTOMER", CheckoutStatusEnum.INITIATED.getCode(),
                CheckoutStatusEnum.ABANDONED.getCode(),
                CartEventTypeEnum.CHECKOUT_ABANDONED.getCode());
        FinanceStockSagaIntentCreate differentOperator = new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, 300L,
                9002L, "CUSTOMER", CheckoutStatusEnum.INITIATED.getCode(),
                CheckoutStatusEnum.ABANDONED.getCode(),
                CartEventTypeEnum.CHECKOUT_ABANDONED.getCode());
        FinanceStockSagaIntentCreate differentTarget = new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, 300L,
                9001L, "CUSTOMER", CheckoutStatusEnum.INITIATED.getCode(),
                CheckoutStatusEnum.FAILED.getCode(),
                CartEventTypeEnum.CHECKOUT_ABANDONED.getCode());

        assertThatThrownBy(() -> store.createOrGet(differentCart))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cartId");
        assertThatThrownBy(() -> store.createOrGet(differentOperator))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("operatorUserId");
        assertThatThrownBy(() -> store.createOrGet(differentTarget))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("targetCheckoutStatus");
    }

    @Test
    void createOrGet_dirtyExpectedAndEventFields_throwIndependently() {
        FinanceStockSagaIntentDO existing =
                store.createOrGet(createAbandoned(TENANT_A, 100L, 300L, 9001L));

        existing.setExpectedCheckoutStatus(CheckoutStatusEnum.PAID.getCode());
        mapper.updateById(existing);
        assertThatThrownBy(() -> store.createOrGet(
                createAbandoned(TENANT_A, 100L, 300L, 9001L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expectedCheckoutStatus");

        existing.setExpectedCheckoutStatus(CheckoutStatusEnum.INITIATED.getCode());
        existing.setCartEventType(CartEventTypeEnum.CART_EXPIRED.getCode());
        mapper.updateById(existing);
        assertThatThrownBy(() -> store.createOrGet(
                createAbandoned(TENANT_A, 100L, 300L, 9001L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHECKOUT_ABANDONED");
    }

    // ==================== createOrGet: validation ====================

    @Test
    void create_invalidTarget_throws() {
        assertThatThrownBy(() -> new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, 300L,
                9001L, "CUSTOMER", CheckoutStatusEnum.INITIATED.getCode(),
                CheckoutStatusEnum.PAID.getCode(),
                CartEventTypeEnum.CHECKOUT_ABANDONED.getCode()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("targetCheckoutStatus");
    }

    @Test
    void create_invalidOperatorRole_throws() {
        assertThatThrownBy(() -> new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, 300L,
                9001L, "SYSTEM", CheckoutStatusEnum.INITIATED.getCode(),
                CheckoutStatusEnum.ABANDONED.getCode(),
                CartEventTypeEnum.CHECKOUT_ABANDONED.getCode()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("operatorRole");
    }

    @Test
    void create_eventMismatchedWithTarget_throws() {
        assertThatThrownBy(() -> new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, 300L,
                9001L, "CUSTOMER", CheckoutStatusEnum.INITIATED.getCode(),
                CheckoutStatusEnum.ABANDONED.getCode(),
                CartEventTypeEnum.CART_EXPIRED.getCode()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHECKOUT_ABANDONED");
    }

    @Test
    void create_invalidExpectedUnknownEventBlankAndNonCheckout_throw() {
        assertThatThrownBy(() -> new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, 300L,
                9001L, "CUSTOMER", CheckoutStatusEnum.PAID.getCode(),
                CheckoutStatusEnum.ABANDONED.getCode(),
                CartEventTypeEnum.CHECKOUT_ABANDONED.getCode()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expectedCheckoutStatus");

        assertThatThrownBy(() -> new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, 300L,
                9001L, "CUSTOMER", CheckoutStatusEnum.INITIATED.getCode(),
                "UNKNOWN", CartEventTypeEnum.CHECKOUT_ABANDONED.getCode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown checkout status");

        assertThatThrownBy(() -> new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, 300L,
                9001L, "CUSTOMER", CheckoutStatusEnum.INITIATED.getCode(),
                CheckoutStatusEnum.ABANDONED.getCode(), CartEventTypeEnum.ITEM_ADDED.getCode()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHECKOUT_ABANDONED");

        assertThatThrownBy(() -> new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, 300L,
                9001L, "CUSTOMER", CheckoutStatusEnum.INITIATED.getCode(),
                CheckoutStatusEnum.ABANDONED.getCode(), "UNKNOWN_EVENT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown cart event type");

        assertThatThrownBy(() -> new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, 300L,
                9001L, "CUSTOMER", " ", CheckoutStatusEnum.ABANDONED.getCode(),
                CartEventTypeEnum.CHECKOUT_ABANDONED.getCode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedCheckoutStatus");

        assertThatThrownBy(() -> new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, 300L,
                9001L, "CUSTOMER", CheckoutStatusEnum.INITIATED.getCode(), " ",
                CartEventTypeEnum.CHECKOUT_ABANDONED.getCode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetCheckoutStatus");

        assertThatThrownBy(() -> new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, 300L,
                9001L, "CUSTOMER", CheckoutStatusEnum.INITIATED.getCode(),
                CheckoutStatusEnum.ABANDONED.getCode(), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cartEventType");

        assertThatThrownBy(() -> new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, 300L,
                9001L, " ", CheckoutStatusEnum.INITIATED.getCode(),
                CheckoutStatusEnum.ABANDONED.getCode(),
                CartEventTypeEnum.CHECKOUT_ABANDONED.getCode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operatorRole");

        assertThatThrownBy(() -> new FinanceStockSagaIntentCreate(
                TENANT_A, FinanceStockSagaType.REFUND, 100L, 300L,
                9001L, "CUSTOMER", CheckoutStatusEnum.INITIATED.getCode(),
                CheckoutStatusEnum.ABANDONED.getCode(),
                CartEventTypeEnum.CHECKOUT_ABANDONED.getCode()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHECKOUT");
    }

    @Test
    void create_nonPositiveIds_throw() {
        assertThatThrownBy(() -> createAbandoned(0L, 100L, 300L, 9001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> createAbandoned(TENANT_A, 0L, 300L, 9001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sagaId");
        assertThatThrownBy(() -> createAbandoned(TENANT_A, 100L, 0L, 9001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cartId");
        assertThatThrownBy(() -> createAbandoned(TENANT_A, 100L, 300L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operatorUserId");
    }

    @Test
    void store_nonCheckoutSagaType_rejectedBeforeSql() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        assertThatThrownBy(() ->
                store.selectByIdentity(TENANT_A, FinanceStockSagaType.REFUND, 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CHECKOUT");
        assertThatThrownBy(() ->
                store.finalizePending(TENANT_A, FinanceStockSagaType.REFUND, 100L, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CHECKOUT");
    }

    // ==================== selectByIdentity ====================

    @Test
    void selectByIdentity_returnsRowOrNull() {
        FinanceStockSagaIntentDO created = store.createOrGet(createAbandoned(TENANT_A, 100L, 300L, 9001L));

        FinanceStockSagaIntentDO found = store.selectByIdentity(TENANT_A, FinanceStockSagaType.CHECKOUT, 100L);
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(created.getId());

        FinanceStockSagaIntentDO missing = store.selectByIdentity(TENANT_A, FinanceStockSagaType.CHECKOUT, 999L);
        assertThat(missing).isNull();
    }

    @Test
    void selectByIdentity_dirtyRow_revalidated() {
        // Insert a dirty row directly, bypassing the store (target is not a valid terminal status).
        FinanceStockSagaIntentDO dirty = new FinanceStockSagaIntentDO();
        dirty.setTenantId(TENANT_A);
        dirty.setSagaType(FinanceStockSagaType.CHECKOUT);
        dirty.setSagaId(777L);
        dirty.setCartId(300L);
        dirty.setExpectedCheckoutStatus("INITIATED");
        dirty.setTargetCheckoutStatus("PAID");
        dirty.setCartEventType("CHECKOUT_ABANDONED");
        dirty.setOperatorUserId(9001L);
        dirty.setOperatorRole("CUSTOMER");
        dirty.setFinalizationStatus("PENDING");
        dirty.setCreateTime(LocalDateTime.now());
        dirty.setUpdateTime(LocalDateTime.now());
        mapper.insert(dirty);

        assertThatThrownBy(() -> store.selectByIdentity(TENANT_A, FinanceStockSagaType.CHECKOUT, 777L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("targetCheckoutStatus");
        assertThatThrownBy(() -> store.createOrGet(
                createAbandoned(TENANT_A, 777L, 300L, 9001L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("targetCheckoutStatus");
    }

    @Test
    void selectByIdentity_dirtyFinalizationPair_rejected() {
        FinanceStockSagaIntentDO intent =
                store.createOrGet(createAbandoned(TENANT_A, 100L, 300L, 9001L));

        intent.setFinalizationStatus(FinanceStockSagaIntentDO.FINALIZATION_STATUS_FINALIZED);
        intent.setFinalizedAt(null);
        mapper.updateById(intent);
        assertThatThrownBy(() -> store.selectByIdentity(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FINALIZED intent");

        intent.setFinalizationStatus(FinanceStockSagaIntentDO.FINALIZATION_STATUS_PENDING);
        intent.setFinalizedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        mapper.updateById(intent);
        assertThatThrownBy(() -> store.selectByIdentity(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING intent");
    }

    // ==================== finalizePending ====================

    @Test
    void finalizePending_pendingToFinalized_thenFalse() {
        store.createOrGet(createAbandoned(TENANT_A, 100L, 300L, 9001L));
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

        boolean first = store.finalizePending(TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, now);
        assertThat(first).isTrue();

        FinanceStockSagaIntentDO finalized =
                store.selectByIdentity(TENANT_A, FinanceStockSagaType.CHECKOUT, 100L);
        assertThat(finalized.getFinalizationStatus())
                .isEqualTo(FinanceStockSagaIntentStore.FINALIZATION_STATUS_FINALIZED);
        assertThat(finalized.getFinalizedAt()).isEqualTo(now);

        boolean second = store.finalizePending(TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, now);
        assertThat(second).isFalse();
    }

    // ==================== Tenant isolation (deterministic) ====================

    @Test
    void tenantIsolation_sameTenant_createAndRead() {
        FinanceStockSagaIntentDO created = store.createOrGet(createAbandoned(TENANT_A, 100L, 300L, 9001L));
        FinanceStockSagaIntentDO found =
                store.selectByIdentity(TENANT_A, FinanceStockSagaType.CHECKOUT, 100L);
        assertThat(found.getId()).isEqualTo(created.getId());
    }

    @Test
    void tenantIsolation_contextMismatch_guardThrowsBeforeSql() {
        // Context B, parameter tenant A -> guard must throw before any SQL.
        FinanceStockSagaIntentCreate create =
                createAbandoned(TENANT_A, 100L, 300L, 9001L);
        TenantContextHolder.setTenantId(TENANT_B);
        assertThatThrownBy(() -> store.createOrGet(create))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context mismatch");
        assertThatThrownBy(() -> store.selectByIdentity(TENANT_A, FinanceStockSagaType.CHECKOUT, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context mismatch");
        assertThatThrownBy(() -> store.finalizePending(
                TENANT_A, FinanceStockSagaType.CHECKOUT, 100L, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context mismatch");
    }

    @Test
    void tenantIsolation_tenantB_noRows_returnsNullFalse() {
        store.createOrGet(createAbandoned(TENANT_A, 100L, 300L, 9001L));
        TenantContextHolder.setTenantId(TENANT_B);
        FinanceStockSagaIntentDO none =
                store.selectByIdentity(TENANT_B, FinanceStockSagaType.CHECKOUT, 100L);
        assertThat(none).isNull();

        boolean finalized = store.finalizePending(
                TENANT_B, FinanceStockSagaType.CHECKOUT, 100L,
                LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        assertThat(finalized).isFalse();
    }

    @Test
    void tenantId_nonPositiveRejectedBeforeSql() {
        TenantContextHolder.setTenantId(0L);
        assertThatThrownBy(() ->
                store.selectByIdentity(0L, FinanceStockSagaType.CHECKOUT, 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> store.finalizePending(
                0L, FinanceStockSagaType.CHECKOUT, 100L, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

}
