package com.geihou.module.finance.stock.saga;

import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockCommandDO;
import com.geihou.module.finance.stock.saga.dal.mapper.FinanceStockCommandMapper;
import com.geihou.module.finance.stock.saga.enums.FinanceStockSagaType;
import com.geihou.module.finance.stock.saga.enums.FinanceStockTransportMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Default {@link FinanceStockReserveCompletionService} implementation.
 *
 * <p>Single Spring bean; the transaction entry point is this class's public
 * {@code @Transactional} methods. No {@code REQUIRES_NEW}: the parent CAS
 * and the child RELEASE {@code createOrGet} share one transaction so that
 * any exception from {@code createOrGet} rolls back the parent's SUCCEEDED
 * transition.
 *
 * <p>Only RESERVE parents are accepted. Non-RESERVE parents throw
 * {@link IllegalArgumentException} before the CAS, so no side effect occurs.
 *
 * <p>Structural violations of {@code releaseCommand} (tenant, saga identity,
 * operation, parentCommandId, businessCommandId, transport, c0) throw
 * {@link IllegalArgumentException} after the CAS but before {@code createOrGet};
 * the surrounding transaction rolls back the CAS.
 */
@Service
@Transactional
public class FinanceStockReserveCompletionServiceImpl
        implements FinanceStockReserveCompletionService {

    private static final String RESERVE = "RESERVE";
    private static final String RELEASE = "RELEASE";

    private final FinanceStockCommandMapper mapper;
    private final FinanceStockCommandStore store;

    public FinanceStockReserveCompletionServiceImpl(FinanceStockCommandMapper mapper,
                                                     FinanceStockCommandStore store) {
        this.mapper = mapper;
        this.store = store;
    }

    @Override
    public boolean completeFromDispatch(long tenantId, long reserveCommandId,
                                        String claimToken, byte[] resultBody,
                                        Integer resultSchemaVersion,
                                        LocalDateTime remoteExecutedAt,
                                        LocalDateTime now,
                                        FinanceStockCommandCreate releaseCommand) {
        return doComplete(true, tenantId, reserveCommandId, claimToken,
                resultBody, resultSchemaVersion, remoteExecutedAt, now,
                releaseCommand);
    }

    @Override
    public boolean completeFromResolution(long tenantId, long reserveCommandId,
                                          String claimToken, byte[] resultBody,
                                          Integer resultSchemaVersion,
                                          LocalDateTime remoteExecutedAt,
                                          LocalDateTime now,
                                          FinanceStockCommandCreate releaseCommand) {
        return doComplete(false, tenantId, reserveCommandId, claimToken,
                resultBody, resultSchemaVersion, remoteExecutedAt, now,
                releaseCommand);
    }

    private boolean doComplete(boolean fromDispatch, long tenantId,
                               long reserveCommandId, String claimToken,
                               byte[] resultBody, Integer resultSchemaVersion,
                               LocalDateTime remoteExecutedAt,
                               LocalDateTime now,
                               FinanceStockCommandCreate releaseCommand) {
        // 1. Pre-read parent to verify it is a RESERVE command in this tenant.
        //    Operation is part of the immutable identity, so this read is safe
        //    against races. Non-RESERVE commands are rejected before the CAS
        //    so no side effect occurs.
        FinanceStockCommandDO pre = mapper.selectByIdTenant(tenantId, reserveCommandId);
        if (pre == null) {
            return false;
        }
        if (!RESERVE.equals(pre.getOperation())) {
            throw new IllegalArgumentException(
                    "Parent command is not RESERVE: operation=" + pre.getOperation()
                            + ", id=" + reserveCommandId);
        }

        // 2. CAS parent RESERVE -> SUCCEEDED.
        int updated;
        if (fromDispatch) {
            updated = mapper.completeSuccess(tenantId, reserveCommandId, claimToken,
                    resultBody, resultSchemaVersion, remoteExecutedAt, now);
        } else {
            updated = mapper.completeSuccessFromUnknown(tenantId, reserveCommandId,
                    claimToken, resultBody, resultSchemaVersion,
                    remoteExecutedAt, now);
        }
        if (updated == 0) {
            return false;
        }

        // 3. Re-read parent (authoritative abort_requested after CAS).
        FinanceStockCommandDO parent = mapper.selectByIdTenant(tenantId, reserveCommandId);
        if (parent == null) {
            throw new IllegalStateException(
                    "Parent command vanished after CAS: id=" + reserveCommandId);
        }

        // 4. No abort -> done, no RELEASE.
        if (!Boolean.TRUE.equals(parent.getAbortRequested())) {
            return true;
        }

        // 5. Abort was requested before success -> create RELEASE child in
        //    the same transaction. Validate structure first, then createOrGet.
        //    Any exception propagates and rolls back the parent CAS.
        validateReleaseCommand(releaseCommand, parent);
        store.createOrGet(releaseCommand);
        return true;
    }

    private void validateReleaseCommand(FinanceStockCommandCreate release,
                                        FinanceStockCommandDO parent) {
        if (release == null) {
            throw new IllegalArgumentException(
                    "releaseCommand must not be null when abort is requested");
        }
        if (release.tenantId() != parent.getTenantId()) {
            throw new IllegalArgumentException(
                    "releaseCommand tenantId mismatch: release=" + release.tenantId()
                            + ", parent=" + parent.getTenantId());
        }
        if (release.sagaType() != FinanceStockSagaType.valueOf(parent.getSagaType())) {
            throw new IllegalArgumentException(
                    "releaseCommand sagaType mismatch: release=" + release.sagaType()
                            + ", parent=" + parent.getSagaType());
        }
        if (release.sagaId() != parent.getSagaId()) {
            throw new IllegalArgumentException(
                    "releaseCommand sagaId mismatch: release=" + release.sagaId()
                            + ", parent=" + parent.getSagaId());
        }
        if (!Objects.equals(release.stepKey(), parent.getStepKey())) {
            throw new IllegalArgumentException(
                    "releaseCommand stepKey mismatch: release=" + release.stepKey()
                            + ", parent=" + parent.getStepKey());
        }
        if (!RELEASE.equals(release.operation())) {
            throw new IllegalArgumentException(
                    "releaseCommand operation must be RELEASE: got=" + release.operation());
        }
        if (!Objects.equals(release.parentCommandId(), parent.getId())) {
            throw new IllegalArgumentException(
                    "releaseCommand parentCommandId mismatch: release="
                            + release.parentCommandId() + ", parent=" + parent.getId());
        }
        if (!Objects.equals(release.businessCommandId(), parent.getBusinessCommandId())) {
            throw new IllegalArgumentException(
                    "releaseCommand businessCommandId mismatch: release="
                            + release.businessCommandId()
                            + ", parent=" + parent.getBusinessCommandId());
        }
        if (release.transportMode() != FinanceStockTransportMode.valueOf(parent.getTransportMode())) {
            throw new IllegalArgumentException(
                    "releaseCommand transportMode mismatch: release=" + release.transportMode()
                            + ", parent=" + parent.getTransportMode());
        }
        if (release.c0JournalAvailable() != parent.getC0JournalAvailable()) {
            throw new IllegalArgumentException(
                    "releaseCommand c0JournalAvailable mismatch: release="
                            + release.c0JournalAvailable()
                            + ", parent=" + parent.getC0JournalAvailable());
        }
    }
}
