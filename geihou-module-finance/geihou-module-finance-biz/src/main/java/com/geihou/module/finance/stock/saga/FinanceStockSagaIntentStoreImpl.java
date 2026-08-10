package com.geihou.module.finance.stock.saga;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockSagaIntentDO;
import com.geihou.module.finance.stock.saga.dal.mapper.FinanceStockSagaIntentMapper;
import com.geihou.module.finance.stock.saga.enums.FinanceStockSagaType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Default {@link FinanceStockSagaIntentStore} implementation backed by
 * {@link FinanceStockSagaIntentMapper}.
 *
 * <p>{@link #createOrGet} uses the unique-key constraint
 * {@code uk_fsi_identity} as the race synchronization point and re-selects
 * on {@link DuplicateKeyException}. {@link #finalizePending} is a single
 * conditional UPDATE whose affected-row count must be exactly one for the
 * store to return {@code true}.
 *
 * <p>Tenant safety: all public methods validate that
 * {@link TenantContextHolder#getTenantId()} matches the {@code tenantId}
 * parameter before any SQL is issued. The MyBatis-Plus tenant interceptor
 * appends {@code AND tenant_id = <context>} to SELECT/UPDATE but uses the
 * DO field on INSERT, so without this guard a mismatched context could let
 * an INSERT land in a different tenant.
 *
 * <p>Every intent read back from the database is re-validated through
 * {@link FinanceStockSagaIntentCreate#validate(FinanceStockSagaIntentDO)}
 * (createOrGet and selectByIdentity return paths), so dirty data cannot
 * bypass create-time validation.
 *
 * <p>G0-04H185 FIN-CONSISTENCY slice 2C-2D.
 */
@Component
public class FinanceStockSagaIntentStoreImpl implements FinanceStockSagaIntentStore {

    private final FinanceStockSagaIntentMapper mapper;

    public FinanceStockSagaIntentStoreImpl(FinanceStockSagaIntentMapper mapper) {
        this.mapper = mapper;
    }

    // ==================== createOrGet ====================

    @Override
    public FinanceStockSagaIntentDO createOrGet(FinanceStockSagaIntentCreate create) {
        Objects.requireNonNull(create, "create must not be null");
        validateTenantContext(create.tenantId());
        FinanceStockSagaIntentDO existing = mapper.selectByIdentity(
                create.tenantId(), create.sagaType().name(), create.sagaId());
        if (existing != null) {
            FinanceStockSagaIntentCreate.validate(existing);
            verifyFrozenFields(existing, create);
            return existing;
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        FinanceStockSagaIntentDO pending = buildPendingDO(create, now);
        try {
            int inserted = mapper.insert(pending);
            if (inserted != 1 || pending.getId() == null) {
                throw new IllegalStateException(
                        "Expected exactly one durable intent insert with generated id, inserted="
                                + inserted + " id=" + pending.getId());
            }
            FinanceStockSagaIntentCreate.validate(pending);
            return pending;
        } catch (DuplicateKeyException e) {
            return handleInsertConflict(create);
        }
    }

    private FinanceStockSagaIntentDO handleInsertConflict(FinanceStockSagaIntentCreate create) {
        FinanceStockSagaIntentDO existing = mapper.selectByIdentityForShare(
                create.tenantId(), create.sagaType().name(), create.sagaId());
        if (existing == null) {
            throw new IllegalStateException(
                    "Concurrent insert vanished after DuplicateKeyException");
        }
        FinanceStockSagaIntentCreate.validate(existing);
        verifyFrozenFields(existing, create);
        return existing;
    }

    // ==================== selectByIdentity ====================

    @Override
    public FinanceStockSagaIntentDO selectByIdentity(long tenantId,
                                                     FinanceStockSagaType sagaType,
                                                     long sagaId) {
        validateTenantContext(tenantId);
        requirePositive(tenantId, "tenantId");
        requirePositive(sagaId, "sagaId");
        requireCheckoutSagaType(sagaType);
        FinanceStockSagaIntentDO intent =
                mapper.selectByIdentity(tenantId, sagaType.name(), sagaId);
        if (intent != null) {
            FinanceStockSagaIntentCreate.validate(intent);
        }
        return intent;
    }

    @Override
    public FinanceStockSagaIntentDO selectByIdentityForUpdate(
            long tenantId, FinanceStockSagaType sagaType, long sagaId) {
        validateTenantContext(tenantId);
        requirePositive(tenantId, "tenantId");
        requirePositive(sagaId, "sagaId");
        requireCheckoutSagaType(sagaType);
        FinanceStockSagaIntentDO intent = mapper.selectByIdentityForUpdate(
                tenantId, sagaType.name(), sagaId);
        if (intent != null) {
            FinanceStockSagaIntentCreate.validate(intent);
        }
        return intent;
    }

    // ==================== finalizePending ====================

    @Override
    public boolean finalizePending(long tenantId, FinanceStockSagaType sagaType,
                                   long sagaId, LocalDateTime now) {
        validateTenantContext(tenantId);
        requirePositive(tenantId, "tenantId");
        requirePositive(sagaId, "sagaId");
        requireCheckoutSagaType(sagaType);
        Objects.requireNonNull(now, "now must not be null");
        int updated = mapper.finalizePending(tenantId, sagaType.name(), sagaId, now);
        return updated == 1;
    }

    // ==================== Internal helpers ====================

    /**
     * Fail-closed guard for the createOrGet contract: when an intent already
     * exists for the identity, its frozen terminalization fields must match
     * the create parameters exactly - otherwise IllegalStateException.
     */
    private static void verifyFrozenFields(FinanceStockSagaIntentDO existing,
                                           FinanceStockSagaIntentCreate create) {
        requireSame(existing.getCartId(), create.cartId(), "cartId");
        requireSame(existing.getExpectedCheckoutStatus(),
                create.expectedCheckoutStatus(), "expectedCheckoutStatus");
        requireSame(existing.getTargetCheckoutStatus(),
                create.targetCheckoutStatus(), "targetCheckoutStatus");
        requireSame(existing.getCartEventType(),
                create.cartEventType(), "cartEventType");
        requireSame(existing.getOperatorUserId(), create.operatorUserId(), "operatorUserId");
        requireSame(existing.getOperatorRole(), create.operatorRole(), "operatorRole");
    }

    private static void requireSame(Object existing, Object provided, String field) {
        if (!Objects.equals(existing, provided)) {
            throw new IllegalStateException(
                    "Intent already exists with different " + field
                            + ": existing=" + existing + ", provided=" + provided);
        }
    }

    private FinanceStockSagaIntentDO buildPendingDO(FinanceStockSagaIntentCreate create,
                                                    LocalDateTime now) {
        FinanceStockSagaIntentDO DO = new FinanceStockSagaIntentDO();
        DO.setTenantId(create.tenantId());
        DO.setSagaType(create.sagaType());
        DO.setSagaId(create.sagaId());
        DO.setCartId(create.cartId());
        DO.setExpectedCheckoutStatus(create.expectedCheckoutStatus());
        DO.setTargetCheckoutStatus(create.targetCheckoutStatus());
        DO.setCartEventType(create.cartEventType());
        DO.setOperatorUserId(create.operatorUserId());
        DO.setOperatorRole(create.operatorRole());
        DO.setFinalizationStatus(FINALIZATION_STATUS_PENDING);
        DO.setFinalizedAt(null);
        DO.setCreateTime(now);
        DO.setUpdateTime(now);
        return DO;
    }

    /**
     * Fail-closed guard: the caller's tenant context must match the
     * tenantId parameter before any SQL is issued. This prevents
     * cross-tenant writes when the MyBatis-Plus tenant interceptor
     * only filters SELECT/UPDATE.
     */
    private static void validateTenantContext(long tenantId) {
        Long ctx = TenantContextHolder.getTenantId();
        if (ctx == null || ctx.longValue() != tenantId) {
            throw new IllegalStateException(
                    "Tenant context mismatch: context=" + ctx
                            + ", parameter=" + tenantId);
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    name + " must be positive: " + value);
        }
    }

    private static void requireCheckoutSagaType(FinanceStockSagaType sagaType) {
        if (sagaType != FinanceStockSagaType.CHECKOUT) {
            throw new IllegalArgumentException(
                    "Only CHECKOUT saga type is supported, got: " + sagaType);
        }
    }
}
