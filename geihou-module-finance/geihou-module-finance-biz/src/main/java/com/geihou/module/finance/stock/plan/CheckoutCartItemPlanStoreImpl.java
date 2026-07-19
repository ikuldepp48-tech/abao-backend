package com.geihou.module.finance.stock.plan;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.stock.plan.dal.dataobject.CheckoutCartItemPlanDO;
import com.geihou.module.finance.stock.plan.dal.mapper.CheckoutCartItemPlanMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Default {@link CheckoutCartItemPlanStore} implementation backed by
 * {@link CheckoutCartItemPlanMapper}.
 *
 * <p>Single-statement writes. {@link #createOrGet} uses the unique-key
 * constraint {@code uk_ccip_tenant_session_cart} as the race
 * synchronization point and re-selects on
 * {@link DuplicateKeyException}.
 *
 * <p>No updates: the plan is write-once. There is no {@code update*}
 * method in this store.
 *
 * <p>Tenant safety: both public methods validate that
 * {@link TenantContextHolder#getTenantId()} matches the tenantId
 * parameter before any SQL is issued. The MyBatis-Plus tenant
 * interceptor appends {@code AND tenant_id = <context>} to SELECTs
 * but uses the DO field on INSERT, so without this guard a mismatched
 * context could let an INSERT land in a different tenant.
 */
@Component
public class CheckoutCartItemPlanStoreImpl implements CheckoutCartItemPlanStore {

    private final CheckoutCartItemPlanMapper mapper;

    public CheckoutCartItemPlanStoreImpl(CheckoutCartItemPlanMapper mapper) {
        this.mapper = mapper;
    }

    // ==================== createOrGet ====================

    @Override
    public CheckoutCartItemPlanDO createOrGet(CheckoutCartItemPlanCreate command) {
        validateTenantContext(command.tenantId());
        CheckoutCartItemPlanDO existing = mapper.selectBySessionAndCartItem(
                command.tenantId(), command.checkoutSessionId(), command.cartItemId());
        if (existing != null) {
            verifyIdentity(existing, command);
            return existing;
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        CheckoutCartItemPlanDO pending = buildDO(command, now);
        try {
            mapper.insert(pending);
            return pending;
        } catch (DuplicateKeyException e) {
            return handleInsertConflict(command);
        }
    }

    private CheckoutCartItemPlanDO handleInsertConflict(CheckoutCartItemPlanCreate command) {
        CheckoutCartItemPlanDO existing = mapper.selectBySessionAndCartItem(
                command.tenantId(), command.checkoutSessionId(), command.cartItemId());
        if (existing == null) {
            throw new IllegalStateException(
                    "Concurrent insert vanished after DuplicateKeyException");
        }
        verifyIdentity(existing, command);
        return existing;
    }

    private void verifyIdentity(CheckoutCartItemPlanDO existing,
                                CheckoutCartItemPlanCreate command) {
        mismatchUnless(existing.getTenantId(), command.tenantId(), "tenantId");
        mismatchUnless(existing.getCheckoutSessionId(), command.checkoutSessionId(),
                "checkoutSessionId");
        mismatchUnless(existing.getCartItemId(), command.cartItemId(), "cartItemId");
        mismatchUnless(existing.getSkuId(), command.skuId(), "skuId");
        mismatchUnless(existing.getClassification(), command.classification().name(),
                "classification");
    }

    private static void mismatchUnless(Object existing, Object provided, String name) {
        if (!Objects.equals(existing, provided)) {
            throw new IllegalStateException(
                    name + " mismatch: existing=" + existing + ", provided=" + provided);
        }
    }

    private CheckoutCartItemPlanDO buildDO(CheckoutCartItemPlanCreate command,
                                           LocalDateTime now) {
        CheckoutCartItemPlanDO DO = new CheckoutCartItemPlanDO();
        DO.setTenantId(command.tenantId());
        DO.setCheckoutSessionId(command.checkoutSessionId());
        DO.setCartItemId(command.cartItemId());
        DO.setSkuId(command.skuId());
        DO.setClassification(command.classification().name());
        DO.setCreateTime(now);
        DO.setUpdateTime(now);
        return DO;
    }

    // ==================== listBySession ====================

    @Override
    public List<CheckoutCartItemPlanDO> listBySession(long tenantId, long checkoutSessionId) {
        validateTenantContext(tenantId);
        requirePositive(tenantId, "tenantId");
        requirePositive(checkoutSessionId, "checkoutSessionId");
        return mapper.listBySession(tenantId, checkoutSessionId);
    }

    // ==================== Internal guards ====================

    /**
     * Fail-closed guard: the caller's tenant context must match the
     * tenantId parameter before any SQL is issued. This prevents
     * cross-tenant writes when the MyBatis-Plus tenant interceptor
     * only filters SELECTs.
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
}
