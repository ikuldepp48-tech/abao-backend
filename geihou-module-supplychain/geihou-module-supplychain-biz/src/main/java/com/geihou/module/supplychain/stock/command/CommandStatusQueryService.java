package com.geihou.module.supplychain.stock.command;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.enums.SupplychainCommandOperationEnum;
import com.geihou.module.supplychain.stock.dal.dataobject.SupplychainCommandJournalDO;
import com.geihou.module.supplychain.stock.dal.mapper.SupplychainCommandJournalMapper;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Read-only query service for command status (C0 section 5.6 /
 * {@code /rpc-api/supplychain/command-status} GET).
 *
 * <p>Single SELECT against {@code supplychain_command_journal} by
 * (tenantId, operation, businessCommandId). No T2 retry, no write, no
 * transaction boundary - the journal is INSERT-only and the query is
 * idempotent.
 *
 * <p>Tenant context captured at entry via {@link TenantContextHolder},
 * matching {@link CommandExecutorImpl}'s fail-closed validation:
 * null/non-positive tenantId and ignore-mode rejected with
 * {@link IllegalStateException}.
 *
 * <p>Schema version gate: only {@code result_schema_version = 1} accepted.
 * ExecutedAt gate: explicit null check before deserialization.
 *
 * <p>Produces {@link CommandStatusResult} - a transport-neutral sealed type.
 * {@link SnapshotV1.Root} appears as a local variable inside {@code query()}
 * (deserialization intermediate) but does not enter the public return type
 * or cross the query-domain boundary. Mapping to wire-level
 * {@code CommandStatusRespDTO} is deferred to FIN-ADAPTER (PROHIBITED).
 */
@Component
public class CommandStatusQueryService {

    private static final int SCHEMA_VERSION = 1;

    private final SupplychainCommandJournalMapper journalMapper;
    private final CommandCodec codec;
    private final CommandClock clock;

    public CommandStatusQueryService(
            SupplychainCommandJournalMapper journalMapper,
            CommandCodec codec,
            CommandClock clock) {
        this.journalMapper = journalMapper;
        this.codec = codec;
        this.clock = clock;
    }

    public CommandStatusResult query(
            SupplychainCommandOperationEnum operation,
            String businessCommandId) {
        Objects.requireNonNull(operation, "operation must not be null");

        if (businessCommandId == null) {
            throw new IllegalArgumentException("businessCommandId must not be null");
        }
        if (businessCommandId.isBlank()) {
            throw new IllegalArgumentException("businessCommandId must not be blank");
        }
        if (businessCommandId.length() > 128) {
            throw new IllegalArgumentException(
                    "businessCommandId must not exceed 128 chars (got "
                            + businessCommandId.length() + ")");
        }

        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalStateException(
                    "Valid tenant context required (got: " + tenantId + ")");
        }
        if (TenantContextHolder.isIgnore()) {
            throw new IllegalStateException(
                    "Tenant ignore flag must not be true for command status query");
        }

        LocalDateTime checkedAt = clock.now();
        SupplychainCommandJournalDO journal = journalMapper.selectByTenantOperationCommandId(
                tenantId, operation.getCode(), businessCommandId);

        if (journal == null) {
            return new CommandStatusResult.NotFound(operation, businessCommandId, checkedAt);
        }

        Integer schemaVersion = journal.getResultSchemaVersion();
        if (schemaVersion == null || schemaVersion != SCHEMA_VERSION) {
            throw new IllegalStateException(
                    "Unknown resultSchemaVersion: " + schemaVersion);
        }

        LocalDateTime executedAt = journal.getExecutedAt();
        if (executedAt == null) {
            throw new IllegalStateException("executedAt must not be null");
        }

        SnapshotV1.Root root = codec.deserialize(operation, journal.getResultSnapshot());
        return CommandSnapshotAdapter.adaptToResult(
                operation, businessCommandId, root, executedAt, checkedAt);
    }
}
