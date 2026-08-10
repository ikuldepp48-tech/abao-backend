package com.geihou.module.finance.stock.saga;

import com.geihou.module.finance.api.cart.enums.CartEventTypeEnum;
import com.geihou.module.finance.checkout.framework.CheckoutStatusEnum;
import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockSagaIntentDO;
import com.geihou.module.finance.stock.saga.enums.FinanceStockSagaType;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable create parameters for {@link FinanceStockSagaIntentStore#createOrGet}.
 *
 * <p>Frozen terminalization intent for one checkout saga:
 * {@code (tenantId, sagaType, sagaId)} is the unique identity;
 * {@code sagaId} is the {@code checkout_session.id}.
 *
 * <p>The compact constructor freezes the validation contract and the
 * store re-applies the exact same static {@link #validate} on any intent
 * read back from the database, so dirty rows cannot bypass create-time
 * validation:
 * <ul>
 *   <li>{@code sagaType} must be {@code CHECKOUT}.</li>
 *   <li>{@code expectedCheckoutStatus} must be {@code INITIATED}.</li>
 *   <li>{@code targetCheckoutStatus} must be one of
 *       {@code ABANDONED}, {@code EXPIRED}, {@code FAILED}.</li>
 *   <li>{@code cartEventType} is resolved via
 *       {@link CartEventTypeEnum#fromCode} and must pair with the target:
 *       {@code ABANDONED/FAILED -> CHECKOUT_ABANDONED},
 *       {@code EXPIRED -> CART_EXPIRED}.</li>
 *   <li>{@code operatorRole} must be {@code CUSTOMER} or {@code STAFF}.</li>
 *   <li>All string fields reject null/blank; all IDs must be positive.</li>
 * </ul>
 *
 * <p>G0-04H185 FIN-CONSISTENCY slice 2C-2D.
 */
public record FinanceStockSagaIntentCreate(
        long tenantId,
        FinanceStockSagaType sagaType,
        long sagaId,
        long cartId,
        long operatorUserId,
        String operatorRole,
        String expectedCheckoutStatus,
        String targetCheckoutStatus,
        String cartEventType
) {
    private static final Set<String> VALID_OPERATOR_ROLES = Set.of("CUSTOMER", "STAFF");

    public FinanceStockSagaIntentCreate {
        Objects.requireNonNull(sagaType, "sagaType must not be null");
        validate(tenantId, sagaId, cartId, operatorUserId,
                sagaType, expectedCheckoutStatus,
                targetCheckoutStatus, cartEventType, operatorRole);
    }

    /**
     * Shared fail-closed validation used by the compact constructor AND by
     * {@link FinanceStockSagaIntentStoreImpl} on every intent read back from
     * the database (guards against dirty data bypassing Create).
     *
     * @param intent the database row to validate
     */
    static void validate(FinanceStockSagaIntentDO intent) {
        Objects.requireNonNull(intent, "intent must not be null");
        validate(
                intent.getTenantId() == null ? 0L : intent.getTenantId(),
                intent.getSagaId() == null ? 0L : intent.getSagaId(),
                intent.getCartId() == null ? 0L : intent.getCartId(),
                intent.getOperatorUserId() == null ? 0L : intent.getOperatorUserId(),
                intent.getSagaType(), intent.getExpectedCheckoutStatus(),
                intent.getTargetCheckoutStatus(), intent.getCartEventType(),
                intent.getOperatorRole());
        validateFinalizationState(intent);
    }

    private static void validate(long tenantId, long sagaId, long cartId, long operatorUserId,
                                 FinanceStockSagaType sagaType, String expectedCheckoutStatus,
                                 String targetCheckoutStatus, String cartEventType,
                                 String operatorRole) {
        requirePositive(tenantId, "tenantId");
        requirePositive(sagaId, "sagaId");
        requirePositive(cartId, "cartId");
        requirePositive(operatorUserId, "operatorUserId");
        Objects.requireNonNull(sagaType, "sagaType must not be null");
        requireNotBlank(expectedCheckoutStatus, "expectedCheckoutStatus");
        requireNotBlank(targetCheckoutStatus, "targetCheckoutStatus");
        requireNotBlank(cartEventType, "cartEventType");
        requireNotBlank(operatorRole, "operatorRole");

        if (sagaType != FinanceStockSagaType.CHECKOUT) {
            throw new IllegalStateException(
                    "sagaType must be CHECKOUT, got: " + sagaType);
        }
        CheckoutStatusEnum expected = CheckoutStatusEnum.fromCode(expectedCheckoutStatus);
        if (expected != CheckoutStatusEnum.INITIATED) {
            throw new IllegalStateException(
                    "expectedCheckoutStatus must be INITIATED, got: " + expectedCheckoutStatus);
        }
        CheckoutStatusEnum target = CheckoutStatusEnum.fromCode(targetCheckoutStatus);
        if (target == CheckoutStatusEnum.ABANDONED || target == CheckoutStatusEnum.FAILED) {
            requireCartEvent(cartEventType, CartEventTypeEnum.CHECKOUT_ABANDONED);
        } else if (target == CheckoutStatusEnum.EXPIRED) {
            requireCartEvent(cartEventType, CartEventTypeEnum.CART_EXPIRED);
        } else {
            throw new IllegalStateException(
                    "targetCheckoutStatus must be one of ABANDONED/EXPIRED/FAILED, got: "
                            + targetCheckoutStatus);
        }
        if (!VALID_OPERATOR_ROLES.contains(operatorRole)) {
            throw new IllegalStateException(
                    "operatorRole must be CUSTOMER or STAFF, got: " + operatorRole);
        }
    }

    private static void requireCartEvent(String cartEventType,
                                         CartEventTypeEnum expectedEvent) {
        CartEventTypeEnum event = CartEventTypeEnum.fromCode(cartEventType);
        if (event != expectedEvent) {
            throw new IllegalStateException(
                    "target must pair with " + expectedEvent.getCode()
                            + ", got cartEventType: " + cartEventType);
        }
    }

    private static void validateFinalizationState(FinanceStockSagaIntentDO intent) {
        String status = intent.getFinalizationStatus();
        if (FinanceStockSagaIntentDO.FINALIZATION_STATUS_PENDING.equals(status)) {
            if (intent.getFinalizedAt() != null) {
                throw new IllegalStateException(
                        "PENDING intent must have finalizedAt=null");
            }
            return;
        }
        if (FinanceStockSagaIntentDO.FINALIZATION_STATUS_FINALIZED.equals(status)) {
            if (intent.getFinalizedAt() == null) {
                throw new IllegalStateException(
                        "FINALIZED intent must have non-null finalizedAt");
            }
            return;
        }
        throw new IllegalStateException(
                "finalizationStatus must be PENDING or FINALIZED, got: " + status);
    }

    private static void requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
    }
}
