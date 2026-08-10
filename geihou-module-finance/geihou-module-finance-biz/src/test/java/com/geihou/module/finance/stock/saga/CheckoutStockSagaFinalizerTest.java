package com.geihou.module.finance.stock.saga;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.cart.enums.CartEventTypeEnum;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.dataobject.CartEventLogDO;
import com.geihou.module.finance.cart.dal.mapper.CartEventLogMapper;
import com.geihou.module.finance.cart.dal.mapper.CartMapper;
import com.geihou.module.finance.checkout.CheckoutTestSchemaInitializer;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutSessionDO;
import com.geihou.module.finance.checkout.dal.mapper.CheckoutSessionMapper;
import com.geihou.module.finance.checkout.framework.CheckoutStatusEnum;
import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockSagaIntentDO;
import com.geihou.module.finance.stock.saga.dal.mapper.FinanceStockSagaIntentMapper;
import com.geihou.module.finance.stock.saga.enums.FinanceStockSagaType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Integration tests for {@link CheckoutStockSagaFinalizerImpl}
 * (G0-04H185 FIN-CONSISTENCY slice 2C-2D, section 7 finalizer).
 *
 * <p>Covers the winner full sequence, CAS=0 reconciliation (no side effects),
 * the already-FINALIZED fast path, the fail-closed guards, the tenant guard,
 * and a REAL two-thread winner/loser race producing exactly one event and one
 * version increment.
 */
@SpringBootTest(
        classes = FinanceStockCommandTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fsf_finalizer_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CheckoutStockSagaFinalizerTest {

    @Autowired private CheckoutStockSagaFinalizer finalizer;
    @MockitoSpyBean private FinanceStockSagaIntentStore intentStore;
    @Autowired private FinanceStockSagaIntentMapper intentMapper;
    @MockitoSpyBean private CheckoutSessionMapper sessionMapper;
    @Autowired private CartMapper cartMapper;
    @MockitoSpyBean private CartEventLogMapper eventLogMapper;
    @Autowired private DataSource dataSource;

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;
    private static final long OPERATOR = 9001L;

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

    private long insertCart(long tenantId, String status) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        CartDO cart = new CartDO();
        cart.setTenantId(tenantId);
        cart.setCustomerUserId(500L);
        cart.setShopId(600L);
        cart.setChannel("CUSTOMER");
        cart.setStatus(status);
        cart.setVersion(0);
        cart.setLastActivityTime(now);
        cart.setCreateTime(now);
        cart.setUpdateTime(now);
        cartMapper.insert(cart);
        return cart.getId();
    }

    private long insertSession(long tenantId, long cartId, String status, long sagaId) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        CheckoutSessionDO session = new CheckoutSessionDO();
        session.setId(sagaId);
        session.setTenantId(tenantId);
        session.setCartId(cartId);
        session.setCustomerUserId(500L);
        session.setShopId(600L);
        session.setSessionToken("tok-" + sagaId);
        session.setStatus(status);
        session.setExpireTime(now.plusHours(1));
        session.setChannel("CUSTOMER");
        session.setCreateTime(now);
        session.setUpdateTime(now);
        sessionMapper.insert(session);
        return session.getId();
    }

    private FinanceStockSagaIntentCreate abandonedIntent(long tenantId, long sagaId, long cartId) {
        return new FinanceStockSagaIntentCreate(
                tenantId,
                FinanceStockSagaType.CHECKOUT,
                sagaId,
                cartId,
                OPERATOR,
                "CUSTOMER",
                CheckoutStatusEnum.INITIATED.getCode(),
                CheckoutStatusEnum.ABANDONED.getCode(),
                CartEventTypeEnum.CHECKOUT_ABANDONED.getCode());
    }

    private void setupHappyPath() {
        long cartId = insertCart(TENANT_A, "CHECKOUT");
        long sagaId = insertSession(TENANT_A, cartId, "INITIATED", 100L);
        intentStore.createOrGet(abandonedIntent(TENANT_A, sagaId, cartId));
    }

    // ==================== Winner full sequence ====================

    @Test
    void finalizeSaga_abandoned_winnerPerformsFullSequence() {
        long cartId = insertCart(TENANT_A, "CHECKOUT");
        long sagaId = insertSession(TENANT_A, cartId, "INITIATED", 100L);
        intentStore.createOrGet(abandonedIntent(TENANT_A, sagaId, cartId));

        boolean result = finalizer.finalizeSaga(TENANT_A, FinanceStockSagaType.CHECKOUT, sagaId);
        assertThat(result).isTrue();

        CheckoutSessionDO session = sessionMapper.selectByIdTenant(TENANT_A, sagaId);
        assertThat(session.getStatus()).isEqualTo("ABANDONED");
        assertThat(session.getUpdater()).isEqualTo(String.valueOf(OPERATOR));

        CartDO cart = cartMapper.selectById(cartId);
        assertThat(cart.getStatus()).isEqualTo("ACTIVE");
        assertThat(cart.getVersion()).isEqualTo(1);
        assertThat(cart.getLastActivityTime()).isNotNull();
        assertThat(cart.getUpdater()).isEqualTo(String.valueOf(OPERATOR));

        List<CartEventLogDO> events = eventLogMapper.selectList(null);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTenantId()).isEqualTo(TENANT_A);
        assertThat(events.get(0).getCartId()).isEqualTo(cartId);
        assertThat(events.get(0).getEventType()).isEqualTo("CHECKOUT_ABANDONED");
        assertThat(events.get(0).getEventTime()).isNotNull();
        assertThat(events.get(0).getOperatorUserId()).isEqualTo(OPERATOR);
        assertThat(events.get(0).getOperatorRole()).isEqualTo("CUSTOMER");
        assertThat(events.get(0).getCreateTime()).isNotNull();

        FinanceStockSagaIntentDO intent =
                intentStore.selectByIdentity(TENANT_A, FinanceStockSagaType.CHECKOUT, sagaId);
        assertThat(intent.getFinalizationStatus())
                .isEqualTo(FinanceStockSagaIntentStore.FINALIZATION_STATUS_FINALIZED);
        assertThat(intent.getFinalizedAt()).isNotNull();
    }

    // ==================== Loser: session CAS = 0 ====================

    @Test
    void finalizeSaga_casZeroWithoutCommittedIntent_throws_noSideEffects() {
        long cartId = insertCart(TENANT_A, "CHECKOUT");
        long sagaId = insertSession(TENANT_A, cartId, "INITIATED", 100L);
        intentStore.createOrGet(abandonedIntent(TENANT_A, sagaId, cartId));

        // Another worker already terminalized the session (CAS target hit).
        CheckoutSessionDO session = sessionMapper.selectByIdTenant(TENANT_A, sagaId);
        session.setStatus("ABANDONED");
        sessionMapper.updateById(session);

        assertThatThrownBy(() ->
                finalizer.finalizeSaga(TENANT_A, FinanceStockSagaType.CHECKOUT, sagaId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without a committed matching finalization");

        // No side effects from the loser.
        assertThat(eventLogMapper.selectList(null)).isEmpty();
        CartDO cart = cartMapper.selectById(cartId);
        assertThat(cart.getStatus()).isEqualTo("CHECKOUT");
        assertThat(cart.getVersion()).isEqualTo(0);

        FinanceStockSagaIntentDO intent =
                intentStore.selectByIdentity(TENANT_A, FinanceStockSagaType.CHECKOUT, sagaId);
        assertThat(intent.getFinalizationStatus())
                .isEqualTo(FinanceStockSagaIntentStore.FINALIZATION_STATUS_PENDING);
        assertThat(intent.getFinalizedAt()).isNull();
    }

    @Test
    void finalizeSaga_sessionCasAbnormalCount_throwsAndRollsBack() {
        setupHappyPath();
        doReturn(2).when(sessionMapper).casTerminalStatus(
                eq(TENANT_A), eq(100L), any(String.class), any(String.class),
                any(String.class), any(LocalDateTime.class));

        assertThatThrownBy(() ->
                finalizer.finalizeSaga(TENANT_A, FinanceStockSagaType.CHECKOUT, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one row");

        assertAllWritesRolledBack(100L);
    }

    // ==================== Already finalized ====================

    @Test
    void finalizeSaga_intentAlreadyFinalized_returnsFalse() {
        long cartId = insertCart(TENANT_A, "CHECKOUT");
        long sagaId = insertSession(TENANT_A, cartId, "INITIATED", 100L);
        intentStore.createOrGet(abandonedIntent(TENANT_A, sagaId, cartId));

        assertThat(finalizer.finalizeSaga(TENANT_A, FinanceStockSagaType.CHECKOUT, sagaId))
                .isTrue();
        assertThat(finalizer.finalizeSaga(TENANT_A, FinanceStockSagaType.CHECKOUT, sagaId))
                .isFalse();

        // Exactly one event, one version increment.
        assertThat(eventLogMapper.selectList(null)).hasSize(1);
        CartDO cart = cartMapper.selectById(cartId);
        assertThat(cart.getVersion()).isEqualTo(1);
    }

    @Test
    void finalizeSaga_unlockZero_throwsAndRollsBackSession() {
        long cartId = insertCart(TENANT_A, "ACTIVE");
        long sagaId = insertSession(TENANT_A, cartId, "INITIATED", 100L);
        intentStore.createOrGet(abandonedIntent(TENANT_A, sagaId, cartId));

        assertThatThrownBy(() ->
                finalizer.finalizeSaga(TENANT_A, FinanceStockSagaType.CHECKOUT, sagaId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unlock exactly one");

        assertThat(sessionMapper.selectByIdTenant(TENANT_A, sagaId).getStatus())
                .isEqualTo("INITIATED");
        assertThat(cartMapper.selectById(cartId).getStatus()).isEqualTo("ACTIVE");
        assertThat(eventLogMapper.selectList(null)).isEmpty();
        assertThat(intentStore.selectByIdentity(
                TENANT_A, FinanceStockSagaType.CHECKOUT, sagaId).getFinalizationStatus())
                .isEqualTo(FinanceStockSagaIntentStore.FINALIZATION_STATUS_PENDING);
    }

    @Test
    void finalizeSaga_eventInsertZero_throwsAndRollsBackAllWrites() {
        setupHappyPath();
        doReturn(0).when(eventLogMapper).insert(any(CartEventLogDO.class));

        assertThatThrownBy(() ->
                finalizer.finalizeSaga(TENANT_A, FinanceStockSagaType.CHECKOUT, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insert exactly one cart event");

        assertAllWritesRolledBack(100L);
    }

    @Test
    void finalizeSaga_intentCasZero_throwsAndRollsBackAllWrites() {
        setupHappyPath();
        doReturn(false).when(intentStore).finalizePending(
                eq(TENANT_A), eq(FinanceStockSagaType.CHECKOUT), eq(100L),
                any(LocalDateTime.class));

        assertThatThrownBy(() ->
                finalizer.finalizeSaga(TENANT_A, FinanceStockSagaType.CHECKOUT, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already finalized by concurrent worker");

        assertAllWritesRolledBack(100L);
    }

    // ==================== Fail-closed guards ====================

    @Test
    void finalizeSaga_nonCheckoutSagaType_throws() {
        assertThatThrownBy(() ->
                finalizer.finalizeSaga(TENANT_A, FinanceStockSagaType.REFUND, 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CHECKOUT");
    }

    @Test
    void finalizeSaga_intentMissing_throws() {
        long cartId = insertCart(TENANT_A, "CHECKOUT");
        insertSession(TENANT_A, cartId, "INITIATED", 100L);

        assertThatThrownBy(() ->
                finalizer.finalizeSaga(TENANT_A, FinanceStockSagaType.CHECKOUT, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No saga intent");
    }

    @Test
    void finalizeSaga_sessionMissing_throws() {
        long cartId = insertCart(TENANT_A, "CHECKOUT");
        intentStore.createOrGet(abandonedIntent(TENANT_A, 999L, cartId));

        assertThatThrownBy(() ->
                finalizer.finalizeSaga(TENANT_A, FinanceStockSagaType.CHECKOUT, 999L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checkout session");
    }

    @Test
    void finalizeSaga_intentCartMismatch_throws() {
        long cartId = insertCart(TENANT_A, "CHECKOUT");
        long sagaId = insertSession(TENANT_A, cartId, "INITIATED", 100L);
        // Intent frozen with a DIFFERENT cartId than the session.
        intentStore.createOrGet(abandonedIntent(TENANT_A, sagaId, cartId + 1L));

        assertThatThrownBy(() ->
                finalizer.finalizeSaga(TENANT_A, FinanceStockSagaType.CHECKOUT, sagaId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cartId");
    }

    @Test
    void finalizeSaga_unknownFinalizationStatus_throws() {
        long cartId = insertCart(TENANT_A, "CHECKOUT");
        long sagaId = insertSession(TENANT_A, cartId, "INITIATED", 100L);
        FinanceStockSagaIntentDO intent =
                intentStore.createOrGet(abandonedIntent(TENANT_A, sagaId, cartId));
        intent.setFinalizationStatus("WEIRD");
        intentMapper.updateById(intent);

        assertThatThrownBy(() ->
                finalizer.finalizeSaga(TENANT_A, FinanceStockSagaType.CHECKOUT, sagaId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("finalizationStatus");
    }

    // ==================== Tenant guard ====================

    @Test
    void finalizeSaga_tenantContextMismatch_throwsBeforeSql() {
        TenantContextHolder.setTenantId(TENANT_B);
        assertThatThrownBy(() ->
                finalizer.finalizeSaga(TENANT_A, FinanceStockSagaType.CHECKOUT, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context mismatch");
    }

    // ==================== Real two-thread winner/loser race ====================

    @Test
    void finalizeSaga_twoWorkersConcurrent_exactlyOneWinner() throws Exception {
        long cartId = insertCart(TENANT_A, "CHECKOUT");
        long sagaId = insertSession(TENANT_A, cartId, "INITIATED", 100L);
        intentStore.createOrGet(abandonedIntent(TENANT_A, sagaId, cartId));

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(2);
        AtomicBoolean result1 = new AtomicBoolean(false);
        AtomicBoolean result2 = new AtomicBoolean(false);
        AtomicReference<Throwable> error1 = new AtomicReference<>(null);
        AtomicReference<Throwable> error2 = new AtomicReference<>(null);

        Thread t1 = new Thread(withTenant(TENANT_A, () -> {
            try {
                startGate.await();
                result1.set(finalizer.finalizeSaga(
                        TENANT_A, FinanceStockSagaType.CHECKOUT, sagaId));
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                finishGate.countDown();
            }
        }));
        Thread t2 = new Thread(withTenant(TENANT_A, () -> {
            try {
                startGate.await();
                result2.set(finalizer.finalizeSaga(
                        TENANT_A, FinanceStockSagaType.CHECKOUT, sagaId));
            } catch (Throwable t) {
                error2.set(t);
            } finally {
                finishGate.countDown();
            }
        }));

        t1.start();
        t2.start();
        startGate.countDown();
        assertThat(finishGate.await(15, TimeUnit.SECONDS))
                .as("both finalizer threads must finish").isTrue();
        t1.join(1000);
        t2.join(1000);

        assertThat(error1.get()).as("thread 1 must not throw").isNull();
        assertThat(error2.get()).as("thread 2 must not throw").isNull();

        assertThat(result1.get() ^ result2.get())
                .as("exactly one worker must win").isTrue();

        // Exactly one event, one version increment.
        assertThat(eventLogMapper.selectList(null)).hasSize(1);
        CartDO cart = cartMapper.selectById(cartId);
        assertThat(cart.getStatus()).isEqualTo("ACTIVE");
        assertThat(cart.getVersion()).isEqualTo(1);

        CheckoutSessionDO session = sessionMapper.selectByIdTenant(TENANT_A, sagaId);
        assertThat(session.getStatus()).isEqualTo("ABANDONED");

        FinanceStockSagaIntentDO intent =
                intentStore.selectByIdentity(TENANT_A, FinanceStockSagaType.CHECKOUT, sagaId);
        assertThat(intent.getFinalizationStatus())
                .isEqualTo(FinanceStockSagaIntentStore.FINALIZATION_STATUS_FINALIZED);
    }

    private void assertAllWritesRolledBack(long sagaId) {
        CheckoutSessionDO session = sessionMapper.selectByIdTenant(TENANT_A, sagaId);
        assertThat(session.getStatus()).isEqualTo("INITIATED");

        CartDO cart = cartMapper.selectById(session.getCartId());
        assertThat(cart.getStatus()).isEqualTo("CHECKOUT");
        assertThat(cart.getVersion()).isEqualTo(0);
        assertThat(eventLogMapper.selectList(null)).isEmpty();

        FinanceStockSagaIntentDO intent = intentStore.selectByIdentity(
                TENANT_A, FinanceStockSagaType.CHECKOUT, sagaId);
        assertThat(intent.getFinalizationStatus())
                .isEqualTo(FinanceStockSagaIntentStore.FINALIZATION_STATUS_PENDING);
        assertThat(intent.getFinalizedAt()).isNull();
    }

    // ==================== Concurrency helper ====================

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
}
