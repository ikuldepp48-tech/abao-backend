package com.geihou.module.finance.stock.saga;

import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockCommandDO;
import com.geihou.module.finance.stock.saga.dal.mapper.FinanceStockCommandMapper;
import com.geihou.module.finance.stock.saga.enums.FinanceStockCommandStatus;
import com.geihou.module.finance.stock.saga.enums.FinanceStockTransportMode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Default {@link FinanceStockCommandStore} implementation backed by
 * {@link FinanceStockCommandMapper}.
 *
 * <p>Single conditional UPDATEs for all state transitions. No
 * select-then-unconditional-update. The only multi-step method is
 * {@link #createOrGet} which uses unique-key constraints as the race
 * synchronization point and re-selects on {@link DuplicateKeyException}.
 *
 * <p>Package-private {@code *FromUnknown} methods delegate to mapper methods
 * with expected_status = UNKNOWN. They are not in the public interface and
 * exist for the UNKNOWN resolution path (next slice).
 */
@Component
public class FinanceStockCommandStoreImpl implements FinanceStockCommandStore {

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final String SHA_256 = "SHA-256";

    private final FinanceStockCommandMapper mapper;

    public FinanceStockCommandStoreImpl(FinanceStockCommandMapper mapper) {
        this.mapper = mapper;
    }

    // ==================== createOrGet ====================

    @Override
    public FinanceStockCommandDO createOrGet(FinanceStockCommandCreate command) {
        validateSha256Hex(command.requestBodySha256());
        validateTransportC0(command.transportMode(), command.c0JournalAvailable());
        String computed = sha256Hex(command.requestBody());
        if (!computed.equals(command.requestBodySha256())) {
            throw new IllegalStateException(
                    "request_body_sha256 does not match SHA-256 of request_body");
        }

        FinanceStockCommandDO remoteExisting = mapper.selectByRemoteIdentity(
                command.tenantId(), command.operation(), command.businessCommandId());
        FinanceStockCommandDO localExisting = mapper.selectByLocalStep(
                command.tenantId(), command.sagaType().name(),
                command.sagaId(), command.stepKey(), command.operation());

        if (remoteExisting != null && localExisting != null) {
            if (!Objects.equals(remoteExisting.getId(), localExisting.getId())) {
                throw new IllegalStateException(
                        "Remote identity and local step hit different records: remoteId="
                                + remoteExisting.getId() + ", localId=" + localExisting.getId());
            }
            verifyIdentity(remoteExisting, command);
            return remoteExisting;
        }
        if (remoteExisting != null) {
            verifyIdentity(remoteExisting, command);
            return remoteExisting;
        }
        if (localExisting != null) {
            verifyIdentity(localExisting, command);
            return localExisting;
        }

        LocalDateTime now = LocalDateTime.now();
        FinanceStockCommandDO pending = buildPendingDO(command, now);
        try {
            mapper.insert(pending);
            return pending;
        } catch (DuplicateKeyException e) {
            return handleInsertConflict(command);
        }
    }

    private FinanceStockCommandDO handleInsertConflict(FinanceStockCommandCreate command) {
        FinanceStockCommandDO remote = mapper.selectByRemoteIdentity(
                command.tenantId(), command.operation(), command.businessCommandId());
        FinanceStockCommandDO local = mapper.selectByLocalStep(
                command.tenantId(), command.sagaType().name(),
                command.sagaId(), command.stepKey(), command.operation());
        if (remote != null && local != null) {
            if (!Objects.equals(remote.getId(), local.getId())) {
                throw new IllegalStateException(
                        "Remote identity and local step hit different records after DuplicateKeyException: remoteId="
                                + remote.getId() + ", localId=" + local.getId());
            }
            verifyIdentity(remote, command);
            return remote;
        }
        if (remote != null) {
            verifyIdentity(remote, command);
            return remote;
        }
        if (local != null) {
            verifyIdentity(local, command);
            return local;
        }
        throw new IllegalStateException(
                "Concurrent insert vanished after DuplicateKeyException");
    }

    private void verifyIdentity(FinanceStockCommandDO existing,
                                FinanceStockCommandCreate command) {
        mismatchUnless(existing.getSagaType(), command.sagaType().name(), "sagaType");
        mismatchUnless(existing.getSagaId(), command.sagaId(), "sagaId");
        mismatchUnless(existing.getStepKey(), command.stepKey(), "stepKey");
        mismatchUnless(existing.getOperation(), command.operation(), "operation");
        mismatchUnless(existing.getBusinessCommandId(), command.businessCommandId(),
                "businessCommandId");
        mismatchUnless(existing.getTransportMode(), command.transportMode().name(),
                "transportMode");
        mismatchUnless(existing.getC0JournalAvailable(), command.c0JournalAvailable(),
                "c0JournalAvailable");
        mismatchUnless(existing.getRequestSchemaVersion(), command.requestSchemaVersion(),
                "requestSchemaVersion");
        if (!Arrays.equals(existing.getRequestBody(), command.requestBody())) {
            throw new IllegalStateException("requestBody mismatch for command id="
                    + existing.getId());
        }
        mismatchUnless(existing.getRequestBodySha256(), command.requestBodySha256(),
                "requestBodySha256");
    }

    private static void mismatchUnless(Object existing, Object provided, String name) {
        if (!Objects.equals(existing, provided)) {
            throw new IllegalStateException(
                    name + " mismatch: existing=" + existing + ", provided=" + provided);
        }
    }

    private FinanceStockCommandDO buildPendingDO(FinanceStockCommandCreate command,
                                                  LocalDateTime now) {
        FinanceStockCommandDO DO = new FinanceStockCommandDO();
        DO.setTenantId(command.tenantId());
        DO.setSagaType(command.sagaType().name());
        DO.setSagaId(command.sagaId());
        DO.setStepKey(command.stepKey());
        DO.setParentCommandId(command.parentCommandId());
        DO.setOperation(command.operation());
        DO.setBusinessCommandId(command.businessCommandId());
        DO.setTransportMode(command.transportMode().name());
        DO.setC0JournalAvailable(command.c0JournalAvailable());
        DO.setRequestSchemaVersion(command.requestSchemaVersion());
        DO.setRequestBody(command.requestBody());
        DO.setRequestBodySha256(command.requestBodySha256());
        DO.setStatus(FinanceStockCommandStatus.PENDING.name());
        DO.setAbortRequested(false);
        DO.setDispatchAttempts(0);
        DO.setResolutionAttempts(0);
        DO.setMaxDispatchAttempts(command.maxDispatchAttempts());
        DO.setMaxResolutionAttempts(command.maxResolutionAttempts());
        DO.setCreateTime(now);
        DO.setUpdateTime(now);
        return DO;
    }

    // ==================== Dispatch CAS ====================

    @Override
    public boolean claimDispatch(long tenantId, long id, String claimToken,
                                 LocalDateTime now, LocalDateTime leaseUntil) {
        requireClaimArgs(claimToken, now, leaseUntil);
        return mapper.claimDispatch(tenantId, id, claimToken, now, leaseUntil) > 0;
    }

    @Override
    public boolean completeSuccess(long tenantId, long id, String claimToken,
                                   byte[] resultBody, Integer resultSchemaVersion,
                                   LocalDateTime remoteExecutedAt, LocalDateTime now) {
        return mapper.completeSuccess(tenantId, id, claimToken, resultBody,
                resultSchemaVersion, remoteExecutedAt, now) > 0;
    }

    @Override
    public boolean scheduleRetry(long tenantId, long id, String claimToken,
                                 LocalDateTime nextAttemptAt, Integer errorCode,
                                 String errorClass, String errorMessage,
                                 LocalDateTime now) {
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return mapper.scheduleRetry(tenantId, id, claimToken, nextAttemptAt,
                errorCode, errorClass, errorMessage, now) > 0;
    }

    @Override
    public boolean markUnknown(long tenantId, long id, String claimToken,
                               LocalDateTime nextAttemptAt, Integer errorCode,
                               String errorClass, String errorMessage,
                               LocalDateTime now) {
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return mapper.markUnknown(tenantId, id, claimToken, nextAttemptAt,
                errorCode, errorClass, errorMessage, now) > 0;
    }

    @Override
    public int expireLeasesToUnknown(long tenantId, LocalDateTime now,
                                     LocalDateTime nextAttemptAt) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt must not be null");
        return mapper.expireLeasesToUnknown(tenantId, now, nextAttemptAt);
    }

    @Override
    public boolean claimResolution(long tenantId, long id, String claimToken,
                                   LocalDateTime now, LocalDateTime leaseUntil) {
        requireClaimArgs(claimToken, now, leaseUntil);
        return mapper.claimResolution(tenantId, id, claimToken, now, leaseUntil) > 0;
    }

    @Override
    public boolean completeNoEffect(long tenantId, long id, String claimToken,
                                    Integer errorCode, String errorClass,
                                    String errorMessage, LocalDateTime now) {
        return mapper.completeNoEffect(tenantId, id, claimToken, errorCode,
                errorClass, errorMessage, now) > 0;
    }

    @Override
    public boolean markStuck(long tenantId, long id, String claimToken,
                             Integer errorCode, String errorClass,
                             String errorMessage, LocalDateTime now) {
        return mapper.markStuck(tenantId, id, claimToken, errorCode,
                errorClass, errorMessage, now) > 0;
    }

    @Override
    public boolean requestAbort(long tenantId, long id, LocalDateTime now) {
        int updated = mapper.cancelUnclaimed(tenantId, id, now);
        if (updated > 0) {
            return true;
        }
        updated = mapper.requestAbortInFlight(tenantId, id, now);
        if (updated > 0) {
            return true;
        }
        FinanceStockCommandDO existing = mapper.selectByIdTenant(tenantId, id);
        return existing != null;
    }

    // ==================== Package-private UNKNOWN resolution writes ====================

    boolean completeSuccessFromUnknown(long tenantId, long id, String claimToken,
                                       byte[] resultBody, Integer resultSchemaVersion,
                                       LocalDateTime remoteExecutedAt, LocalDateTime now) {
        return mapper.completeSuccessFromUnknown(tenantId, id, claimToken, resultBody,
                resultSchemaVersion, remoteExecutedAt, now) > 0;
    }

    boolean completeNoEffectFromUnknown(long tenantId, long id, String claimToken,
                                        Integer errorCode, String errorClass,
                                        String errorMessage, LocalDateTime now) {
        return mapper.completeNoEffectFromUnknown(tenantId, id, claimToken, errorCode,
                errorClass, errorMessage, now) > 0;
    }

    boolean markStuckFromUnknown(long tenantId, long id, String claimToken,
                                 Integer errorCode, String errorClass,
                                 String errorMessage, LocalDateTime now) {
        return mapper.markStuckFromUnknown(tenantId, id, claimToken, errorCode,
                errorClass, errorMessage, now) > 0;
    }

    // ==================== Validation helpers ====================

    private static void requireClaimArgs(String claimToken, LocalDateTime now,
                                         LocalDateTime leaseUntil) {
        if (claimToken == null || claimToken.isBlank()) {
            throw new IllegalArgumentException("claimToken must not be null or blank");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (leaseUntil == null) {
            throw new IllegalArgumentException("leaseUntil must not be null");
        }
        if (!leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("leaseUntil must be after now");
        }
    }

    private static void validateSha256Hex(String hash) {
        if (hash == null || !SHA256_HEX.matcher(hash).matches()) {
            throw new IllegalArgumentException(
                    "requestBodySha256 must be 64-char lowercase hex");
        }
    }

    private static void validateTransportC0(FinanceStockTransportMode mode,
                                            boolean c0Available) {
        if (mode == FinanceStockTransportMode.LOCAL_API_V1 && c0Available) {
            throw new IllegalStateException(
                    "LOCAL_API_V1 + c0_journal_available=true is forbidden");
        }
    }

    private static String sha256Hex(byte[] body) {
        try {
            MessageDigest md = MessageDigest.getInstance(SHA_256);
            byte[] digest = md.digest(body);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
