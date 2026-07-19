package com.geihou.module.finance.stock.saga;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.checkout.CheckoutTestSchemaInitializer;
import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockCommandDO;
import com.geihou.module.finance.stock.saga.dal.mapper.FinanceStockCommandMapper;
import com.geihou.module.finance.stock.saga.enums.FinanceStockCommandStatus;
import com.geihou.module.finance.stock.saga.enums.FinanceStockSagaType;
import com.geihou.module.finance.stock.saga.enums.FinanceStockTransportMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link FinanceStockReserveCompletionServiceImpl}
 * covering the 10-item matrix from
 * TASK-G0-04H185-FIN-CONSISTENCY-SLICE-2A-ATOMIC-RESERVE-RELEASE.
 *
 * <p>Each test method runs without its own {@code @Transactional} so that
 * each service call executes in its own transaction. When a service call
 * throws, its transaction rolls back fully (including the parent CAS), and
 * the test's subsequent mapper reads (auto-commit) observe the rolled-back
 * state.
 *
 * <p>Concurrency tests use {@link CountDownLatch} barriers - no
 * {@code Thread.sleep} timing guesses.
 */
@SpringBootTest(
        classes = FinanceStockCommandTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fsc_completion_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class FinanceStockReserveCompletionServiceTest {

    @Autowired private FinanceStockReserveCompletionService service;
    @Autowired private FinanceStockCommandStore store;
    @Autowired private FinanceStockCommandMapper mapper;
    @Autowired private DataSource dataSource;

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;
    private static final byte[] BODY_A = "{\"op\":\"reserve\",\"qty\":1}"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] BODY_RESULT = "{\"ok\":true}"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] BODY_RELEASE = "{\"op\":\"release\"}"
            .getBytes(StandardCharsets.UTF_8);

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

    private String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private FinanceStockCommandCreate buildReserveCreate(long tenantId, String cmdId, byte[] body) {
        return new FinanceStockCommandCreate(
                tenantId,
                FinanceStockSagaType.CHECKOUT,
                100L,
                "step-reserve-" + cmdId,
                null,
                "RESERVE",
                cmdId,
                FinanceStockTransportMode.LOCAL_API_V1,
                false,
                1,
                body,
                sha256Hex(body),
                3,
                3
        );
    }

    private FinanceStockCommandCreate buildReleaseCreate(FinanceStockCommandDO parent, byte[] body) {
        return new FinanceStockCommandCreate(
                parent.getTenantId(),
                FinanceStockSagaType.valueOf(parent.getSagaType()),
                parent.getSagaId(),
                parent.getStepKey(),
                parent.getId(),
                "RELEASE",
                parent.getBusinessCommandId(),
                FinanceStockTransportMode.valueOf(parent.getTransportMode()),
                parent.getC0JournalAvailable(),
                1,
                body,
                sha256Hex(body),
                3,
                3
        );
    }

    private long createPendingReserve(long tenantId, String cmdId) {
        FinanceStockCommandDO DO = store.createOrGet(buildReserveCreate(tenantId, cmdId, BODY_A));
        assertThat(DO.getStatus()).isEqualTo("PENDING");
        return DO.getId();
    }

    private void claimToInFlight(long tenantId, long id, String token) {
        LocalDateTime now = LocalDateTime.now();
        boolean claimed = store.claimDispatch(tenantId, id, token, now, now.plusMinutes(5));
        assertThat(claimed).as("claim should succeed").isTrue();
    }

    private void transitionToUnknownAndClaimResolution(long tenantId, long id,
                                                       String dispatchToken,
                                                       String resolutionToken) {
        // Truncate to millis: H2 DATETIME(3) stores millisecond precision, so
        // a nanosecond `now` would be rounded by the DB and break the
        // `next_attempt_at <= now` comparison in claimResolution.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        // nextAttemptAt = now so claimResolution's "next_attempt_at <= now" passes
        boolean marked = store.markUnknown(tenantId, id, dispatchToken,
                now, 0, "Timeout", "no response", now);
        assertThat(marked).as("markUnknown should succeed").isTrue();
        // Verify nextAttemptAt == now persists at millisecond precision (CI-FIX
        // regression guard for the time-precision race that broke
        // claimResolution's `next_attempt_at <= now` predicate).
        FinanceStockCommandDO afterMark = mapper.selectById(id);
        assertThat(afterMark.getNextAttemptAt())
                .as("persisted nextAttemptAt must equal `now` at millisecond precision")
                .isEqualTo(now);
        boolean claimed = store.claimResolution(tenantId, id, resolutionToken,
                now, now.plusMinutes(5));
        assertThat(claimed).as("claimResolution should succeed").isTrue();
    }

    private void requestAbort(long tenantId, long id) {
        boolean aborted = store.requestAbort(tenantId, id, LocalDateTime.now());
        assertThat(aborted).as("requestAbort should succeed").isTrue();
    }

    private long directInsertRelease(long tenantId, String businessCommandId,
                                     long sagaId, String stepKey, byte[] body) {
        return directInsertReleaseWithParent(tenantId, businessCommandId,
                sagaId, stepKey, null, body);
    }

    private long directInsertReleaseWithParent(long tenantId, String businessCommandId,
                                               long sagaId, String stepKey,
                                               Long parentCommandId, byte[] body) {
        FinanceStockCommandDO DO = new FinanceStockCommandDO();
        DO.setTenantId(tenantId);
        DO.setSagaType(FinanceStockSagaType.CHECKOUT.name());
        DO.setSagaId(sagaId);
        DO.setStepKey(stepKey);
        DO.setParentCommandId(parentCommandId);
        DO.setOperation("RELEASE");
        DO.setBusinessCommandId(businessCommandId);
        DO.setTransportMode(FinanceStockTransportMode.LOCAL_API_V1.name());
        DO.setC0JournalAvailable(false);
        DO.setRequestSchemaVersion(1);
        DO.setRequestBody(body);
        DO.setRequestBodySha256(sha256Hex(body));
        DO.setStatus(FinanceStockCommandStatus.PENDING.name());
        DO.setAbortRequested(false);
        DO.setDispatchAttempts(0);
        DO.setResolutionAttempts(0);
        DO.setMaxDispatchAttempts(3);
        DO.setMaxResolutionAttempts(3);
        DO.setCreateTime(LocalDateTime.now());
        DO.setUpdateTime(LocalDateTime.now());
        mapper.insert(DO);
        return DO.getId();
    }

    private long directInsertNonReserve(long tenantId, String cmdId, String operation) {
        FinanceStockCommandDO DO = new FinanceStockCommandDO();
        DO.setTenantId(tenantId);
        DO.setSagaType(FinanceStockSagaType.CHECKOUT.name());
        DO.setSagaId(200L);
        DO.setStepKey("step-" + operation + "-" + cmdId);
        DO.setParentCommandId(null);
        DO.setOperation(operation);
        DO.setBusinessCommandId(cmdId);
        DO.setTransportMode(FinanceStockTransportMode.LOCAL_API_V1.name());
        DO.setC0JournalAvailable(false);
        DO.setRequestSchemaVersion(1);
        DO.setRequestBody(BODY_A);
        DO.setRequestBodySha256(sha256Hex(BODY_A));
        DO.setStatus(FinanceStockCommandStatus.IN_FLIGHT.name());
        DO.setAbortRequested(false);
        DO.setDispatchAttempts(1);
        DO.setResolutionAttempts(0);
        DO.setMaxDispatchAttempts(3);
        DO.setMaxResolutionAttempts(3);
        DO.setClaimToken("token-nonreserve");
        DO.setLeaseUntil(LocalDateTime.now().plusMinutes(5));
        DO.setCreateTime(LocalDateTime.now());
        DO.setUpdateTime(LocalDateTime.now());
        mapper.insert(DO);
        return DO.getId();
    }

    private long countReleaseCommands(long tenantId, String businessCommandId) {
        // H2 doesn't have a dedicated count mapper; use selectByRemoteIdentity
        // which returns the RELEASE if it exists.
        FinanceStockCommandDO release = mapper.selectByRemoteIdentity(
                tenantId, "RELEASE", businessCommandId);
        return release == null ? 0 : 1;
    }

    // ==================== Test 1: dispatch success, no abort ====================

    @Test
    void completeFromDispatch_successNoAbort_succeedsNoRelease() {
        long id = createPendingReserve(TENANT_A, "cmd-1");
        claimToInFlight(TENANT_A, id, "token-1");
        FinanceStockCommandDO parent = mapper.selectByIdTenant(TENANT_A, id);
        FinanceStockCommandCreate release = buildReleaseCreate(parent, BODY_RELEASE);

        LocalDateTime executedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        boolean result = service.completeFromDispatch(TENANT_A, id, "token-1",
                BODY_RESULT, 1, executedAt, executedAt, release);

        assertThat(result).isTrue();
        FinanceStockCommandDO updated = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(updated.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(updated.getAbortRequested()).isFalse();
        assertThat(updated.getClaimToken()).isNull();
        assertThat(updated.getLeaseUntil()).isNull();
        assertThat(updated.getResultBody()).isEqualTo(BODY_RESULT);
        assertThat(countReleaseCommands(TENANT_A, "cmd-1")).isZero();
    }

    // ==================== Test 2: dispatch success with abort, RELEASE created ====================

    @Test
    void completeFromDispatch_successWithAbort_createsRelease() {
        long id = createPendingReserve(TENANT_A, "cmd-2");
        claimToInFlight(TENANT_A, id, "token-2");
        requestAbort(TENANT_A, id);
        FinanceStockCommandDO parent = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(parent.getAbortRequested()).isTrue();
        FinanceStockCommandCreate release = buildReleaseCreate(parent, BODY_RELEASE);

        LocalDateTime executedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        boolean result = service.completeFromDispatch(TENANT_A, id, "token-2",
                BODY_RESULT, 1, executedAt, executedAt, release);

        assertThat(result).isTrue();
        FinanceStockCommandDO updated = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(updated.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(updated.getAbortRequested()).isTrue();
        assertThat(countReleaseCommands(TENANT_A, "cmd-2")).isEqualTo(1);
    }

    // ==================== Test 3: UNKNOWN resolution success with abort, RELEASE created ====================

    @Test
    void completeFromResolution_successWithAbort_createsRelease() {
        long id = createPendingReserve(TENANT_A, "cmd-3");
        claimToInFlight(TENANT_A, id, "token-dispatch-3");
        transitionToUnknownAndClaimResolution(TENANT_A, id, "token-dispatch-3", "token-resolution-3");
        requestAbort(TENANT_A, id);
        FinanceStockCommandDO parent = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(parent.getStatus()).isEqualTo("UNKNOWN");
        assertThat(parent.getAbortRequested()).isTrue();
        FinanceStockCommandCreate release = buildReleaseCreate(parent, BODY_RELEASE);

        LocalDateTime executedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        boolean result = service.completeFromResolution(TENANT_A, id, "token-resolution-3",
                BODY_RESULT, 1, executedAt, executedAt, release);

        assertThat(result).isTrue();
        FinanceStockCommandDO updated = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(updated.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(updated.getAbortRequested()).isTrue();
        assertThat(countReleaseCommands(TENANT_A, "cmd-3")).isEqualTo(1);
    }

    // ==================== Test 4: RELEASE identity matches parent ====================

    @Test
    void releaseChild_hasCorrectIdentity() {
        long id = createPendingReserve(TENANT_A, "cmd-4");
        claimToInFlight(TENANT_A, id, "token-4");
        requestAbort(TENANT_A, id);
        FinanceStockCommandDO parent = mapper.selectByIdTenant(TENANT_A, id);
        FinanceStockCommandCreate release = buildReleaseCreate(parent, BODY_RELEASE);

        LocalDateTime executedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        boolean result = service.completeFromDispatch(TENANT_A, id, "token-4",
                BODY_RESULT, 1, executedAt, executedAt, release);

        assertThat(result).isTrue();
        FinanceStockCommandDO releaseDO = mapper.selectByRemoteIdentity(
                TENANT_A, "RELEASE", "cmd-4");
        assertThat(releaseDO).isNotNull();
        assertThat(releaseDO.getTenantId()).isEqualTo(parent.getTenantId());
        assertThat(releaseDO.getSagaType()).isEqualTo(parent.getSagaType());
        assertThat(releaseDO.getSagaId()).isEqualTo(parent.getSagaId());
        assertThat(releaseDO.getStepKey()).isEqualTo(parent.getStepKey());
        assertThat(releaseDO.getOperation()).isEqualTo("RELEASE");
        assertThat(releaseDO.getParentCommandId()).isEqualTo(parent.getId());
        assertThat(releaseDO.getBusinessCommandId()).isEqualTo(parent.getBusinessCommandId());
        assertThat(releaseDO.getTransportMode()).isEqualTo(parent.getTransportMode());
        assertThat(releaseDO.getC0JournalAvailable()).isEqualTo(parent.getC0JournalAvailable());
        assertThat(releaseDO.getStatus()).isEqualTo("PENDING");
    }

    // ==================== Test 5: stale claim token returns false ====================

    @Test
    void completeFromDispatch_staleClaimToken_returnsFalseNoRelease() {
        long id = createPendingReserve(TENANT_A, "cmd-5");
        claimToInFlight(TENANT_A, id, "token-5");
        FinanceStockCommandDO parent = mapper.selectByIdTenant(TENANT_A, id);
        FinanceStockCommandCreate release = buildReleaseCreate(parent, BODY_RELEASE);

        LocalDateTime executedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        boolean result = service.completeFromDispatch(TENANT_A, id, "stale-token",
                BODY_RESULT, 1, executedAt, executedAt, release);

        assertThat(result).isFalse();
        FinanceStockCommandDO unchanged = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(unchanged.getStatus()).isEqualTo("IN_FLIGHT");
        assertThat(unchanged.getClaimToken()).isEqualTo("token-5");
        assertThat(countReleaseCommands(TENANT_A, "cmd-5")).isZero();
    }

    // ==================== Test 6: tenant mismatch, no update or create ====================

    @Test
    void completeFromDispatch_tenantMismatch_returnsFalseNoRelease() {
        long id = createPendingReserve(TENANT_A, "cmd-6");
        claimToInFlight(TENANT_A, id, "token-6");
        FinanceStockCommandDO parent = mapper.selectByIdTenant(TENANT_A, id);
        FinanceStockCommandCreate release = buildReleaseCreate(parent, BODY_RELEASE);

        LocalDateTime executedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        // Call with TENANT_B - no such command in that tenant
        boolean result = service.completeFromDispatch(TENANT_B, id, "token-6",
                BODY_RESULT, 1, executedAt, executedAt, release);

        assertThat(result).isFalse();
        FinanceStockCommandDO unchanged = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(unchanged.getStatus()).isEqualTo("IN_FLIGHT");
        assertThat(countReleaseCommands(TENANT_A, "cmd-6")).isZero();
        assertThat(countReleaseCommands(TENANT_B, "cmd-6")).isZero();
    }

    // ==================== Test 7: non-RESERVE parent rejected ====================

    @Test
    void completeFromDispatch_nonReserveParent_throwsNoSideEffect() {
        long id = directInsertNonReserve(TENANT_A, "cmd-7", "COMMIT");
        FinanceStockCommandDO parent = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(parent.getOperation()).isEqualTo("COMMIT");
        FinanceStockCommandCreate release = buildReleaseCreate(parent, BODY_RELEASE);

        LocalDateTime executedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        assertThatThrownBy(() -> service.completeFromDispatch(TENANT_A, id, "token-nonreserve",
                BODY_RESULT, 1, executedAt, executedAt, release))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not RESERVE");

        FinanceStockCommandDO unchanged = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(unchanged.getStatus()).isEqualTo("IN_FLIGHT");
        assertThat(unchanged.getClaimToken()).isEqualTo("token-nonreserve");
    }

    // ==================== Test 8: repeat call, at most one RELEASE ====================

    @Test
    void completeFromDispatch_repeatCall_atMostOneRelease() {
        long id = createPendingReserve(TENANT_A, "cmd-8");
        claimToInFlight(TENANT_A, id, "token-8");
        requestAbort(TENANT_A, id);
        FinanceStockCommandDO parent = mapper.selectByIdTenant(TENANT_A, id);
        FinanceStockCommandCreate release = buildReleaseCreate(parent, BODY_RELEASE);

        LocalDateTime executedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        boolean first = service.completeFromDispatch(TENANT_A, id, "token-8",
                BODY_RESULT, 1, executedAt, executedAt, release);
        boolean second = service.completeFromDispatch(TENANT_A, id, "token-8",
                BODY_RESULT, 1, executedAt, executedAt, release);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(countReleaseCommands(TENANT_A, "cmd-8")).isEqualTo(1);
        FinanceStockCommandDO updated = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(updated.getStatus()).isEqualTo("SUCCEEDED");
    }

    // ==================== Test 9: concurrent same-claim, one winner ====================

    @Test
    void completeFromDispatch_concurrentSameClaim_oneWinnerOneRelease() throws InterruptedException {
        long id = createPendingReserve(TENANT_A, "cmd-9");
        claimToInFlight(TENANT_A, id, "token-9");
        requestAbort(TENANT_A, id);
        FinanceStockCommandDO parent = mapper.selectByIdTenant(TENANT_A, id);
        FinanceStockCommandCreate release = buildReleaseCreate(parent, BODY_RELEASE);

        LocalDateTime executedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(2);
        AtomicBoolean result1 = new AtomicBoolean(false);
        AtomicBoolean result2 = new AtomicBoolean(false);
        AtomicReference<Throwable> error1 = new AtomicReference<>(null);
        AtomicReference<Throwable> error2 = new AtomicReference<>(null);

        Thread t1 = new Thread(withTenant(TENANT_A, () -> {
            try {
                startGate.await();
                boolean r = service.completeFromDispatch(TENANT_A, id, "token-9",
                        BODY_RESULT, 1, executedAt, executedAt, release);
                result1.set(r);
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                finishGate.countDown();
            }
        }));
        Thread t2 = new Thread(withTenant(TENANT_A, () -> {
            try {
                startGate.await();
                boolean r = service.completeFromDispatch(TENANT_A, id, "token-9",
                        BODY_RESULT, 1, executedAt, executedAt, release);
                result2.set(r);
            } catch (Throwable t) {
                error2.set(t);
            } finally {
                finishGate.countDown();
            }
        }));

        t1.start();
        t2.start();
        startGate.countDown();
        assertThat(finishGate.await(10, TimeUnit.SECONDS))
                .as("both threads must finish").isTrue();
        t1.join(1000);
        t2.join(1000);

        assertThat(error1.get()).as("thread 1 must not throw").isNull();
        assertThat(error2.get()).as("thread 2 must not throw").isNull();

        // Exactly one CAS winner
        assertThat(result1.get() && !result2.get()
                || !result1.get() && result2.get())
                .as("exactly one thread must win the CAS").isTrue();

        assertThat(countReleaseCommands(TENANT_A, "cmd-9"))
                .as("exactly one RELEASE must exist").isEqualTo(1);
        FinanceStockCommandDO updated = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(updated.getStatus()).isEqualTo("SUCCEEDED");
    }

    // ==================== Test 10: incompatible pre-existing RELEASE rolls back parent ====================

    @Test
    void completeFromDispatch_incompatibleReleaseExists_throwsAndRollsBack() {
        long id = createPendingReserve(TENANT_A, "cmd-10");
        claimToInFlight(TENANT_A, id, "token-10");
        requestAbort(TENANT_A, id);
        FinanceStockCommandDO parent = mapper.selectByIdTenant(TENANT_A, id);

        // Pre-insert an incompatible RELEASE: same remote identity
        // (operation=RELEASE, businessCommandId=cmd-10) but different sagaId/stepKey.
        directInsertRelease(TENANT_A, "cmd-10", 999L, "step-incompatible", BODY_RELEASE);

        FinanceStockCommandCreate release = buildReleaseCreate(parent, BODY_RELEASE);

        LocalDateTime executedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        assertThatThrownBy(() -> service.completeFromDispatch(TENANT_A, id, "token-10",
                BODY_RESULT, 1, executedAt, executedAt, release))
                .isInstanceOf(IllegalStateException.class);

        // Parent must roll back to IN_FLIGHT (CAS undone)
        FinanceStockCommandDO rolled = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(rolled.getStatus()).isEqualTo("IN_FLIGHT");
        assertThat(rolled.getClaimToken()).isEqualTo("token-10");
        assertThat(rolled.getResultBody()).isNull();
        // Only the pre-existing incompatible RELEASE exists; no new one
        FinanceStockCommandDO releaseDO = mapper.selectByRemoteIdentity(
                TENANT_A, "RELEASE", "cmd-10");
        assertThat(releaseDO).isNotNull();
        assertThat(releaseDO.getSagaId()).isEqualTo(999L);
    }

    // ==================== Test 11: pre-existing RELEASE with wrong parentCommandId rolls back parent ====================

    @Test
    void completeFromDispatch_releaseWithWrongParentId_throwsAndRollsBack() {
        long id = createPendingReserve(TENANT_A, "cmd-11");
        claimToInFlight(TENANT_A, id, "token-11");
        requestAbort(TENANT_A, id);
        FinanceStockCommandDO parent = mapper.selectByIdTenant(TENANT_A, id);

        // Pre-insert a RELEASE with same identity and same payload but WRONG
        // parentCommandId. All other identity fields match the releaseCommand
        // that completeFromDispatch will build, so createOrGet finds this
        // record by remote identity and verifyIdentity must reject on
        // parentCommandId mismatch.
        directInsertReleaseWithParent(TENANT_A, "cmd-11",
                parent.getSagaId(), parent.getStepKey(),
                999999L, BODY_RELEASE);

        FinanceStockCommandCreate release = buildReleaseCreate(parent, BODY_RELEASE);

        LocalDateTime executedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        assertThatThrownBy(() -> service.completeFromDispatch(TENANT_A, id, "token-11",
                BODY_RESULT, 1, executedAt, executedAt, release))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("parentCommandId");

        // Parent must roll back to IN_FLIGHT (CAS undone)
        FinanceStockCommandDO rolled = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(rolled.getStatus()).isEqualTo("IN_FLIGHT");
        assertThat(rolled.getClaimToken()).isEqualTo("token-11");
        assertThat(rolled.getResultBody()).isNull();

        // Only the pre-existing wrong-parent RELEASE exists; no correct one
        FinanceStockCommandDO releaseDO = mapper.selectByRemoteIdentity(
                TENANT_A, "RELEASE", "cmd-11");
        assertThat(releaseDO).isNotNull();
        assertThat(releaseDO.getParentCommandId()).isEqualTo(999999L);
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
