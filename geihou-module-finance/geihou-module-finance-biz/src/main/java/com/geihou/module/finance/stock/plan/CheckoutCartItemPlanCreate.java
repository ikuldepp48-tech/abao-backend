package com.geihou.module.finance.stock.plan;

import com.geihou.module.finance.stock.plan.enums.CheckoutStockClassification;

import java.util.Objects;

/**
 * Immutable create parameters for
 * {@link CheckoutCartItemPlanStore#createOrGet}.
 *
 * <p>Carries the immutable identity of a cart item plan:
 * {@code (tenantId, checkoutSessionId, cartItemId)} is the unique key;
 * {@code skuId} and {@code classification} are the immutable payload
 * that must match exactly if a row already exists.
 *
 * <p>The compact constructor rejects null {@code classification} and
 * requires all four IDs ({@code tenantId}, {@code checkoutSessionId},
 * {@code cartItemId}, {@code skuId}) to be strictly positive.
 */
public record CheckoutCartItemPlanCreate(
        long tenantId,
        long checkoutSessionId,
        long cartItemId,
        long skuId,
        CheckoutStockClassification classification
) {
    public CheckoutCartItemPlanCreate {
        Objects.requireNonNull(classification, "classification must not be null");
        requirePositive(tenantId, "tenantId");
        requirePositive(checkoutSessionId, "checkoutSessionId");
        requirePositive(cartItemId, "cartItemId");
        requirePositive(skuId, "skuId");
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    name + " must be positive: " + value);
        }
    }
}
