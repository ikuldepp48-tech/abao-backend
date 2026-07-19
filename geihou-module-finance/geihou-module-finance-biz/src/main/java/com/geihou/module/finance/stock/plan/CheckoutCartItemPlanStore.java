package com.geihou.module.finance.stock.plan;

import com.geihou.module.finance.stock.plan.dal.dataobject.CheckoutCartItemPlanDO;

import java.util.List;

/**
 * Durable, write-once stock classification plan store for checkout cart
 * items (G0-04H185 FIN-CONSISTENCY slice 2B).
 *
 * <p>Each {@code (checkout_session, cart_item)} pair gets exactly one
 * plan row. The classification is one of {@code BOM}, {@code NON_BOM},
 * {@code UNMAPPED} and is never updated after creation. Downstream
 * stages MUST read the plan and MUST NOT re-derive classification.
 *
 * <p>All methods are tenant-scoped: every SQL includes {@code tenant_id}.
 */
public interface CheckoutCartItemPlanStore {

    /**
     * Create a new plan row or return the existing one if the same
     * {@code (tenantId, checkoutSessionId, cartItemId)} already exists.
     *
     * <p>If a row already exists, all immutable fields
     * {@code (checkoutSessionId, cartItemId, skuId, classification)}
     * must match exactly - otherwise {@link IllegalStateException} is
     * thrown.
     *
     * <p>Uses the unique-key constraint
     * {@code uk_ccip_tenant_session_cart} as the race synchronization
     * point. On {@code DuplicateKeyException}, the row is re-selected
     * and verified.
     */
    CheckoutCartItemPlanDO createOrGet(CheckoutCartItemPlanCreate command);

    /**
     * List all plan rows for a checkout session, ordered by
     * {@code cart_item_id ASC, id ASC} for deterministic iteration.
     *
     * <p>Returns an empty list when no plans exist for the session.
     */
    List<CheckoutCartItemPlanDO> listBySession(long tenantId, long checkoutSessionId);
}
