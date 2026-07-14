package com.geihou.module.supplychain.stock.dal;

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
import org.springframework.dao.DuplicateKeyException;

import javax.sql.DataSource;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SupplychainCommandJournalMapper}.
 *
 * <p>FIN-CONSISTENCY implements the journal infrastructure (C0 introduced the
 * command-status endpoint). Verifies INSERT-only round-trip, uk_tenant_op_cmd
 * uniqueness, tenant isolation, and operation scoping. T2 loser recovery uses
 * regular queries with bounded retry (no pessimistic locking) - tests reflect
 * this query-only model.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:supplychain_command_journal_mapper_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class SupplychainCommandJournalMapperTest {

    @Autowired
    private SupplychainCommandJournalMapper mapper;
    @Autowired
    private DataSource dataSource;

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

    @Test
    void insertAndSelectByTenantOperationCommandId() {
        SupplychainCommandJournalDO journal = buildJournal(
                1L,
                SupplychainCommandOperationEnum.RESERVE.getCode(),
                "cmd-001",
                "a".repeat(64));
        mapper.insert(journal);

        SupplychainCommandJournalDO found = mapper.selectByTenantOperationCommandId(
                1L, SupplychainCommandOperationEnum.RESERVE.getCode(), "cmd-001");
        assertThat(found).isNotNull();
        assertThat(found.getTenantId()).isEqualTo(1L);
        assertThat(found.getOperation()).isEqualTo("RESERVE");
        assertThat(found.getBusinessCommandId()).isEqualTo("cmd-001");
        assertThat(found.getRequestBodySha256()).isEqualTo("a".repeat(64));
    }

    @Test
    void ukTenantOpCmd_duplicateThrows() {
        SupplychainCommandJournalDO j1 = buildJournal(
                1L,
                SupplychainCommandOperationEnum.COMMIT.getCode(),
                "cmd-dup",
                "a".repeat(64));
        mapper.insert(j1);

        // Same (tenant_id, operation, business_command_id) but different sha256.
        // Use a valid 64-char hex string so failure is due to unique key, not column length.
        SupplychainCommandJournalDO j2 = buildJournal(
                1L,
                SupplychainCommandOperationEnum.COMMIT.getCode(),
                "cmd-dup",
                "b".repeat(64));

        assertThatThrownBy(() -> mapper.insert(j2))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void sameCommandIdDifferentTenantAllowed() {
        // Tenant 1 inserts
        TenantContextHolder.setTenantId(1L);
        SupplychainCommandJournalDO j1 = buildJournal(
                1L,
                SupplychainCommandOperationEnum.RESERVE.getCode(),
                "shared-cmd",
                "a".repeat(64));
        mapper.insert(j1);

        // Tenant 2 inserts with SAME command_id - must be allowed (uk includes tenant_id)
        TenantContextHolder.setTenantId(2L);
        SupplychainCommandJournalDO j2 = buildJournal(
                2L,
                SupplychainCommandOperationEnum.RESERVE.getCode(),
                "shared-cmd",
                "b".repeat(64));
        mapper.insert(j2);

        // Tenant 1 sees only its own - switch context back
        TenantContextHolder.setTenantId(1L);
        SupplychainCommandJournalDO found1 = mapper.selectByTenantOperationCommandId(
                1L, SupplychainCommandOperationEnum.RESERVE.getCode(), "shared-cmd");
        assertThat(found1).isNotNull();
        assertThat(found1.getTenantId()).isEqualTo(1L);
        assertThat(found1.getRequestBodySha256()).isEqualTo("a".repeat(64));

        // Tenant 2 sees only its own
        TenantContextHolder.setTenantId(2L);
        SupplychainCommandJournalDO found2 = mapper.selectByTenantOperationCommandId(
                2L, SupplychainCommandOperationEnum.RESERVE.getCode(), "shared-cmd");
        assertThat(found2).isNotNull();
        assertThat(found2.getTenantId()).isEqualTo(2L);
        assertThat(found2.getRequestBodySha256()).isEqualTo("b".repeat(64));
    }

    @Test
    void sameCommandIdDifferentOperationAllowed() {
        SupplychainCommandJournalDO j1 = buildJournal(
                1L,
                SupplychainCommandOperationEnum.RESERVE.getCode(),
                "cmd-multi",
                "a".repeat(64));
        mapper.insert(j1);

        // Same (tenant_id, business_command_id) but different operation - must be allowed
        SupplychainCommandJournalDO j2 = buildJournal(
                1L,
                SupplychainCommandOperationEnum.RELEASE.getCode(),
                "cmd-multi",
                "b".repeat(64));
        mapper.insert(j2);

        SupplychainCommandJournalDO found1 = mapper.selectByTenantOperationCommandId(
                1L, SupplychainCommandOperationEnum.RESERVE.getCode(), "cmd-multi");
        assertThat(found1).isNotNull();
        assertThat(found1.getOperation()).isEqualTo("RESERVE");

        SupplychainCommandJournalDO found2 = mapper.selectByTenantOperationCommandId(
                1L, SupplychainCommandOperationEnum.RELEASE.getCode(), "cmd-multi");
        assertThat(found2).isNotNull();
        assertThat(found2.getOperation()).isEqualTo("RELEASE");
    }

    @Test
    void tenantIsolation_wrongTenantReturnsNull() {
        SupplychainCommandJournalDO journal = buildJournal(
                1L,
                SupplychainCommandOperationEnum.RESERVE.getCode(),
                "cmd-iso",
                "a".repeat(64));
        mapper.insert(journal);

        // Query with wrong tenant returns null
        SupplychainCommandJournalDO found = mapper.selectByTenantOperationCommandId(
                999L, SupplychainCommandOperationEnum.RESERVE.getCode(), "cmd-iso");
        assertThat(found).isNull();
    }

    @Test
    void roundTrip_allFields() {
        String snapshot = "{\"eventId\":12345,\"balanceAfter\":100.0000}";
        LocalDateTime executedAt = LocalDateTime.of(2026, 7, 13, 10, 30, 0, 123000000);

        SupplychainCommandJournalDO journal = new SupplychainCommandJournalDO();
        journal.setTenantId(1L);
        journal.setOperation(SupplychainCommandOperationEnum.SALES_OUT_BOM_REVERSE.getCode());
        journal.setBusinessCommandId("cmd-rt");
        journal.setRequestBodySha256("c".repeat(64));
        journal.setResultSchemaVersion(2);
        journal.setResultSnapshot(snapshot);
        journal.setExecutedAt(executedAt);

        mapper.insert(journal);

        SupplychainCommandJournalDO found = mapper.selectByTenantOperationCommandId(
                1L, SupplychainCommandOperationEnum.SALES_OUT_BOM_REVERSE.getCode(), "cmd-rt");
        assertThat(found).isNotNull();
        assertThat(found.getId()).isNotNull();
        assertThat(found.getTenantId()).isEqualTo(1L);
        assertThat(found.getOperation()).isEqualTo("SALES_OUT_BOM_REVERSE");
        assertThat(found.getBusinessCommandId()).isEqualTo("cmd-rt");
        assertThat(found.getRequestBodySha256()).isEqualTo("c".repeat(64));
        assertThat(found.getResultSchemaVersion()).isEqualTo(2);
        assertThat(found.getResultSnapshot()).isEqualTo(snapshot);
        assertThat(found.getExecutedAt()).isEqualTo(executedAt);
        assertThat(found.getCreateTime()).isNotNull();
    }

    @Test
    void roundTrip_defaultSchemaVersion() {
        // Do NOT set resultSchemaVersion - DDL default is 1
        SupplychainCommandJournalDO journal = new SupplychainCommandJournalDO();
        journal.setTenantId(1L);
        journal.setOperation(SupplychainCommandOperationEnum.OBSERVE_MISSING_MAPPING.getCode());
        journal.setBusinessCommandId("cmd-default");
        journal.setRequestBodySha256("d".repeat(64));
        journal.setResultSnapshot("{}");
        journal.setExecutedAt(LocalDateTime.now());

        mapper.insert(journal);

        SupplychainCommandJournalDO found = mapper.selectByTenantOperationCommandId(
                1L, SupplychainCommandOperationEnum.OBSERVE_MISSING_MAPPING.getCode(), "cmd-default");
        assertThat(found).isNotNull();
        assertThat(found.getResultSchemaVersion()).isEqualTo(1);
    }

    private SupplychainCommandJournalDO buildJournal(Long tenantId, String operation,
                                                     String businessCommandId, String sha256) {
        SupplychainCommandJournalDO journal = new SupplychainCommandJournalDO();
        journal.setTenantId(tenantId);
        journal.setOperation(operation);
        journal.setBusinessCommandId(businessCommandId);
        journal.setRequestBodySha256(sha256);
        journal.setResultSchemaVersion(1);
        journal.setResultSnapshot("{\"ok\":true}");
        journal.setExecutedAt(LocalDateTime.now());
        return journal;
    }
}
