package com.geihou.module.supplychain.stock.command;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.enums.SupplychainCommandOperationEnum;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.SupplychainCommandJournalDO;
import com.geihou.module.supplychain.stock.dal.mapper.SupplychainCommandJournalMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static com.geihou.module.supplychain.api.stock.enums.SupplychainCommandOperationEnum.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link CommandStatusQueryService}.
 *
 * <p>22 tests: 17 H2 + 5 mock mapper.
 *
 * <p>H2 tests cover 7 SUCCEEDED (with full field verification), 6 NOT_FOUND,
 * unknown schema version, corrupted JSON, cross-tenant isolation, and
 * checkedAt sourced from fixed clock.
 *
 * <p>Mock mapper tests cover null schemaVersion, null executedAt, and 3
 * tenant-context invalid scenarios (null, non-positive, ignore-mode) that
 * cannot be set up via H2 due to NOT NULL constraints.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:command_status_query_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CommandStatusQueryServiceTest {

    @Autowired private CommandStatusQueryService queryService;
    @Autowired private SupplychainCommandJournalMapper journalMapper;
    @Autowired private CommandCodec codec;
    @Autowired private DataSource dataSource;

    private static final String SHA_A = "a".repeat(64);

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

    private SupplychainCommandJournalDO buildJournal(
            SupplychainCommandOperationEnum operation,
            String cmdId, String sha, int schemaVersion, String snapshot) {
        SupplychainCommandJournalDO journal = new SupplychainCommandJournalDO();
        journal.setTenantId(1L);
        journal.setOperation(operation.getCode());
        journal.setBusinessCommandId(cmdId);
        journal.setRequestBodySha256(sha);
        journal.setResultSchemaVersion(schemaVersion);
        journal.setResultSnapshot(snapshot);
        journal.setExecutedAt(TestSupport.FIXED_TIME);
        return journal;
    }

    private CommandStatusQueryService queryServiceWithMock(
            SupplychainCommandJournalMapper mockMapper) {
        return new CommandStatusQueryService(mockMapper, codec, TestSupport.fixedClock());
    }

    // === SUCCEEDED tests (7) ===

    @Test
    void reserve_succeeded() {
        String snapshot = codec.serialize(RESERVE, TestSupport.sampleReserveResult());
        journalMapper.insert(buildJournal(RESERVE, "reserve-cmd", SHA_A, 1, snapshot));

        CommandStatusResult result = queryService.query(RESERVE, "reserve-cmd");

        assertThat(result).isInstanceOf(CommandStatusResult.ReserveSucceeded.class);
        CommandStatusResult.ReserveSucceeded succeeded = (CommandStatusResult.ReserveSucceeded) result;
        assertThat(succeeded.operation()).isEqualTo(RESERVE);
        assertThat(succeeded.status()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.businessCommandId()).isEqualTo("reserve-cmd");
        assertThat(succeeded.result().reserveId()).isEqualTo(123L);
        assertThat(succeeded.executedAt()).isEqualTo(TestSupport.FIXED_TIME);
        assertThat(succeeded.checkedAt()).isNotNull();
    }

    @Test
    void release_succeeded() {
        String snapshot = codec.serialize(RELEASE, null);
        journalMapper.insert(buildJournal(RELEASE, "release-cmd", SHA_A, 1, snapshot));

        CommandStatusResult result = queryService.query(RELEASE, "release-cmd");

        assertThat(result).isInstanceOf(CommandStatusResult.ReleaseSucceeded.class);
        CommandStatusResult.ReleaseSucceeded succeeded = (CommandStatusResult.ReleaseSucceeded) result;
        assertThat(succeeded.operation()).isEqualTo(RELEASE);
        assertThat(succeeded.status()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.businessCommandId()).isEqualTo("release-cmd");
        assertThat(succeeded.result()).isInstanceOf(CommandStatusResult.ReleaseResult.class);
        assertThat(succeeded.executedAt()).isEqualTo(TestSupport.FIXED_TIME);
        assertThat(succeeded.checkedAt()).isNotNull();
    }

    @Test
    void commit_succeeded() {
        String snapshot = codec.serialize(COMMIT, TestSupport.sampleCommitResult());
        journalMapper.insert(buildJournal(COMMIT, "commit-cmd", SHA_A, 1, snapshot));

        CommandStatusResult result = queryService.query(COMMIT, "commit-cmd");

        assertThat(result).isInstanceOf(CommandStatusResult.CommitSucceeded.class);
        CommandStatusResult.CommitSucceeded succeeded = (CommandStatusResult.CommitSucceeded) result;
        assertThat(succeeded.operation()).isEqualTo(COMMIT);
        assertThat(succeeded.status()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.businessCommandId()).isEqualTo("commit-cmd");
        assertThat(succeeded.result().consumeOutEventId()).isEqualTo(456L);
        assertThat(succeeded.executedAt()).isEqualTo(TestSupport.FIXED_TIME);
        assertThat(succeeded.checkedAt()).isNotNull();
    }

    @Test
    void salesOutBomReverse_succeeded() {
        String snapshot = codec.serialize(SALES_OUT_BOM_REVERSE,
                TestSupport.sampleSalesOutBomReverse());
        journalMapper.insert(buildJournal(SALES_OUT_BOM_REVERSE, "bom-reverse-cmd",
                SHA_A, 1, snapshot));

        CommandStatusResult result = queryService.query(SALES_OUT_BOM_REVERSE, "bom-reverse-cmd");

        assertThat(result).isInstanceOf(CommandStatusResult.SalesOutBomReverseSucceeded.class);
        CommandStatusResult.SalesOutBomReverseSucceeded succeeded =
                (CommandStatusResult.SalesOutBomReverseSucceeded) result;
        assertThat(succeeded.operation()).isEqualTo(SALES_OUT_BOM_REVERSE);
        assertThat(succeeded.status()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.businessCommandId()).isEqualTo("bom-reverse-cmd");
        assertThat(succeeded.executedAt()).isEqualTo(TestSupport.FIXED_TIME);
        CommandStatusResult.SalesOutBomReverseResult res = succeeded.result();
        assertThat(res.productId()).isEqualTo(100L);
        assertThat(res.skuCode()).isEqualTo("SKU-FINISHED");
        assertThat(res.quantity()).isEqualByComparingTo("5.00");
        assertThat(res.recipeId()).isEqualTo(200L);
        assertThat(res.recipeVersion()).isEqualTo(1);
        assertThat(res.items()).hasSize(1);
        CommandStatusResult.SalesOutBomReverseResult.Item item = res.items().get(0);
        assertThat(item.componentProductId()).isEqualTo(101L);
        assertThat(item.skuCode()).isEqualTo("SKU-RAW");
        assertThat(item.unit()).isEqualTo("KG");
        assertThat(item.stockItemId()).isEqualTo(1001L);
        assertThat(item.quantity()).isEqualByComparingTo("2.00");
        assertThat(item.eventId()).isEqualTo(5001L);
        assertThat(item.clientRequestId()).isEqualTo("cr-001::101");
        assertThat(item.recipeId()).isEqualTo(200L);
        assertThat(item.recipeVersion()).isEqualTo(1);
    }

    @Test
    void salesReverseRestore_succeeded() {
        String snapshot = codec.serialize(SALES_REVERSE_RESTORE,
                TestSupport.sampleSalesReverseRestore());
        journalMapper.insert(buildJournal(SALES_REVERSE_RESTORE, "reverse-restore-cmd",
                SHA_A, 1, snapshot));

        CommandStatusResult result = queryService.query(SALES_REVERSE_RESTORE,
                "reverse-restore-cmd");

        assertThat(result).isInstanceOf(CommandStatusResult.SalesReverseRestoreSucceeded.class);
        CommandStatusResult.SalesReverseRestoreSucceeded succeeded =
                (CommandStatusResult.SalesReverseRestoreSucceeded) result;
        assertThat(succeeded.operation()).isEqualTo(SALES_REVERSE_RESTORE);
        assertThat(succeeded.status()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.businessCommandId()).isEqualTo("reverse-restore-cmd");
        assertThat(succeeded.executedAt()).isEqualTo(TestSupport.FIXED_TIME);
        CommandStatusResult.SalesReverseRestoreResult res = succeeded.result();
        assertThat(res.restoredItemCount()).isEqualTo(1);
        assertThat(res.items()).hasSize(1);
        CommandStatusResult.SalesReverseRestoreResult.Item item = res.items().get(0);
        assertThat(item.originalEventId()).isEqualTo(5001L);
        assertThat(item.restoreEventId()).isEqualTo(6001L);
        assertThat(item.stockItemId()).isEqualTo(1001L);
        assertThat(item.locationId()).isEqualTo(2001L);
        assertThat(item.quantity()).isEqualByComparingTo("2.00");
        assertThat(item.unit()).isEqualTo("KG");
        assertThat(item.recipeId()).isEqualTo(200L);
        assertThat(item.recipeVersion()).isEqualTo(1);
    }

    @Test
    void observeAuditOnly_succeeded() {
        String snapshot = codec.serialize(OBSERVE_MISSING_MAPPING,
                TestSupport.sampleObserveAuditOnly());
        journalMapper.insert(buildJournal(OBSERVE_MISSING_MAPPING, "observe-audit-cmd",
                SHA_A, 1, snapshot));

        CommandStatusResult result = queryService.query(OBSERVE_MISSING_MAPPING,
                "observe-audit-cmd");

        assertThat(result).isInstanceOf(CommandStatusResult.ObserveSucceeded.class);
        CommandStatusResult.ObserveSucceeded succeeded =
                (CommandStatusResult.ObserveSucceeded) result;
        assertThat(succeeded.operation()).isEqualTo(OBSERVE_MISSING_MAPPING);
        assertThat(succeeded.status()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.businessCommandId()).isEqualTo("observe-audit-cmd");
        assertThat(succeeded.executedAt()).isEqualTo(TestSupport.FIXED_TIME);
        assertThat(succeeded.result()).isInstanceOf(CommandStatusResult.AuditOnlyObserveResult.class);
        CommandStatusResult.AuditOnlyObserveResult observeResult =
                (CommandStatusResult.AuditOnlyObserveResult) succeeded.result();
        assertThat(observeResult.mode()).isEqualTo("AUDIT_ONLY");
        assertThat(observeResult.enforce()).isFalse();
    }

    @Test
    void observeEnforce_succeeded() {
        String snapshot = codec.serialize(OBSERVE_MISSING_MAPPING,
                TestSupport.sampleObserveEnforce());
        journalMapper.insert(buildJournal(OBSERVE_MISSING_MAPPING, "observe-enforce-cmd",
                SHA_A, 1, snapshot));

        CommandStatusResult result = queryService.query(OBSERVE_MISSING_MAPPING,
                "observe-enforce-cmd");

        assertThat(result).isInstanceOf(CommandStatusResult.ObserveSucceeded.class);
        CommandStatusResult.ObserveSucceeded succeeded =
                (CommandStatusResult.ObserveSucceeded) result;
        assertThat(succeeded.operation()).isEqualTo(OBSERVE_MISSING_MAPPING);
        assertThat(succeeded.status()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.businessCommandId()).isEqualTo("observe-enforce-cmd");
        assertThat(succeeded.executedAt()).isEqualTo(TestSupport.FIXED_TIME);
        assertThat(succeeded.result()).isInstanceOf(CommandStatusResult.EnforceObserveResult.class);
        CommandStatusResult.EnforceObserveResult observeResult =
                (CommandStatusResult.EnforceObserveResult) succeeded.result();
        assertThat(observeResult.mode()).isEqualTo("ENFORCE");
        assertThat(observeResult.enforce()).isTrue();
    }

    // === NOT_FOUND tests (6) ===

    @Test
    void notFound_reserve() {
        CommandStatusResult result = queryService.query(RESERVE, "nonexistent-reserve-cmd");

        assertThat(result).isInstanceOf(CommandStatusResult.NotFound.class);
        CommandStatusResult.NotFound notFound = (CommandStatusResult.NotFound) result;
        assertThat(notFound.operation()).isEqualTo(RESERVE);
        assertThat(notFound.status()).isEqualTo("NOT_FOUND");
        assertThat(notFound.businessCommandId()).isEqualTo("nonexistent-reserve-cmd");
        assertThat(notFound.checkedAt()).isNotNull();
    }

    @Test
    void notFound_release() {
        CommandStatusResult result = queryService.query(RELEASE, "nonexistent-release-cmd");

        assertThat(result).isInstanceOf(CommandStatusResult.NotFound.class);
        CommandStatusResult.NotFound notFound = (CommandStatusResult.NotFound) result;
        assertThat(notFound.operation()).isEqualTo(RELEASE);
        assertThat(notFound.status()).isEqualTo("NOT_FOUND");
        assertThat(notFound.businessCommandId()).isEqualTo("nonexistent-release-cmd");
        assertThat(notFound.checkedAt()).isNotNull();
    }

    @Test
    void notFound_commit() {
        CommandStatusResult result = queryService.query(COMMIT, "nonexistent-commit-cmd");

        assertThat(result).isInstanceOf(CommandStatusResult.NotFound.class);
        CommandStatusResult.NotFound notFound = (CommandStatusResult.NotFound) result;
        assertThat(notFound.operation()).isEqualTo(COMMIT);
        assertThat(notFound.status()).isEqualTo("NOT_FOUND");
        assertThat(notFound.businessCommandId()).isEqualTo("nonexistent-commit-cmd");
        assertThat(notFound.checkedAt()).isNotNull();
    }

    @Test
    void notFound_salesOutBomReverse() {
        CommandStatusResult result = queryService.query(SALES_OUT_BOM_REVERSE,
                "nonexistent-bom-cmd");

        assertThat(result).isInstanceOf(CommandStatusResult.NotFound.class);
        CommandStatusResult.NotFound notFound = (CommandStatusResult.NotFound) result;
        assertThat(notFound.operation()).isEqualTo(SALES_OUT_BOM_REVERSE);
        assertThat(notFound.status()).isEqualTo("NOT_FOUND");
        assertThat(notFound.businessCommandId()).isEqualTo("nonexistent-bom-cmd");
        assertThat(notFound.checkedAt()).isNotNull();
    }

    @Test
    void notFound_salesReverseRestore() {
        CommandStatusResult result = queryService.query(SALES_REVERSE_RESTORE,
                "nonexistent-restore-cmd");

        assertThat(result).isInstanceOf(CommandStatusResult.NotFound.class);
        CommandStatusResult.NotFound notFound = (CommandStatusResult.NotFound) result;
        assertThat(notFound.operation()).isEqualTo(SALES_REVERSE_RESTORE);
        assertThat(notFound.status()).isEqualTo("NOT_FOUND");
        assertThat(notFound.businessCommandId()).isEqualTo("nonexistent-restore-cmd");
        assertThat(notFound.checkedAt()).isNotNull();
    }

    @Test
    void notFound_observe() {
        CommandStatusResult result = queryService.query(OBSERVE_MISSING_MAPPING,
                "nonexistent-observe-cmd");

        assertThat(result).isInstanceOf(CommandStatusResult.NotFound.class);
        CommandStatusResult.NotFound notFound = (CommandStatusResult.NotFound) result;
        assertThat(notFound.operation()).isEqualTo(OBSERVE_MISSING_MAPPING);
        assertThat(notFound.status()).isEqualTo("NOT_FOUND");
        assertThat(notFound.businessCommandId()).isEqualTo("nonexistent-observe-cmd");
        assertThat(notFound.checkedAt()).isNotNull();
    }

    // === Error handling H2 tests (2) ===

    @Test
    void unknownSchemaVersion_throwsIllegalState() {
        journalMapper.insert(buildJournal(RESERVE, "unknown-schema-cmd", SHA_A, 2,
                "{\"reserveId\":123}"));

        assertThatThrownBy(() -> queryService.query(RESERVE, "unknown-schema-cmd"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unknown resultSchemaVersion: 2");
    }

    @Test
    void corruptedJson_throwsIllegalState() {
        journalMapper.insert(buildJournal(RESERVE, "corrupted-json-cmd", SHA_A, 1,
                "not valid json"));

        assertThatThrownBy(() -> queryService.query(RESERVE, "corrupted-json-cmd"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Snapshot validation failed");
    }

    // === Cross-tenant isolation (1) ===

    @Test
    void crossTenantIsolation_notFound() {
        String snapshot = codec.serialize(RESERVE, TestSupport.sampleReserveResult());
        journalMapper.insert(buildJournal(RESERVE, "cross-tenant-cmd", SHA_A, 1, snapshot));

        TenantContextHolder.setTenantId(2L);
        CommandStatusResult result = queryService.query(RESERVE, "cross-tenant-cmd");

        assertThat(result).isInstanceOf(CommandStatusResult.NotFound.class);
        CommandStatusResult.NotFound notFound = (CommandStatusResult.NotFound) result;
        assertThat(notFound.operation()).isEqualTo(RESERVE);
        assertThat(notFound.status()).isEqualTo("NOT_FOUND");
        assertThat(notFound.businessCommandId()).isEqualTo("cross-tenant-cmd");
    }

    // === checkedAt from fixed clock (1) ===

    @Test
    void checkedAt_sourcedFromClock() {
        String snapshot = codec.serialize(RESERVE, TestSupport.sampleReserveResult());
        journalMapper.insert(buildJournal(RESERVE, "clock-cmd", SHA_A, 1, snapshot));

        CommandStatusQueryService fixedClockSvc = new CommandStatusQueryService(
                journalMapper, codec, TestSupport.fixedClock());
        CommandStatusResult result = fixedClockSvc.query(RESERVE, "clock-cmd");

        assertThat(result.checkedAt()).isEqualTo(TestSupport.FIXED_TIME);
    }

    // === Mock mapper tests (5) ===

    @Test
    void nullSchemaVersion_throwsIllegalState() {
        SupplychainCommandJournalDO journal = new SupplychainCommandJournalDO();
        journal.setTenantId(1L);
        journal.setOperation(RESERVE.getCode());
        journal.setBusinessCommandId("null-schema-cmd");
        journal.setRequestBodySha256(SHA_A);
        journal.setResultSchemaVersion(null);
        journal.setResultSnapshot("{}");
        journal.setExecutedAt(TestSupport.FIXED_TIME);

        SupplychainCommandJournalMapper mockMapper = mock(SupplychainCommandJournalMapper.class);
        when(mockMapper.selectByTenantOperationCommandId(anyLong(), anyString(), anyString()))
                .thenReturn(journal);

        CommandStatusQueryService svc = queryServiceWithMock(mockMapper);
        assertThatThrownBy(() -> svc.query(RESERVE, "null-schema-cmd"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unknown resultSchemaVersion: null");
    }

    @Test
    void nullExecutedAt_throwsIllegalState() {
        SupplychainCommandJournalDO journal = new SupplychainCommandJournalDO();
        journal.setTenantId(1L);
        journal.setOperation(RESERVE.getCode());
        journal.setBusinessCommandId("null-executed-cmd");
        journal.setRequestBodySha256(SHA_A);
        journal.setResultSchemaVersion(1);
        journal.setResultSnapshot("{\"reserveId\":123}");
        journal.setExecutedAt(null);

        SupplychainCommandJournalMapper mockMapper = mock(SupplychainCommandJournalMapper.class);
        when(mockMapper.selectByTenantOperationCommandId(anyLong(), anyString(), anyString()))
                .thenReturn(journal);

        CommandStatusQueryService svc = queryServiceWithMock(mockMapper);
        assertThatThrownBy(() -> svc.query(RESERVE, "null-executed-cmd"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("executedAt must not be null");
    }

    @Test
    void tenantContextNull_throwsIllegalState() {
        TenantContextHolder.clear();

        SupplychainCommandJournalMapper mockMapper = mock(SupplychainCommandJournalMapper.class);
        CommandStatusQueryService svc = queryServiceWithMock(mockMapper);

        assertThatThrownBy(() -> svc.query(RESERVE, "no-tenant-cmd"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Valid tenant context required (got: null)");
        verify(mockMapper, never()).selectByTenantOperationCommandId(
                anyLong(), anyString(), anyString());
    }

    @Test
    void tenantContextNonPositive_throwsIllegalState() {
        TenantContextHolder.setTenantId(0L);

        SupplychainCommandJournalMapper mockMapper = mock(SupplychainCommandJournalMapper.class);
        CommandStatusQueryService svc = queryServiceWithMock(mockMapper);

        assertThatThrownBy(() -> svc.query(RESERVE, "tenant-zero-cmd"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Valid tenant context required (got: 0)");
        verify(mockMapper, never()).selectByTenantOperationCommandId(
                anyLong(), anyString(), anyString());
    }

    @Test
    void tenantIgnore_throwsIllegalState() {
        TenantContextHolder.setTenantId(1L);
        TenantContextHolder.setIgnore(true);

        SupplychainCommandJournalMapper mockMapper = mock(SupplychainCommandJournalMapper.class);
        CommandStatusQueryService svc = queryServiceWithMock(mockMapper);

        assertThatThrownBy(() -> svc.query(RESERVE, "ignore-cmd"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tenant ignore flag must not be true for command status query");
        verify(mockMapper, never()).selectByTenantOperationCommandId(
                anyLong(), anyString(), anyString());
    }
}
