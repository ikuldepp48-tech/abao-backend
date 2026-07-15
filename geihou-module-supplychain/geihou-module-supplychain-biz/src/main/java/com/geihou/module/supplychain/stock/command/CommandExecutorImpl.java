package com.geihou.module.supplychain.stock.command;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.enums.SupplychainCommandOperationEnum;
import com.geihou.module.supplychain.stock.dal.dataobject.SupplychainCommandJournalDO;
import com.geihou.module.supplychain.stock.dal.mapper.SupplychainCommandJournalMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Journal-backed {@link CommandExecutor} providing at-most-once semantics for
 * finance&rarr;supplychain write commands.
 *
 * <p>Two-tier protocol:
 * <ul>
 *   <li>T1: pre-check journal, run action, and insert the success snapshot all
 *       within a single REQUIRES_NEW transaction. DuplicateKeyException on the
 *       journal unique key triggers T2.</li>
 *   <li>T2: bounded retry poll (per {@link CommandRetryConfig}) with each
 *       SELECT in its own REQUIRES_NEW transaction; replay the winner's
 *       snapshot or signal idempotent conflict.</li>
 * </ul>
 *
 * <p>Tenant context is captured at entry and restored in finally. The executor
 * rejects null/non-positive tenant IDs and ignore-mode at entry, and detects
 * tenant/ignore drift after the action. Sleeper interruptions
 * (IllegalStateException) propagate as-is and are never converted to
 * {@code CONSISTENCY_INTERNAL_ERROR} (2002065).
 */
@Component
public class CommandExecutorImpl implements CommandExecutor {

    private static final int SCHEMA_VERSION = 1;
    private static final Pattern SHA256_HEX_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private final SupplychainCommandJournalMapper journalMapper;
    private final CommandCodec codec;
    private final CommandClock clock;
    private final CommandSleeper sleeper;
    private final CommandRetryConfig retryConfig;
    private final TransactionTemplate transactionTemplate;

    public CommandExecutorImpl(
            SupplychainCommandJournalMapper journalMapper,
            CommandCodec codec,
            CommandClock clock,
            CommandSleeper sleeper,
            CommandRetryConfig retryConfig,
            PlatformTransactionManager transactionManager) {
        this.journalMapper = journalMapper;
        this.codec = codec;
        this.clock = clock;
        this.sleeper = sleeper;
        this.retryConfig = retryConfig;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <R> CommandResult<R> execute(
            SupplychainCommandOperationEnum operation,
            String businessCommandId,
            String trustedBodySha256Hex,
            CommandAction<R> action) {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(action, "action must not be null");

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

        if (trustedBodySha256Hex == null) {
            throw new IllegalArgumentException("trustedBodySha256Hex must not be null");
        }
        if (!SHA256_HEX_PATTERN.matcher(trustedBodySha256Hex).matches()) {
            throw new IllegalArgumentException(
                    "trustedBodySha256Hex must be 64-char lowercase hex");
        }

        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalStateException(
                    "Valid tenant context required (got: " + tenantId + ")");
        }
        if (TenantContextHolder.isIgnore()) {
            throw new IllegalStateException(
                    "Tenant ignore flag must not be true for command execution");
        }

        try {
            TxOutcome<R> outcome = runT1(operation, businessCommandId,
                    trustedBodySha256Hex, action, tenantId);
            if (outcome instanceof Conflict) {
                outcome = runT2(operation, businessCommandId,
                        trustedBodySha256Hex, tenantId);
            }
            return toResult(outcome);
        } finally {
            TenantContextHolder.clear();
            TenantContextHolder.setTenantId(tenantId);
        }
    }

    // --- T1: pre-check + execute + insert (all in one REQUIRES_NEW) ---

    private <R> TxOutcome<R> runT1(
            SupplychainCommandOperationEnum operation,
            String businessCommandId,
            String trustedBodySha256Hex,
            CommandAction<R> action,
            Long tenantId) {
        try {
            return transactionTemplate.execute(status -> {
                TenantContextHolder.setTenantId(tenantId);

                SupplychainCommandJournalDO existing = journalMapper.selectByTenantOperationCommandId(
                        tenantId, operation.getCode(), businessCommandId);
                if (existing != null) {
                    return checkExisting(existing, operation, trustedBodySha256Hex);
                }

                R businessResult = action.run();

                Long currentTenant = TenantContextHolder.getTenantId();
                if (!tenantId.equals(currentTenant)) {
                    throw new IllegalStateException(
                            "Tenant context drifted: expected " + tenantId
                                    + ", got " + currentTenant);
                }
                if (TenantContextHolder.isIgnore()) {
                    throw new IllegalStateException(
                            "Tenant ignore flag drifted during action");
                }

                codec.validateBusinessResultType(operation, businessResult);
                String snapshotJson = codec.serialize(operation, businessResult);

                SnapshotV1.Root root = codec.deserialize(operation, snapshotJson);
                R normalizedResult = CommandSnapshotAdapter.adaptToBusiness(operation, root);

                SupplychainCommandJournalDO journalDO = new SupplychainCommandJournalDO();
                journalDO.setTenantId(tenantId);
                journalDO.setOperation(operation.getCode());
                journalDO.setBusinessCommandId(businessCommandId);
                journalDO.setRequestBodySha256(trustedBodySha256Hex);
                journalDO.setResultSchemaVersion(SCHEMA_VERSION);
                journalDO.setResultSnapshot(snapshotJson);
                LocalDateTime now = clock.now();
                journalDO.setExecutedAt(now);
                journalDO.setCreateTime(now);
                try {
                    journalMapper.insert(journalDO);
                } catch (DuplicateKeyException e) {
                    throw new JournalInsertConflictException(e);
                }

                return new FirstWin<>(normalizedResult);
            });
        } catch (JournalInsertConflictException e) {
            return new Conflict<>();
        }
    }

    // --- T2: bounded retry poll (each SELECT in its own REQUIRES_NEW) ---

    private <R> TxOutcome<R> runT2(
            SupplychainCommandOperationEnum operation,
            String businessCommandId,
            String trustedBodySha256Hex,
            Long tenantId) {
        int maxAttempts = retryConfig.getMaxAttempts();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (attempt > 0) {
                sleeper.sleepMillis(retryConfig.getBackoffMs(attempt));
            }
            SupplychainCommandJournalDO existing = transactionTemplate.execute(status -> {
                TenantContextHolder.setTenantId(tenantId);
                return journalMapper.selectByTenantOperationCommandId(
                        tenantId, operation.getCode(), businessCommandId);
            });
            if (existing != null) {
                return checkExisting(existing, operation, trustedBodySha256Hex);
            }
        }
        throw new StockBusinessException(
                StockErrorCodeConstants.CONSISTENCY_INTERNAL_ERROR);
    }

    // --- Existing journal check ---

    private <R> TxOutcome<R> checkExisting(
            SupplychainCommandJournalDO existing,
            SupplychainCommandOperationEnum operation,
            String trustedBodySha256Hex) {
        if (!trustedBodySha256Hex.equals(existing.getRequestBodySha256())) {
            throw new StockBusinessException(
                    StockErrorCodeConstants.IDEMPOTENT_CONFLICT);
        }
        Integer schemaVersion = existing.getResultSchemaVersion();
        if (schemaVersion == null || schemaVersion != SCHEMA_VERSION) {
            throw new IllegalStateException(
                    "Unknown resultSchemaVersion: " + schemaVersion);
        }
        SnapshotV1.Root root = codec.deserialize(operation, existing.getResultSnapshot());
        R businessResult = CommandSnapshotAdapter.adaptToBusiness(operation, root);
        return new Replay<>(businessResult);
    }

    // --- TxOutcome to CommandResult ---

    private <R> CommandResult<R> toResult(TxOutcome<R> outcome) {
        if (outcome instanceof FirstWin<?> firstWin) {
            @SuppressWarnings("unchecked")
            R value = (R) firstWin.value();
            return CommandResult.firstWin(value);
        }
        if (outcome instanceof Replay<?> replay) {
            @SuppressWarnings("unchecked")
            R value = (R) replay.value();
            return CommandResult.replay(value);
        }
        throw new IllegalStateException("Unexpected TxOutcome: " + outcome);
    }

    // --- Internal types ---

    private sealed interface TxOutcome<R> permits FirstWin, Replay, Conflict {
    }

    private record FirstWin<R>(R value) implements TxOutcome<R> {
    }

    private record Replay<R>(R value) implements TxOutcome<R> {
    }

    private record Conflict<R>() implements TxOutcome<R> {
    }

    private static final class JournalInsertConflictException extends RuntimeException {
        JournalInsertConflictException(Throwable cause) {
            super(cause);
        }
    }
}
