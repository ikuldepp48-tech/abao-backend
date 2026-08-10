package com.geihou.module.finance.stock.saga;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.cart.dal.dataobject.CartEventLogDO;
import com.geihou.module.finance.cart.dal.mapper.CartEventLogMapper;
import com.geihou.module.finance.cart.dal.mapper.CartMapper;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutSessionDO;
import com.geihou.module.finance.checkout.dal.mapper.CheckoutSessionMapper;
import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockSagaIntentDO;
import com.geihou.module.finance.stock.saga.enums.FinanceStockSagaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Default {@link CheckoutStockSagaFinalizer} implementation.
 *
 * <p>Runs inside a single transaction (see interface javadoc for the exact
 * sequence and the winner/loser race semantics). The {@code checkout_session}
 * status CAS is the synchronization point; a {@code 0}-row CAS is reconciled
 * against current locked intent/session reads before it can be treated as a
 * committed loser result.
 *
 * <p>Tenant safety: {@link #validateTenantContext} runs before any SQL, so a
 * mismatched {@link TenantContextHolder} context cannot leak across tenants.
 *
 * <p>G0-04H185 FIN-CONSISTENCY slice 2C-2D.
 */
@Service
public class CheckoutStockSagaFinalizerImpl implements CheckoutStockSagaFinalizer {

    private final FinanceStockSagaIntentStore intentStore;
    private final CheckoutSessionMapper checkoutSessionMapper;
    private final CartMapper cartMapper;
    private final CartEventLogMapper cartEventLogMapper;

    public CheckoutStockSagaFinalizerImpl(FinanceStockSagaIntentStore intentStore,
                                          CheckoutSessionMapper checkoutSessionMapper,
                                          CartMapper cartMapper,
                                          CartEventLogMapper cartEventLogMapper) {
        this.intentStore = intentStore;
        this.checkoutSessionMapper = checkoutSessionMapper;
        this.cartMapper = cartMapper;
        this.cartEventLogMapper = cartEventLogMapper;
    }

    @Override
    @Transactional
    public boolean finalizeSaga(long tenantId, FinanceStockSagaType sagaType, long sagaId) {
        validateTenantContext(tenantId);
        requirePositive(tenantId, "tenantId");
        requirePositive(sagaId, "sagaId");
        if (sagaType != FinanceStockSagaType.CHECKOUT) {
            throw new IllegalArgumentException(
                    "finalizeSaga only supports CHECKOUT saga type, got: " + sagaType);
        }

        FinanceStockSagaIntentDO intent =
                intentStore.selectByIdentity(tenantId, sagaType, sagaId);
        if (intent == null) {
            throw new IllegalStateException(
                    "No saga intent for tenant=" + tenantId
                            + " sagaType=" + sagaType + " sagaId=" + sagaId);
        }
        String status = intent.getFinalizationStatus();
        if (!(FinanceStockSagaIntentStore.FINALIZATION_STATUS_PENDING.equals(status)
                || FinanceStockSagaIntentStore.FINALIZATION_STATUS_FINALIZED.equals(status))) {
            throw new IllegalStateException(
                    "Intent finalizationStatus must be PENDING or FINALIZED, got: " + status);
        }
        if (FinanceStockSagaIntentStore.FINALIZATION_STATUS_FINALIZED.equals(status)) {
            return false;
        }

        CheckoutSessionDO session = checkoutSessionMapper.selectByIdTenant(tenantId, sagaId);
        if (session == null) {
            throw new IllegalStateException(
                    "No checkout session for tenant=" + tenantId + " id=" + sagaId);
        }
        if (intent.getCartId() == null || session.getCartId() == null
                || intent.getCartId().longValue() != session.getCartId().longValue()) {
            throw new IllegalStateException(
                    "Intent cartId=" + intent.getCartId()
                            + " mismatch with session cartId=" + session.getCartId());
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        String updater = String.valueOf(intent.getOperatorUserId());

        // Synchronization point: whoever CASes the session wins.
        int casRows = checkoutSessionMapper.casTerminalStatus(
                tenantId, sagaId,
                intent.getExpectedCheckoutStatus(), intent.getTargetCheckoutStatus(),
                updater, now);
        if (casRows == 0) {
            FinanceStockSagaIntentDO committedIntent =
                    intentStore.selectByIdentityForUpdate(tenantId, sagaType, sagaId);
            CheckoutSessionDO committedSession =
                    checkoutSessionMapper.selectByIdTenantForUpdate(tenantId, sagaId);
            if (committedIntent != null
                    && FinanceStockSagaIntentStore.FINALIZATION_STATUS_FINALIZED.equals(
                    committedIntent.getFinalizationStatus())
                    && committedSession != null
                    && Objects.equals(committedSession.getStatus(), intent.getTargetCheckoutStatus())) {
                return false;
            }
            throw new IllegalStateException(
                    "Session CAS affected 0 rows without a committed matching finalization: tenant="
                            + tenantId + " sagaId=" + sagaId);
        }
        if (casRows != 1) {
            throw new IllegalStateException(
                    "Expected terminal session CAS to affect exactly one row, updated=" + casRows
                            + " tenant=" + tenantId + " sagaId=" + sagaId);
        }

        int unlockRows = cartMapper.unlockFromCheckout(
                tenantId, session.getCartId(), updater, now);
        if (unlockRows != 1) {
            throw new IllegalStateException(
                    "Expected to unlock exactly one checkout cart, updated=" + unlockRows
                            + " tenant=" + tenantId + " cartId=" + session.getCartId());
        }

        CartEventLogDO event = new CartEventLogDO();
        event.setTenantId(intent.getTenantId());
        event.setCartId(intent.getCartId());
        event.setEventType(intent.getCartEventType());
        event.setEventTime(now);
        event.setOperatorUserId(intent.getOperatorUserId());
        event.setOperatorRole(intent.getOperatorRole());
        event.setCreateTime(now);
        int eventRows = cartEventLogMapper.insert(event);
        if (eventRows != 1) {
            throw new IllegalStateException(
                    "Expected to insert exactly one cart event, inserted=" + eventRows
                            + " tenant=" + tenantId + " cartId=" + intent.getCartId());
        }

        // CAS 0 here means a concurrent finalizer already marked FINALIZED -
        // we must not keep the unlock/event we just wrote, so throw to roll back.
        boolean finalized = intentStore.finalizePending(tenantId, sagaType, sagaId, now);
        if (!finalized) {
            throw new IllegalStateException(
                    "Intent already finalized by concurrent worker: tenant=" + tenantId
                            + " sagaType=" + sagaType + " sagaId=" + sagaId);
        }
        return true;
    }

    // ==================== Internal helpers ====================

    /**
     * Fail-closed guard: the caller's tenant context must match the tenantId
     * parameter before any SQL is issued.
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
