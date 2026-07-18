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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link FinanceStockCommandStoreImpl} covering the 19-item
 * test matrix from TASK-G0-04H185-FIN-CONSISTENCY-DURABLE-COMMAND-STORE.
 *
 * <p>Items 1 and 19 (DDL/DO/H2 consistency, enum round-trip) are covered by
 * {@link FinanceStockCommandDdlConsistencyTest}. This class covers items 2-18:
 * create/identity, claim fencing, state transitions, lease expiry, UNKNOWN
 * resolution, abort, cross-tenant isolation, and concurrency.
 *
 * <p>Concurrency tests use {@link CountDownLatch} barriers - no
 * {@code Thread.sleep} timing guesses.
 */
@SpringBootTest(
        classes = FinanceStockCommandTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fsc_store_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class FinanceStockCommandStoreTest {

    @Autowired private FinanceStockCommandStore store;
    @Autowired private FinanceStockCommandMapper mapper;
    @Autowired private DataSource dataSource;

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;
    private static final byte[] BODY_A = "{\"op\":\"reserve\",\"qty\":1}"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] BODY_B = "{\"op\":\"reserve\",\"qty\":2}"
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
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private FinanceStockCommandCreate buildCreate(long tenantId, String cmdId, byte[] body) {
        return new FinanceStockCommandCreate(
                tenantId,
                FinanceStockSagaType.CHECKOUT,
                100L,
                "step-" + cmdId,
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

    private FinanceStockCommandCreate buildCreateWithSaga(
            long tenantId, String cmdId, byte[] body,
            long sagaId, String stepKey) {
        return new FinanceStockCommandCreate(
                tenantId,
                FinanceStockSagaType.CHECKOUT,
                sagaId,
                stepKey,
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

    private FinanceStockCommandCreate buildCreateWithParent(
            long tenantId, String cmdId, byte[] body, Long parentCommandId) {
        return new FinanceStockCommandCreate(
                tenantId,
                FinanceStockSagaType.CHECKOUT,
                100L,
                "step-" + cmdId,
                parentCommandId,
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

    private long createPending(long tenantId, String cmdId) {
        FinanceStockCommandDO DO = store.createOrGet(buildCreate(tenantId, cmdId, BODY_A));
        assertThat(DO.getStatus()).isEqualTo("PENDING");
        return DO.getId();
    }

    private long claimToInFlight(long tenantId, long id, String token) {
        LocalDateTime now = LocalDateTime.now();
        boolean claimed = store.claimDispatch(tenantId, id, token, now, now.plusMinutes(5));
        assertThat(claimed).as("claim should succeed").isTrue();
        return id;
    }

    // ==================== Item 2: create defaults PENDING, byte[] defensive copy ====================

    @Test
    void create_defaultsToPendingAndDefensiveCopy() {
        byte[] originalBody = BODY_A.clone();
        FinanceStockCommandCreate create = buildCreate(TENANT_A, "cmd-defensive", originalBody);
        FinanceStockCommandDO DO = store.createOrGet(create);

        assertThat(DO.getStatus()).isEqualTo("PENDING");
        assertThat(DO.getDispatchAttempts()).isEqualTo(0);
        assertThat(DO.getResolutionAttempts()).isEqualTo(0);
        assertThat(DO.getAbortRequested()).isFalse();
        assertThat(DO.getClaimToken()).isNull();
        assertThat(DO.getLeaseUntil()).isNull();
        assertThat(DO.getMaxDispatchAttempts()).isEqualTo(3);
        assertThat(DO.getMaxResolutionAttempts()).isEqualTo(3);

        // Getter returns defensive copy: mutating it must not affect DO
        byte[] gotOnce = DO.getRequestBody();
        gotOnce[0] = (byte) 'X';
        byte[] gotAgain = DO.getRequestBody();
        assertThat(gotAgain).isEqualTo(originalBody);

        // Setter stores defensive copy: mutating source after set must not affect DO
        byte[] sourceForSetter = BODY_B.clone();
        DO.setRequestBody(sourceForSetter);
        sourceForSetter[0] = (byte) 'Y';
        assertThat(DO.getRequestBody()).isEqualTo(BODY_B);

        // Create record accessor also defensive-copies
        byte[] fromRecord = create.requestBody();
        fromRecord[0] = (byte) 'Z';
        assertThat(create.requestBody()).isEqualTo(originalBody);
    }

    // ==================== Item 3: createOrGet same identity same body returns existing ====================

    @Test
    void createOrGet_sameIdentitySameBody_returnsExisting() {
        FinanceStockCommandCreate create = buildCreate(TENANT_A, "cmd-same", BODY_A);
        FinanceStockCommandDO first = store.createOrGet(create);
        FinanceStockCommandDO second = store.createOrGet(create);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getRequestBodySha256()).isEqualTo(first.getRequestBodySha256());
    }

    // ==================== Item 4: remote identity same key different hash conflict ====================

    @Test
    void createOrGet_remoteIdentityDifferentHash_throwsConflict() {
        store.createOrGet(buildCreate(TENANT_A, "cmd-conflict-remote", BODY_A));

        // Same (tenant, operation, businessCommandId) but different body + hash
        assertThatThrownBy(() -> store.createOrGet(buildCreate(TENANT_A, "cmd-conflict-remote", BODY_B)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ==================== Item 5: local identity same step different command conflict ====================

    @Test
    void createOrGet_localIdentitySameStepDifferentCommand_throwsConflict() {
        // First: (sagaId=200, stepKey="shared-step", cmdId="cmd-A")
        store.createOrGet(buildCreateWithSaga(
                TENANT_A, "cmd-A", BODY_A, 200L, "shared-step"));

        // Second: same (sagaId=200, stepKey="shared-step") but cmdId="cmd-B"
        assertThatThrownBy(() -> store.createOrGet(buildCreateWithSaga(
                TENANT_A, "cmd-B", BODY_A, 200L, "shared-step")))
                .isInstanceOf(IllegalStateException.class);
    }

    // ==================== Item 6: LOCAL_API_V1 + c0=true rejected ====================

    @Test
    void createOrGet_localApiV1WithC0True_rejected() {
        FinanceStockCommandCreate bad = new FinanceStockCommandCreate(
                TENANT_A,
                FinanceStockSagaType.CHECKOUT,
                100L,
                "step-bad",
                null,
                "RESERVE",
                "cmd-bad-c0",
                FinanceStockTransportMode.LOCAL_API_V1,
                true,  // c0=true with LOCAL_API_V1 -> forbidden
                1,
                BODY_A,
                sha256Hex(BODY_A),
                3,
                3
        );
        assertThatThrownBy(() -> store.createOrGet(bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LOCAL_API_V1");
    }

    @Test
    void createOrGet_hmacRpcV1WithC0False_accepted() {
        FinanceStockCommandCreate ok = new FinanceStockCommandCreate(
                TENANT_A,
                FinanceStockSagaType.CHECKOUT,
                100L,
                "step-hmac",
                null,
                "RESERVE",
                "cmd-hmac",
                FinanceStockTransportMode.HMAC_RPC_V1,
                false,
                1,
                BODY_A,
                sha256Hex(BODY_A),
                3,
                3
        );
        FinanceStockCommandDO DO = store.createOrGet(ok);
        assertThat(DO.getStatus()).isEqualTo("PENDING");
        assertThat(DO.getTransportMode()).isEqualTo("HMAC_RPC_V1");
        assertThat(DO.getC0JournalAvailable()).isFalse();
    }

    // ==================== Item 7: two claim tokens compete, only one succeeds ====================

    @Test
    void claimDispatch_twoTokensConcurrent_onlyOneSucceeds() throws Exception {
        long id = createPending(TENANT_A, "cmd-concurrent-claim");
        LocalDateTime now = LocalDateTime.now();

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(2);
        AtomicBoolean result1 = new AtomicBoolean(false);
        AtomicBoolean result2 = new AtomicBoolean(false);
        AtomicReference<Throwable> error1 = new AtomicReference<>(null);
        AtomicReference<Throwable> error2 = new AtomicReference<>(null);

        Thread t1 = new Thread(withTenant(TENANT_A, () -> {
            try {
                startGate.await();
                result1.set(store.claimDispatch(TENANT_A, id, "token-1", now, now.plusMinutes(5)));
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                finishGate.countDown();
            }
        }));
        Thread t2 = new Thread(withTenant(TENANT_A, () -> {
            try {
                startGate.await();
                result2.set(store.claimDispatch(TENANT_A, id, "token-2", now, now.plusMinutes(5)));
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
                .as("both claim threads must finish").isTrue();
        t1.join(1000);
        t2.join(1000);

        assertThat(error1.get()).as("thread 1 must not throw").isNull();
        assertThat(error2.get()).as("thread 2 must not throw").isNull();

        assertThat(result1.get() ^ result2.get())
                .as("exactly one claim must succeed").isTrue();

        FinanceStockCommandDO DO = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(DO.getStatus()).isEqualTo("IN_FLIGHT");
        assertThat(DO.getClaimToken()).isIn("token-1", "token-2");
        assertThat(DO.getDispatchAttempts()).isEqualTo(1);
    }

    // ==================== Item 8: not-due RETRY_WAIT cannot claim, due can ====================

    @Test
    void claimDispatch_retryWaitNotDue_fails_dueSucceeds() {
        long id = createPending(TENANT_A, "cmd-retry-wait");
        String token = "token-retry";
        claimToInFlight(TENANT_A, id, token);

        LocalDateTime future = LocalDateTime.now().plusMinutes(10);
        boolean retried = store.scheduleRetry(TENANT_A, id, token, future,
                500, "RetryableError", "transient", LocalDateTime.now());
        assertThat(retried).isTrue();

        FinanceStockCommandDO afterRetry = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(afterRetry.getStatus()).isEqualTo("RETRY_WAIT");

        // Not due: now is before nextAttemptAt
        boolean claimBeforeDue = store.claimDispatch(TENANT_A, id, "token-2",
                future.minusMinutes(1), future.minusMinutes(1).plusMinutes(5));
        assertThat(claimBeforeDue).isFalse();

        // Due: now is at or after nextAttemptAt
        boolean claimAfterDue = store.claimDispatch(TENANT_A, id, "token-3",
                future.plusMinutes(1), future.plusMinutes(6));
        assertThat(claimAfterDue).isTrue();

        FinanceStockCommandDO afterClaim = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(afterClaim.getStatus()).isEqualTo("IN_FLIGHT");
        assertThat(afterClaim.getDispatchAttempts()).isEqualTo(2);
    }

    // ==================== Item 9: stale token cannot complete ====================

    @Test
    void completeSuccess_staleToken_fails() {
        long id = createPending(TENANT_A, "cmd-stale");
        claimToInFlight(TENANT_A, id, "real-token");

        boolean result = store.completeSuccess(TENANT_A, id, "stale-token",
                BODY_B, 1, LocalDateTime.now(), LocalDateTime.now());
        assertThat(result).isFalse();

        FinanceStockCommandDO DO = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(DO.getStatus()).isEqualTo("IN_FLIGHT");
        assertThat(DO.getClaimToken()).isEqualTo("real-token");
    }

    // ==================== Item 10: success saves result and clears lease ====================

    @Test
    void completeSuccess_savesResultAndClearsLease() {
        long id = createPending(TENANT_A, "cmd-success");
        String token = "token-success";
        claimToInFlight(TENANT_A, id, token);

        LocalDateTime executedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        boolean result = store.completeSuccess(TENANT_A, id, token,
                BODY_B, 1, executedAt, executedAt);
        assertThat(result).isTrue();

        FinanceStockCommandDO DO = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(DO.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(DO.getRequestBody()).isEqualTo(BODY_A);
        assertThat(DO.getResultBody()).isEqualTo(BODY_B);
        assertThat(DO.getResultSchemaVersion()).isEqualTo(1);
        assertThat(DO.getRemoteExecutedAt()).isEqualTo(executedAt);
        assertThat(DO.getResolvedAt()).isNotNull();
        assertThat(DO.getClaimToken()).isNull();
        assertThat(DO.getLeaseUntil()).isNull();
        assertThat(DO.getNextAttemptAt()).isNull();
        assertThat(DO.getLastErrorCode()).isNull();
    }

    // ==================== Item 11: retry enters RETRY_WAIT and can be claimed again ====================

    @Test
    void scheduleRetry_entersRetryWaitAndCanBeReclaimed() {
        long id = createPending(TENANT_A, "cmd-reclaim");
        String token = "token-reclaim";
        claimToInFlight(TENANT_A, id, token);

        LocalDateTime past = LocalDateTime.now().minusSeconds(1);
        boolean retried = store.scheduleRetry(TENANT_A, id, token, past,
                503, "ServiceUnavailable", "retry later", LocalDateTime.now());
        assertThat(retried).isTrue();

        FinanceStockCommandDO afterRetry = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(afterRetry.getStatus()).isEqualTo("RETRY_WAIT");
        assertThat(afterRetry.getClaimToken()).isNull();
        assertThat(afterRetry.getLeaseUntil()).isNull();
        assertThat(afterRetry.getLastErrorCode()).isEqualTo(503);

        // Reclaim with a new token
        boolean reclaimed = store.claimDispatch(TENANT_A, id, "token-reclaim-2",
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));
        assertThat(reclaimed).isTrue();

        FinanceStockCommandDO afterReclaim = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(afterReclaim.getStatus()).isEqualTo("IN_FLIGHT");
        assertThat(afterReclaim.getDispatchAttempts()).isEqualTo(2);
    }

    // ==================== Item 12: timeout enters UNKNOWN, not RETRY_WAIT ====================

    @Test
    void markUnknown_entersUnknownNotRetryWait() {
        long id = createPending(TENANT_A, "cmd-unknown");
        String token = "token-unknown";
        claimToInFlight(TENANT_A, id, token);

        LocalDateTime nextAttempt = LocalDateTime.now()
                .plusMinutes(1)
                .truncatedTo(ChronoUnit.MILLIS);
        boolean result = store.markUnknown(TENANT_A, id, token, nextAttempt,
                0, "SocketTimeout", "no response", LocalDateTime.now());
        assertThat(result).isTrue();

        FinanceStockCommandDO DO = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(DO.getStatus()).isEqualTo("UNKNOWN");
        assertThat(DO.getStatus()).isNotEqualTo("RETRY_WAIT");
        assertThat(DO.getClaimToken()).isNull();
        assertThat(DO.getLeaseUntil()).isNull();
        assertThat(DO.getNextAttemptAt()).isEqualTo(nextAttempt);
        assertThat(DO.getLastErrorClass()).isEqualTo("SocketTimeout");
    }

    // ==================== Item 13: expired IN_FLIGHT -> UNKNOWN, not PENDING ====================

    @Test
    void expireLeasesToUnknown_transitionsToUnknownNotPending() {
        long id = createPending(TENANT_A, "cmd-expired");
        // Claim with a lease in the past (leaseUntil must be > now per requireClaimArgs,
        // but both still in the past so expireLeasesToUnknown can fire)
        LocalDateTime past = LocalDateTime.now().minusMinutes(1);
        boolean claimed = store.claimDispatch(TENANT_A, id, "token-expired", past, past.plusSeconds(1));
        assertThat(claimed).isTrue();

        int expired = store.expireLeasesToUnknown(TENANT_A, LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(1));
        assertThat(expired).isGreaterThanOrEqualTo(1);

        FinanceStockCommandDO DO = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(DO.getStatus()).isEqualTo("UNKNOWN");
        assertThat(DO.getStatus()).isNotEqualTo("PENDING");
        assertThat(DO.getClaimToken()).isNull();
        assertThat(DO.getLeaseUntil()).isNull();

        // Cannot claim UNKNOWN via claimDispatch
        boolean reClaimed = store.claimDispatch(TENANT_A, id, "token-reclaim-3",
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));
        assertThat(reClaimed).isFalse();
    }

    // ==================== Item 14: UNKNOWN two resolvers compete, only one succeeds ====================

    @Test
    void claimResolution_twoResolversConcurrent_onlyOneSucceeds() throws Exception {
        long id = createPending(TENANT_A, "cmd-resolve-concurrent");
        String dispatchToken = "token-dispatch";
        claimToInFlight(TENANT_A, id, dispatchToken);

        // Move to UNKNOWN with nextAttemptAt in the past (immediately resolvable)
        LocalDateTime past = LocalDateTime.now().minusMinutes(1);
        store.markUnknown(TENANT_A, id, dispatchToken, past,
                0, "Timeout", "unknown result", LocalDateTime.now());

        LocalDateTime now = LocalDateTime.now();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(2);
        AtomicBoolean result1 = new AtomicBoolean(false);
        AtomicBoolean result2 = new AtomicBoolean(false);
        AtomicReference<Throwable> error1 = new AtomicReference<>(null);
        AtomicReference<Throwable> error2 = new AtomicReference<>(null);

        Thread t1 = new Thread(withTenant(TENANT_A, () -> {
            try {
                startGate.await();
                result1.set(store.claimResolution(TENANT_A, id, "resolve-1", now, now.plusMinutes(5)));
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                finishGate.countDown();
            }
        }));
        Thread t2 = new Thread(withTenant(TENANT_A, () -> {
            try {
                startGate.await();
                result2.set(store.claimResolution(TENANT_A, id, "resolve-2", now, now.plusMinutes(5)));
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
                .as("both resolution threads must finish").isTrue();
        t1.join(1000);
        t2.join(1000);

        assertThat(error1.get()).as("thread 1 must not throw").isNull();
        assertThat(error2.get()).as("thread 2 must not throw").isNull();

        assertThat(result1.get() ^ result2.get())
                .as("exactly one resolution claim must succeed").isTrue();

        FinanceStockCommandDO DO = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(DO.getStatus()).isEqualTo("UNKNOWN");
        assertThat(DO.getClaimToken()).isIn("resolve-1", "resolve-2");
        assertThat(DO.getResolutionAttempts()).isEqualTo(1);
    }

    // ==================== Item 15: dispatchAttempts and resolutionAttempts independent ====================

    @Test
    void attempts_dispatchAndResolutionAreIndependent() {
        long id = createPending(TENANT_A, "cmd-attempts");
        String token1 = "token-attempts-1";
        claimToInFlight(TENANT_A, id, token1);

        // dispatchAttempts = 1
        FinanceStockCommandDO afterDispatch = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(afterDispatch.getDispatchAttempts()).isEqualTo(1);
        assertThat(afterDispatch.getResolutionAttempts()).isEqualTo(0);

        // Move to UNKNOWN
        LocalDateTime past = LocalDateTime.now().minusMinutes(1);
        store.markUnknown(TENANT_A, id, token1, past, 0, "Timeout", "unknown",
                LocalDateTime.now());

        // Claim resolution
        boolean resolved = store.claimResolution(TENANT_A, id, "resolve-token",
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));
        assertThat(resolved).isTrue();

        FinanceStockCommandDO afterResolution = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(afterResolution.getDispatchAttempts()).isEqualTo(1);
        assertThat(afterResolution.getResolutionAttempts()).isEqualTo(1);
    }

    // ==================== Item 16: abort PENDING/RETRY_WAIT -> CANCELLED ====================

    @Test
    void requestAbort_pending_transitionsToCancelled() {
        long id = createPending(TENANT_A, "cmd-abort-pending");

        boolean result = store.requestAbort(TENANT_A, id, LocalDateTime.now());
        assertThat(result).isTrue();

        FinanceStockCommandDO DO = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(DO.getStatus()).isEqualTo("CANCELLED");
        assertThat(DO.getAbortRequested()).isTrue();
        assertThat(DO.getClaimToken()).isNull();
        assertThat(DO.getLeaseUntil()).isNull();
    }

    @Test
    void requestAbort_retryWait_transitionsToCancelled() {
        long id = createPending(TENANT_A, "cmd-abort-retry");
        String token = "token-abort-retry";
        claimToInFlight(TENANT_A, id, token);
        store.scheduleRetry(TENANT_A, id, token, LocalDateTime.now().plusMinutes(10),
                500, "Error", "retry", LocalDateTime.now());

        boolean result = store.requestAbort(TENANT_A, id, LocalDateTime.now());
        assertThat(result).isTrue();

        FinanceStockCommandDO DO = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(DO.getStatus()).isEqualTo("CANCELLED");
        assertThat(DO.getAbortRequested()).isTrue();
    }

    // ==================== Item 17: abort IN_FLIGHT/UNKNOWN only sets flag ====================

    @Test
    void requestAbort_inFlight_onlySetsFlag() {
        long id = createPending(TENANT_A, "cmd-abort-inflight");
        String token = "token-abort-inflight";
        claimToInFlight(TENANT_A, id, token);

        boolean result = store.requestAbort(TENANT_A, id, LocalDateTime.now());
        assertThat(result).isTrue();

        FinanceStockCommandDO DO = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(DO.getStatus()).isEqualTo("IN_FLIGHT");
        assertThat(DO.getAbortRequested()).isTrue();
        assertThat(DO.getClaimToken()).isEqualTo(token);
    }

    @Test
    void requestAbort_unknown_onlySetsFlag() {
        long id = createPending(TENANT_A, "cmd-abort-unknown");
        String token = "token-abort-unknown";
        claimToInFlight(TENANT_A, id, token);
        store.markUnknown(TENANT_A, id, token, LocalDateTime.now().plusMinutes(1),
                0, "Timeout", "unknown", LocalDateTime.now());

        boolean result = store.requestAbort(TENANT_A, id, LocalDateTime.now());
        assertThat(result).isTrue();

        FinanceStockCommandDO DO = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(DO.getStatus()).isEqualTo("UNKNOWN");
        assertThat(DO.getAbortRequested()).isTrue();
    }

    @Test
    void requestAbort_terminal_isIdempotent() {
        long id = createPending(TENANT_A, "cmd-abort-terminal");
        store.requestAbort(TENANT_A, id, LocalDateTime.now());
        // Already CANCELLED (terminal) - second abort is idempotent
        boolean result = store.requestAbort(TENANT_A, id, LocalDateTime.now());
        assertThat(result).isTrue();

        FinanceStockCommandDO DO = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(DO.getStatus()).isEqualTo("CANCELLED");
    }

    // ==================== Item 18: tenant A cannot claim/update tenant B ====================

    @Test
    void crossTenant_isolation() {
        long idA = createPending(TENANT_A, "cmd-tenant-A");

        // Tenant B tries to claim tenant A's command
        TenantContextHolder.setTenantId(TENANT_B);
        boolean claimedByB = store.claimDispatch(TENANT_B, idA, "token-tenant-B",
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));
        assertThat(claimedByB).isFalse();

        // Tenant B tries to abort tenant A's command
        boolean abortedByB = store.requestAbort(TENANT_B, idA, LocalDateTime.now());
        assertThat(abortedByB).isFalse();

        // Tenant A can still claim
        TenantContextHolder.setTenantId(TENANT_A);
        boolean claimedByA = store.claimDispatch(TENANT_A, idA, "token-tenant-A",
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));
        assertThat(claimedByA).isTrue();

        FinanceStockCommandDO DO = mapper.selectByIdTenant(TENANT_A, idA);
        assertThat(DO.getStatus()).isEqualTo("IN_FLIGHT");
        assertThat(DO.getClaimToken()).isEqualTo("token-tenant-A");
    }

    // ==================== Supplement: same remote identity, different saga step ====================

    @Test
    void createOrGet_sameRemoteIdentityDifferentSagaStep_throwsConflict() {
        // First: (saga=100, step="step-A", cmd="cmd-remote-share")
        store.createOrGet(buildCreateWithSaga(
                TENANT_A, "cmd-remote-share", BODY_A, 100L, "step-A"));

        // Same (tenant, operation, businessCommandId) but different saga step
        assertThatThrownBy(() -> store.createOrGet(buildCreateWithSaga(
                TENANT_A, "cmd-remote-share", BODY_A, 200L, "step-B")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sagaId");
    }

    // ==================== Supplement: remote and local hit different records ====================

    @Test
    void createOrGet_remoteAndLocalHitDifferentRecords_throwsConflict() {
        // Pre-insert record A: (saga=100, step="step-A", cmd="cmd-A")
        long idA = directInsert(TENANT_A, "cmd-A", BODY_A, 100L, "step-A");
        // Pre-insert record B: (saga=200, step="step-B", cmd="cmd-B")
        long idB = directInsert(TENANT_A, "cmd-B", BODY_A, 200L, "step-B");

        // Third createOrGet matches A by local step (saga=100, step="step-A")
        // and B by remote identity (cmd="cmd-B"). Different ids -> conflict.
        assertThatThrownBy(() -> store.createOrGet(buildCreateWithSaga(
                TENANT_A, "cmd-B", BODY_A, 100L, "step-A")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different records");
    }

    // ==================== Supplement: same identity, different parentCommandId ====================

    @Test
    void createOrGet_sameIdentityDifferentParentCommandId_throwsConflict() {
        // First create with parentCommandId=111L
        FinanceStockCommandCreate createA = buildCreateWithParent(
                TENANT_A, "cmd-parent-mismatch", BODY_A, 111L);
        FinanceStockCommandDO first = store.createOrGet(createA);
        assertThat(first.getParentCommandId()).isEqualTo(111L);

        // Same identity (tenant, saga, step, operation, businessCommandId, body)
        // but different parentCommandId -> verifyIdentity must reject
        FinanceStockCommandCreate createB = buildCreateWithParent(
                TENANT_A, "cmd-parent-mismatch", BODY_A, 222L);
        assertThatThrownBy(() -> store.createOrGet(createB))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("parentCommandId");
    }

    // ==================== Supplement: abort_requested RETRY_WAIT cannot be claimed ====================

    @Test
    void claimDispatch_abortRequestedRetryWait_cannotClaim() {
        long id = createPending(TENANT_A, "cmd-abort-retry-claim");
        String token = "token-abort-retry";
        claimToInFlight(TENANT_A, id, token);

        // Abort while IN_FLIGHT -> abort_requested=true, status stays IN_FLIGHT
        store.requestAbort(TENANT_A, id, LocalDateTime.now());

        // scheduleRetry -> RETRY_WAIT, abort_requested stays true
        LocalDateTime past = LocalDateTime.now().minusSeconds(1);
        boolean retried = store.scheduleRetry(TENANT_A, id, token, past,
                500, "Error", "retry", LocalDateTime.now());
        assertThat(retried).isTrue();

        FinanceStockCommandDO afterRetry = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(afterRetry.getStatus()).isEqualTo("RETRY_WAIT");
        assertThat(afterRetry.getAbortRequested()).isTrue();

        // claimDispatch must fail because abort_requested=TRUE
        boolean claimed = store.claimDispatch(TENANT_A, id, "token-after-abort",
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));
        assertThat(claimed).isFalse();

        FinanceStockCommandDO afterClaim = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(afterClaim.getStatus()).isEqualTo("RETRY_WAIT");
        assertThat(afterClaim.getDispatchAttempts()).isEqualTo(1);
    }

    // ==================== Supplement: null/invalid args rejected ====================

    @Test
    void claimDispatch_nullOrInvalidArgs_rejected() {
        long id = createPending(TENANT_A, "cmd-null-args");
        LocalDateTime now = LocalDateTime.now();

        assertThatThrownBy(() ->
                store.claimDispatch(TENANT_A, id, null, now, now.plusMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimToken");
        assertThatThrownBy(() ->
                store.claimDispatch(TENANT_A, id, "  ", now, now.plusMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimToken");
        assertThatThrownBy(() ->
                store.claimDispatch(TENANT_A, id, "token", null, now.plusMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("now");
        assertThatThrownBy(() ->
                store.claimDispatch(TENANT_A, id, "token", now, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leaseUntil");
        assertThatThrownBy(() ->
                store.claimDispatch(TENANT_A, id, "token", now, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leaseUntil");
        assertThatThrownBy(() ->
                store.claimDispatch(TENANT_A, id, "token", now, now.minusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leaseUntil");
    }

    @Test
    void claimResolution_nullOrInvalidArgs_rejected() {
        long id = createPending(TENANT_A, "cmd-null-args-res");
        LocalDateTime now = LocalDateTime.now();

        assertThatThrownBy(() ->
                store.claimResolution(TENANT_A, id, null, now, now.plusMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimToken");
        assertThatThrownBy(() ->
                store.claimResolution(TENANT_A, id, "token", now, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leaseUntil");
        assertThatThrownBy(() ->
                store.claimResolution(TENANT_A, id, "token", now, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leaseUntil");
    }

    @Test
    void scheduleRetry_nullNextAttemptAt_rejected() {
        long id = createPending(TENANT_A, "cmd-null-next");
        String token = "token-null-next";
        claimToInFlight(TENANT_A, id, token);

        assertThatThrownBy(() ->
                store.scheduleRetry(TENANT_A, id, token, null,
                        500, "Error", "msg", LocalDateTime.now()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("nextAttemptAt");
    }

    @Test
    void markUnknown_nullNextAttemptAt_rejected() {
        long id = createPending(TENANT_A, "cmd-null-next-unk");
        String token = "token-null-next-unk";
        claimToInFlight(TENANT_A, id, token);

        assertThatThrownBy(() ->
                store.markUnknown(TENANT_A, id, token, null,
                        0, "Timeout", "msg", LocalDateTime.now()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("nextAttemptAt");
    }

    @Test
    void expireLeasesToUnknown_nullNextAttemptAt_rejected() {
        assertThatThrownBy(() ->
                store.expireLeasesToUnknown(TENANT_A, LocalDateTime.now(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("nextAttemptAt");
    }

    // ==================== Supplement: concurrent createOrGet, only one row ====================

    @Test
    void createOrGet_concurrentSameIdentity_onlyOneRow() throws Exception {
        int threadCount = 4;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threadCount);
        AtomicReference<Long> firstId = new AtomicReference<>(null);
        AtomicBoolean idMismatch = new AtomicBoolean(false);
        AtomicReference<Throwable> error = new AtomicReference<>(null);

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(withTenant(TENANT_A, () -> {
                try {
                    startGate.await();
                    FinanceStockCommandDO DO = store.createOrGet(
                            buildCreate(TENANT_A, "cmd-concurrent-create", BODY_A));
                    if (!firstId.compareAndSet(null, DO.getId())) {
                        if (!firstId.get().equals(DO.getId())) {
                            idMismatch.set(true);
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
        assertThat(idMismatch.get()).as("all threads must see the same id").isFalse();

        // Exactly one row in the table for this remote identity
        FinanceStockCommandDO found = mapper.selectByRemoteIdentity(
                TENANT_A, "RESERVE", "cmd-concurrent-create");
        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo("PENDING");
    }

    // ==================== Supplement: resultBody defensive copy ====================

    @Test
    void resultBody_defensiveCopy() {
        long id = createPending(TENANT_A, "cmd-result-copy");
        String token = "token-result-copy";
        claimToInFlight(TENANT_A, id, token);

        byte[] source = BODY_B.clone();
        store.completeSuccess(TENANT_A, id, token, source, 1,
                LocalDateTime.now(), LocalDateTime.now());

        // Mutate source after set - DO must not be affected
        source[0] = (byte) 'X';
        FinanceStockCommandDO DO = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(DO.getResultBody()).isEqualTo(BODY_B);

        // Mutate getter result - DO must not be affected
        byte[] got = DO.getResultBody();
        got[0] = (byte) 'Y';
        FinanceStockCommandDO reloaded = mapper.selectByIdTenant(TENANT_A, id);
        assertThat(reloaded.getResultBody()).isEqualTo(BODY_B);
    }

    // ==================== Direct insert helper (bypass createOrGet) ====================

    private long directInsert(long tenantId, String cmdId, byte[] body,
                              long sagaId, String stepKey) {
        FinanceStockCommandDO DO = new FinanceStockCommandDO();
        DO.setTenantId(tenantId);
        DO.setSagaType(FinanceStockSagaType.CHECKOUT.name());
        DO.setSagaId(sagaId);
        DO.setStepKey(stepKey);
        DO.setParentCommandId(null);
        DO.setOperation("RESERVE");
        DO.setBusinessCommandId(cmdId);
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
