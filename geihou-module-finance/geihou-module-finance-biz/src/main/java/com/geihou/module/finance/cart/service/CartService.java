package com.geihou.module.finance.cart.service;

import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartVO;

/**
 * Cart service interface for customer and staff-assisted cart operations.
 *
 * <p>G1-04A: 5 customer cart endpoints (current, add item, update quantity,
 * remove item, clear cart). All operations are @Transactional with triple-write
 * (cart + cart_item + cart_event_log) per Cart root-cause 3.
 *
 * <p>G1-04F: 5 staff-assisted cart endpoints mirroring the customer surface.
 * Staff methods accept {@code staffUserId} as an internal parameter (from
 * GeihouSecurityContextHolder, never from HTTP input). Staff-touched carts
 * are marked {@code isStaffAssisted = true} with {@code assistedByUserId}
 * set to the authenticated staff id. Staff event logs use
 * {@code operatorRole = "STAFF"}.
 *
 * <p>CG-5 degradation: No stock check, only ProductApi.getSku() for SKU existence/status.
 */
public interface CartService {

    /**
     * Get the current active cart for the customer, creating one if none exists.
     *
     * @param customerUserId customer user ID (from JWT claims)
     * @param shopId         shop ID (from header)
     * @param channel        order channel (ENUM_ORDER_CHANNEL 9 values)
     * @return CartVO with items
     */
    CartVO getCurrentCart(Long customerUserId, Long shopId, String channel);

    /**
     * Add an item to the cart. Creates cart if none exists, reuses ACTIVE cart if exists.
     *
     * @param customerUserId customer user ID
     * @param shopId         shop ID
     * @param channel        order channel
     * @param reqVO          add item request
     * @return updated CartVO
     */
    CartVO addItem(Long customerUserId, Long shopId, String channel, CartAddItemReqVO reqVO);

    /**
     * Update quantity of an existing cart item.
     *
     * <p>G1-04A follow-up hardening: shopId is validated against the cart's shopId
     * to prevent cross-shop mutation when an itemId leaks across shops.
     *
     * @param customerUserId customer user ID
     * @param shopId         shop ID (must match the cart's shopId)
     * @param itemId         cart item ID
     * @param quantity       new quantity (must be >= 1)
     * @return updated CartVO
     */
    CartVO updateQuantity(Long customerUserId, Long shopId, Long itemId, Integer quantity);

    /**
     * Remove an item from the cart (soft delete).
     *
     * <p>G1-04A follow-up hardening: shopId is validated against the cart's shopId
     * to prevent cross-shop mutation when an itemId leaks across shops.
     *
     * @param customerUserId customer user ID
     * @param shopId         shop ID (must match the cart's shopId)
     * @param itemId         cart item ID
     * @return updated CartVO
     */
    CartVO removeItem(Long customerUserId, Long shopId, Long itemId);

    /**
     * Clear all items from the cart (soft delete all items).
     *
     * @param customerUserId customer user ID
     * @param shopId         shop ID
     * @return updated CartVO (empty)
     */
    CartVO clearCart(Long customerUserId, Long shopId);

    /**
     * Abandon old ACTIVE carts across all tenants.
     *
     * <p>G1-04E: Called by {@link com.geihou.module.finance.cart.job.CartAbandonmentJob}.
     * Scans for ACTIVE carts with {@code last_activity_time < now() - 24 hours} and transitions
     * them to ABANDONED, writing a CART_EXPIRED event log with operator_role = SYSTEM.
     * Cart items are NOT modified — they remain for historical reference.
     *
     * <p>This method is NOT transactional — each cart is abandoned in its own transaction
     * via {@link #abandonCart(Long)}. If one cart fails, others are still processed.
     * Cross-tenant scanning uses TenantContextHolder.setIgnore(true) to bypass tenant SQL filter.
     */
    void abandonOldCarts();

    /**
     * Abandon a single old ACTIVE cart. Transactional.
     *
     * <p>Called by {@link #abandonOldCarts()} via self-injection proxy to ensure
     * per-row transaction demarcation. Re-fetches the cart, validates it is still ACTIVE
     * and old enough, then transitions to ABANDONED with operator_role = SYSTEM.
     *
     * <p>This method is NOT intended for direct controller use. It is exposed on the interface
     * solely to enable Spring proxy-based transaction demarcation.
     *
     * @param cartId cart ID
     */
    void abandonCart(Long cartId);

    // --- Staff-assisted cart operations (G1-04F) ---

    /**
     * Get the current active cart for a customer, marked as staff-assisted.
     *
     * <p>Staff-touched carts are marked {@code isStaffAssisted = true} and
     * {@code assistedByUserId = staffUserId}.
     *
     * @param staffUserId    authenticated staff user ID (from GeihouPrincipal, never HTTP input)
     * @param customerUserId target customer user ID (from HTTP request)
     * @param shopId         shop ID
     * @param channel        order channel
     * @return CartVO with items
     */
    CartVO staffGetCurrentCart(Long staffUserId, Long customerUserId, Long shopId, String channel);

    /**
     * Add an item to a customer's cart as staff. Marks cart as staff-assisted.
     *
     * @param staffUserId    authenticated staff user ID (from GeihouPrincipal)
     * @param customerUserId target customer user ID
     * @param shopId         shop ID
     * @param channel        order channel
     * @param reqVO          add item request
     * @return updated CartVO
     */
    CartVO staffAddItem(Long staffUserId, Long customerUserId, Long shopId, String channel, CartAddItemReqVO reqVO);

    /**
     * Update quantity of a cart item as staff. Marks cart as staff-assisted.
     *
     * @param staffUserId    authenticated staff user ID (from GeihouPrincipal)
     * @param customerUserId target customer user ID
     * @param shopId         shop ID (must match the cart's shopId)
     * @param itemId         cart item ID
     * @param quantity       new quantity (must be >= 1)
     * @return updated CartVO
     */
    CartVO staffUpdateQuantity(Long staffUserId, Long customerUserId, Long shopId, Long itemId, Integer quantity);

    /**
     * Remove an item from a customer's cart as staff. Marks cart as staff-assisted.
     *
     * @param staffUserId    authenticated staff user ID (from GeihouPrincipal)
     * @param customerUserId target customer user ID
     * @param shopId         shop ID (must match the cart's shopId)
     * @param itemId         cart item ID
     * @return updated CartVO
     */
    CartVO staffRemoveItem(Long staffUserId, Long customerUserId, Long shopId, Long itemId);

    /**
     * Clear all items from a customer's cart as staff. Marks cart as staff-assisted.
     *
     * @param staffUserId    authenticated staff user ID (from GeihouPrincipal)
     * @param customerUserId target customer user ID
     * @param shopId         shop ID
     * @return updated CartVO (empty)
     */
    CartVO staffClearCart(Long staffUserId, Long customerUserId, Long shopId);
}
