package com.geihou.module.supplychain.stock.command;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.framework.tenant.core.context.TenantTaskDecorator;
import com.geihou.module.supplychain.api.stock.enums.SupplychainCommandOperationEnum;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.SupplychainCommandJournalDO;
import com.geihou.module.supplychain.stock.dal.mapper.SupplychainCommandJournalMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.geihou.module.supplychain.api.stock.enums.SupplychainCommandOperationEnum.RESERVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REV11 integration tests for {@link CommandExecutorImpl}.
 *
 * <p>Real H2 + Spring context for T1 winner, replay, drift, and TTL paths.
 * Mock mapper + real {@link PlatformTransactionManager} for T2 loser-recovery,
 * business-action DKE, and interrupt paths.
 *
 * <p>32 tests: T1 winner (9), T2 recovery (5), tenant drift (6),
 * TTL (2), interrupt (1), {@code @Nested CommandRetryConfigValidation} (9).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:command_executor_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CommandExecutorImplTest {

    @Autowired private CommandExecutor executor;
    @Autowired private SupplychainCommandJournalMapper journalMapper;
    @Autowired private CommandCodec codec;
    @Autowired private CommandClock clock;
    @Autowired private CommandRetryConfig retryConfig;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private DataSource dataSource;

    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);
    private static final String SHA_C = "c".repeat(64);
    private static final String SHA_D = "d".repeat(64);
    private static final String SHA_E = "e".repeat(64);
    private static final String SHA_F = "f".repeat(64);

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // --- Helpers ---

    private SupplychainCommandJournalDO buildJournal(String cmdId, String sha, int schemaVersion, String snapshot) {
        SupplychainCommandJournalDO journal = new SupplychainCommandJournalDO();
        journal.setTenantId(1L);
        journal.setOperation(RESERVE.getCode());
        journal.setBusinessCommandId(cmdId);
        journal.setRequestBodySha256(sha);
        journal.setResultSchemaVersion(schemaVersion);
        journal.setResultSnapshot(snapshot);
        journal.setExecutedAt(LocalDateTime.now());
        return journal;
    }

    private CommandExecutorImpl mockExecutor(SupplychainCommandJournalMapper mockMapper, CommandSleeper sleeper) {
        return new CommandExecutorImpl(mockMapper, codec, clock, sleeper, retryConfig, txManager);
    }

    // === T1 winner path (9) ===

    @Test
    void t1_firstWin_insertsJournalAndReturnsCanonicalSnapshot() {
        CommandResult<Long> result = executor.execute(RESERVE, "t1-firstwin-cmd", SHA_A, () -> 42L);

        assertThat(result.isReplay()).isFalse();
        assertThat(result.value()).isEqualTo(42L);

        SupplychainCommandJournalDO journal = journalMapper.selectByTenantOperationCommandId(
                1L, RESERVE.getCode(), "t1-firstwin-cmd");
        assertThat(journal).isNotNull();
        assertThat(journal.getTenantId()).isEqualTo(1L);
        assertThat(journal.getRequestBodySha256()).isEqualTo(SHA_A);
        assertThat(journal.getResultSchemaVersion()).isEqualTo(1);
        assertThat(journal.getResultSnapshot()).contains("42");
    }

    @Test
    void t1_sameHash_replayReturnsWinnerSnapshot() {
        String snapshot = codec.serialize(RESERVE, 42L);
        journalMapper.insert(buildJournal("t1-samehash-cmd", SHA_A, 1, snapshot));

        AtomicInteger runCount = new AtomicInteger();
        CommandResult<Long> result = executor.execute(RESERVE, "t1-samehash-cmd", SHA_A,
                () -> { runCount.incrementAndGet(); return 999L; });

        assertThat(result.isReplay()).isTrue();
        assertThat(result.value()).isEqualTo(42L);
        assertThat(runCount.get()).isEqualTo(0);
    }

    @Test
    void t1_diffHash_throws2002064() {
        journalMapper.insert(buildJournal("t1-diffhash-cmd", SHA_B, 1, codec.serialize(RESERVE, 42L)));

        StockBusinessException ex = assertThrows(StockBusinessException.class,
                () -> executor.execute(RESERVE, "t1-diffhash-cmd", SHA_A, () -> 999L));

        assertThat(ex.getCode()).isEqualTo(2002064);
        assertThat(ex.getMessage()).isEqualTo("IDEMPOTENT_CONFLICT");
    }

    @Test
    void release_voidResult_insertsEmptySnapshot() {
        CommandResult<Void> result = executor.execute(
                SupplychainCommandOperationEnum.RELEASE, "t1-release-cmd", SHA_A,
                (CommandAction<Void>) () -> null);

        assertThat(result.isReplay()).isFalse();
        assertThat(result.value()).isNull();

        SupplychainCommandJournalDO journal = journalMapper.selectByTenantOperationCommandId(
                1L, SupplychainCommandOperationEnum.RELEASE.getCode(), "t1-release-cmd");
        assertThat(journal).isNotNull();
        assertThat(journal.getResultSnapshot()).isNotBlank();
    }

    @Test
    void businessActionDuplicateKey_doesNotStartT2() {
        SupplychainCommandJournalMapper mockMapper = mock(SupplychainCommandJournalMapper.class);
        when(mockMapper.selectByTenantOperationCommandId(anyLong(), anyString(), anyString()))
                .thenReturn(null);

        CommandExecutorImpl testExecutor = mockExecutor(mockMapper, ms -> {});

        assertThatThrownBy(() -> testExecutor.execute(RESERVE, "action-dke-cmd", SHA_A, () -> {
            throw new DuplicateKeyException("action-level DKE");
        })).isInstanceOf(DuplicateKeyException.class);

        verify(mockMapper, times(1)).selectByTenantOperationCommandId(anyLong(), anyString(), anyString());
        verify(mockMapper, never()).insert(any(SupplychainCommandJournalDO.class));
    }

    @Test
    void businessCommandId_exceeds128_throwsIllegalArgument() {
        String cmd129 = "c".repeat(129);
        assertThatThrownBy(() -> executor.execute(RESERVE, cmd129, SHA_A, () -> 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 128 chars");
    }

    @Test
    void businessCommandId_exactly128_accepted() {
        String cmd128 = "a".repeat(128);
        CommandResult<Long> result = executor.execute(RESERVE, cmd128, SHA_A, () -> 42L);

        assertThat(result.value()).isEqualTo(42L);
        assertThat(journalMapper.selectByTenantOperationCommandId(1L, RESERVE.getCode(), cmd128)).isNotNull();
    }

    @Test
    void t1_unknownSchemaVersionDiffHash_throws2002064() {
        journalMapper.insert(buildJournal("t1-unknown-diffhash-cmd", SHA_B, 2, "{\"reserveId\":42}"));

        StockBusinessException ex = assertThrows(StockBusinessException.class,
                () -> executor.execute(RESERVE, "t1-unknown-diffhash-cmd", SHA_A, () -> 999L));

        assertThat(ex.getCode()).isEqualTo(2002064);
        assertThat(ex.getMessage()).isEqualTo("IDEMPOTENT_CONFLICT");
    }

    @Test
    void t1_unknownSchemaVersionSameHash_throwsIllegalState() {
        journalMapper.insert(buildJournal("t1-unknown-samehash-cmd", SHA_A, 2, "{\"reserveId\":42}"));

        assertThatThrownBy(() -> executor.execute(RESERVE, "t1-unknown-samehash-cmd", SHA_A, () -> 999L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown resultSchemaVersion");
    }

    // === T2 loser recovery (5) ===

    @Test
    void t2_boundedRetryExhausted_actionRunCalledOnceAndThrows2002065() {
        SupplychainCommandJournalMapper mockMapper = mock(SupplychainCommandJournalMapper.class);
        when(mockMapper.selectByTenantOperationCommandId(anyLong(), anyString(), anyString()))
                .thenReturn(null);
        when(mockMapper.insert(any(SupplychainCommandJournalDO.class)))
                .thenThrow(new DuplicateKeyException("uk conflict"));

        List<Long> sleepCalls = new ArrayList<>();
        CommandExecutorImpl testExecutor = mockExecutor(mockMapper, sleepCalls::add);

        AtomicInteger runCount = new AtomicInteger();
        CommandAction<Long> action = () -> { runCount.incrementAndGet(); return 42L; };

        StockBusinessException ex = assertThrows(StockBusinessException.class,
                () -> testExecutor.execute(RESERVE, "t2-exhausted-cmd", SHA_A, action));

        assertThat(ex.getCode()).isEqualTo(2002065);
        assertThat(ex.getMessage()).isEqualTo("CONSISTENCY_INTERNAL_ERROR");
        assertThat(runCount.get()).isEqualTo(1);
        verify(mockMapper, times(1)).insert(any(SupplychainCommandJournalDO.class));
        verify(mockMapper, times(6)).selectByTenantOperationCommandId(anyLong(), anyString(), anyString());
        assertThat(sleepCalls).containsExactly(20L, 40L, 80L, 160L);
    }

    @Test
    void t2_readsWinnerSameHash_replayReturnsWinnerSnapshot() {
        String winnerSnapshot = codec.serialize(RESERVE, 42L);
        SupplychainCommandJournalDO winner = buildJournal("t2-samehash-cmd", SHA_A, 1, winnerSnapshot);

        SupplychainCommandJournalMapper mockMapper = mock(SupplychainCommandJournalMapper.class);
        when(mockMapper.selectByTenantOperationCommandId(anyLong(), anyString(), anyString()))
                .thenReturn(null)
                .thenReturn(winner);
        when(mockMapper.insert(any(SupplychainCommandJournalDO.class)))
                .thenThrow(new DuplicateKeyException("uk conflict"));

        AtomicInteger runCount = new AtomicInteger();
        CommandExecutorImpl testExecutor = mockExecutor(mockMapper, ms -> {});

        CommandResult<Long> result = testExecutor.execute(RESERVE, "t2-samehash-cmd", SHA_A,
                () -> { runCount.incrementAndGet(); return 999L; });

        assertThat(result.isReplay()).isTrue();
        assertThat(result.value()).isEqualTo(42L);
        assertThat(runCount.get()).isEqualTo(1);
        verify(mockMapper, times(1)).insert(any(SupplychainCommandJournalDO.class));
        verify(mockMapper, times(2)).selectByTenantOperationCommandId(anyLong(), anyString(), anyString());
    }

    @Test
    void t2_readsWinnerDiffHash_throws2002064() {
        SupplychainCommandJournalDO winner = buildJournal("t2-diffhash-cmd", SHA_B, 1,
                codec.serialize(RESERVE, 42L));

        SupplychainCommandJournalMapper mockMapper = mock(SupplychainCommandJournalMapper.class);
        when(mockMapper.selectByTenantOperationCommandId(anyLong(), anyString(), anyString()))
                .thenReturn(null)
                .thenReturn(winner);
        when(mockMapper.insert(any(SupplychainCommandJournalDO.class)))
                .thenThrow(new DuplicateKeyException("uk conflict"));

        CommandExecutorImpl testExecutor = mockExecutor(mockMapper, ms -> {});

        StockBusinessException ex = assertThrows(StockBusinessException.class,
                () -> testExecutor.execute(RESERVE, "t2-diffhash-cmd", SHA_A, () -> 42L));

        assertThat(ex.getCode()).isEqualTo(2002064);
        assertThat(ex.getMessage()).isEqualTo("IDEMPOTENT_CONFLICT");
    }

    @Test
    void t2_unknownSchemaVersionDiffHash_throws2002064() {
        SupplychainCommandJournalDO winner = buildJournal("t2-unknown-diffhash-cmd", SHA_B, 2,
                "{\"reserveId\":42}");

        SupplychainCommandJournalMapper mockMapper = mock(SupplychainCommandJournalMapper.class);
        when(mockMapper.selectByTenantOperationCommandId(anyLong(), anyString(), anyString()))
                .thenReturn(null)
                .thenReturn(winner);
        when(mockMapper.insert(any(SupplychainCommandJournalDO.class)))
                .thenThrow(new DuplicateKeyException("uk conflict"));

        AtomicInteger runCount = new AtomicInteger();
        CommandExecutorImpl testExecutor = mockExecutor(mockMapper, ms -> {});

        StockBusinessException ex = assertThrows(StockBusinessException.class,
                () -> testExecutor.execute(RESERVE, "t2-unknown-diffhash-cmd", SHA_A,
                        () -> { runCount.incrementAndGet(); return 42L; }));

        assertThat(ex.getCode()).isEqualTo(2002064);
        assertThat(ex.getMessage()).isEqualTo("IDEMPOTENT_CONFLICT");
        assertThat(runCount.get()).isEqualTo(1);
        verify(mockMapper, times(1)).insert(any(SupplychainCommandJournalDO.class));
        verify(mockMapper, times(2)).selectByTenantOperationCommandId(anyLong(), anyString(), anyString());
    }

    @Test
    void t2_unknownSchemaVersionSameHash_throwsIllegalState() {
        SupplychainCommandJournalDO winner = buildJournal("t2-unknown-samehash-cmd", SHA_A, 2,
                "{\"reserveId\":42}");

        SupplychainCommandJournalMapper mockMapper = mock(SupplychainCommandJournalMapper.class);
        when(mockMapper.selectByTenantOperationCommandId(anyLong(), anyString(), anyString()))
                .thenReturn(null)
                .thenReturn(winner);
        when(mockMapper.insert(any(SupplychainCommandJournalDO.class)))
                .thenThrow(new DuplicateKeyException("uk conflict"));

        AtomicInteger runCount = new AtomicInteger();
        CommandExecutorImpl testExecutor = mockExecutor(mockMapper, ms -> {});

        assertThatThrownBy(() -> testExecutor.execute(RESERVE, "t2-unknown-samehash-cmd", SHA_A,
                () -> { runCount.incrementAndGet(); return 42L; }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown resultSchemaVersion");

        assertThat(runCount.get()).isEqualTo(1);
        verify(mockMapper, times(1)).insert(any(SupplychainCommandJournalDO.class));
        verify(mockMapper, times(2)).selectByTenantOperationCommandId(anyLong(), anyString(), anyString());
    }

    // === Tenant drift (6) ===

    @Test
    void drift_actionWritesDbThenDrifts_throwsAndRollsBackBusinessWrite() {
        CommandAction<Long> driftingAction = () -> {
            journalMapper.insert(buildJournal("business-write-evidence-cmd", SHA_C, 1,
                    "{\"reserveId\":1}"));
            TenantContextHolder.setTenantId(999L);
            return 42L;
        };

        assertThatThrownBy(() -> executor.execute(RESERVE, "drift-with-business-cmd", SHA_A, driftingAction))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context drifted");

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(1L);
        assertThat(TenantContextHolder.isIgnore()).isFalse();
        assertThat(journalMapper.selectByTenantOperationCommandId(
                1L, RESERVE.getCode(), "business-write-evidence-cmd")).isNull();
        assertThat(journalMapper.selectByTenantOperationCommandId(
                1L, RESERVE.getCode(), "drift-with-business-cmd")).isNull();
    }

    @Test
    void drift_actionWritesDbThenThrows_finallyRestoresAndRollsBack() {
        CommandAction<Long> throwingAction = () -> {
            journalMapper.insert(buildJournal("business-write-throw-cmd", SHA_D, 1,
                    "{\"reserveId\":2}"));
            TenantContextHolder.setTenantId(999L);
            throw new RuntimeException("business error after drift");
        };

        assertThatThrownBy(() -> executor.execute(RESERVE, "drift-throw-business-cmd", SHA_A, throwingAction))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("business error after drift");

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(1L);
        assertThat(TenantContextHolder.isIgnore()).isFalse();
        assertThat(journalMapper.selectByTenantOperationCommandId(
                1L, RESERVE.getCode(), "business-write-throw-cmd")).isNull();
        assertThat(journalMapper.selectByTenantOperationCommandId(
                1L, RESERVE.getCode(), "drift-throw-business-cmd")).isNull();
    }

    @Test
    void drift_actionWritesDbThenSetsIgnore_throwsAndRollsBack() {
        CommandAction<Long> ignoreAction = () -> {
            journalMapper.insert(buildJournal("business-write-ignore-cmd", SHA_E, 1,
                    "{\"reserveId\":3}"));
            TenantContextHolder.setIgnore(true);
            return 42L;
        };

        assertThatThrownBy(() -> executor.execute(RESERVE, "drift-ignore-business-cmd", SHA_A, ignoreAction))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tenant ignore flag drifted during action");

        assertThat(TenantContextHolder.isIgnore()).isFalse();
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(1L);
        assertThat(journalMapper.selectByTenantOperationCommandId(
                1L, RESERVE.getCode(), "business-write-ignore-cmd")).isNull();
        assertThat(journalMapper.selectByTenantOperationCommandId(
                1L, RESERVE.getCode(), "drift-ignore-business-cmd")).isNull();
    }

    @Test
    void noDrift_actionWritesDb_commitsSuccessfully() {
        CommandAction<Long> cleanAction = () -> {
            journalMapper.insert(buildJournal("clean-business-write-cmd", SHA_F, 1,
                    "{\"reserveId\":4}"));
            return 42L;
        };

        CommandResult<Long> result = executor.execute(RESERVE, "no-drift-main-cmd", SHA_A, cleanAction);

        assertThat(result.value()).isEqualTo(42L);
        assertThat(journalMapper.selectByTenantOperationCommandId(
                1L, RESERVE.getCode(), "clean-business-write-cmd")).isNotNull();
        assertThat(journalMapper.selectByTenantOperationCommandId(
                1L, RESERVE.getCode(), "no-drift-main-cmd")).isNotNull();
    }

    @Test
    void entry_tenantIdZero_throwsIllegalState() {
        TenantContextHolder.setTenantId(0L);
        assertThatThrownBy(() -> executor.execute(RESERVE, "tenant-zero-cmd", SHA_A, () -> 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Valid tenant context required");
    }

    @Test
    void entry_ignoreTrue_throwsIllegalState() {
        TenantContextHolder.setIgnore(true);
        assertThatThrownBy(() -> executor.execute(RESERVE, "ignore-true-cmd", SHA_A, () -> 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tenant ignore flag must not be true for command execution");
    }

    // === TTL (2) ===

    @Test
    void t_tenant_3_tenantTaskDecorator_capturesSnapshotAcrossCleanWorker() throws Exception {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            // Step 1: establish a clean worker - clear any inherited context inside the worker
            exec.submit(() -> TenantContextHolder.clear()).get(3, TimeUnit.SECONDS);

            // Step 2: set tenant=3 in main thread AFTER worker is clean
            TenantContextHolder.setTenantId(3L);

            // Step 3: decorated task captures tenant from the snapshot taken at submit time
            AtomicReference<Long> captured = new AtomicReference<>();
            exec.submit(new TenantTaskDecorator().decorate(
                    () -> captured.set(TenantContextHolder.getTenantId())
            )).get(3, TimeUnit.SECONDS);

            // Without decorator, clean worker would see null; with decorator, it sees captured snapshot
            assertThat(captured.get()).isEqualTo(3L);
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void t_tenant_3_mainThreadWithTenant_executorSucceedsAndJournalInserted() {
        TenantContextHolder.setTenantId(3L);
        CommandResult<Long> result = executor.execute(RESERVE, "t-tenant-3-main-cmd", SHA_A, () -> 42L);

        assertThat(result.value()).isEqualTo(42L);
        SupplychainCommandJournalDO journal = journalMapper.selectByTenantOperationCommandId(
                3L, RESERVE.getCode(), "t-tenant-3-main-cmd");
        assertThat(journal).isNotNull();
        assertThat(journal.getTenantId()).isEqualTo(3L);
    }

    // === Interrupt (1) ===

    @Test
    void interruptedExceptionInSleeper_restoresInterruptFlagAndThrowsIllegalState() {
        SupplychainCommandJournalMapper mockMapper = mock(SupplychainCommandJournalMapper.class);
        when(mockMapper.selectByTenantOperationCommandId(anyLong(), anyString(), anyString()))
                .thenReturn(null);
        when(mockMapper.insert(any(SupplychainCommandJournalDO.class)))
                .thenThrow(new DuplicateKeyException("uk conflict"));

        // Pre-interrupt, then delegate to real SystemCommandSleeper. This verifies the
        // production sleeper catches InterruptedException, restores the interrupt flag,
        // and throws ISE - rather than simulating the behavior manually.
        SystemCommandSleeper systemSleeper = new SystemCommandSleeper();
        CommandSleeper interruptingSleeper = ms -> {
            Thread.currentThread().interrupt();
            systemSleeper.sleepMillis(ms);
        };

        CommandExecutorImpl testExecutor = mockExecutor(mockMapper, interruptingSleeper);

        AtomicInteger runCount = new AtomicInteger();
        CommandAction<Long> action = () -> { runCount.incrementAndGet(); return 42L; };

        try {
            // ISE propagates directly from T2 sleeper - NOT converted to StockBusinessException(2002065)
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> testExecutor.execute(RESERVE, "interrupt-cmd", SHA_A, action));

            assertThat(ex.getMessage()).isEqualTo("Command sleeper interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();

            // T1 ran (action 1x, insert 1x); T2 entered (SELECT 2x = 1 T1 + 1 T2 attempt 0)
            // T2 did NOT exhaust (would need 6 SELECTs + throw 2002065)
            assertThat(runCount.get()).isEqualTo(1);
            verify(mockMapper, times(1)).insert(any(SupplychainCommandJournalDO.class));
            verify(mockMapper, times(2)).selectByTenantOperationCommandId(anyLong(), anyString(), anyString());
        } finally {
            // ensure no interrupt flag leaks to subsequent tests
            Thread.interrupted();
        }
    }

    // === REV11: CommandRetryConfig validation merged as @Nested ===

    @Nested
    class CommandRetryConfigValidation {

        @Test
        void defaults_areValid() {
            CommandRetryConfig config = new CommandRetryConfig();
            config.validate();
            assertThat(config.getMaxAttempts()).isEqualTo(5);
            assertThat(config.getBackoffMs()).containsExactly(0L, 20L, 40L, 80L, 160L);
        }

        @Test
        void backoffMsNull_throwsIllegalState() {
            CommandRetryConfig config = new CommandRetryConfig();
            assertThatThrownBy(() -> config.setBackoffMs(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("backoffMs must be non-null and non-empty");
        }

        @Test
        void backoffMsEmpty_throwsIllegalState() {
            CommandRetryConfig config = new CommandRetryConfig();
            assertThatThrownBy(() -> config.setBackoffMs(new long[0]))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("backoffMs must be non-null and non-empty");
        }

        @Test
        void backoffMsFirstNotZero_throwsIllegalState() {
            CommandRetryConfig config = new CommandRetryConfig();
            assertThatThrownBy(() -> config.setBackoffMs(new long[]{1L, 20L}))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("backoffMs[0] must be 0");
        }

        @Test
        void backoffMsNegative_throwsIllegalState() {
            CommandRetryConfig config = new CommandRetryConfig();
            assertThatThrownBy(() -> config.setBackoffMs(new long[]{0L, -1L}))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must be non-negative");
        }

        @Test
        void maxAttemptsZero_throwsIllegalState() {
            CommandRetryConfig config = new CommandRetryConfig();
            assertThatThrownBy(() -> config.setMaxAttempts(0))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("maxAttempts must be >= 1");
        }

        @Test
        void maxAttemptsAndBackoffLengthMismatch_throwsIllegalState() {
            CommandRetryConfig config = new CommandRetryConfig();
            config.setMaxAttempts(3);
            assertThatThrownBy(() -> config.validate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("maxAttempts (3) must equal backoffMs.length (5)");
        }

        @Test
        void getBackoffMs_returnsDefensiveCopy() {
            CommandRetryConfig config = new CommandRetryConfig();
            long[] arr = config.getBackoffMs();
            arr[0] = 999L;
            assertThat(config.getBackoffMs()[0]).isEqualTo(0L);
        }

        @Test
        void setBackoffMs_defensiveCopy() {
            CommandRetryConfig config = new CommandRetryConfig();
            long[] input = {0L, 20L, 40L};
            config.setBackoffMs(input);
            input[0] = 999L;
            assertThat(config.getBackoffMs()[0]).isEqualTo(0L);
        }
    }
}
