package com.geihou.module.finance.checkout.service;

import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutInitiateReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutPayReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutSessionVO;

/**
 * Checkout service interface for customer checkout operations.
 *
 * <p>G1-04B: 4 customer checkout endpoints (initiate, query, cancel, simulated pay).
 * All operations are @Transactional — no fire-and-forget writes.
 *
 * <p>CG-7 Option C: No order creation. order_id remains null after simulated pay.
 * CG-8 StockApi degraded: No reserve/commit. Oversell risk documented.
 * CG-9 Simulated local payment bridge: Not real WeChat Pay, no signature verification.
 * CG-11 PromotionApi missing: Reject couponIds with COUPON_INVALID.
 * Session expiry: 5 min per PRD §4.1/§4.2. Lazy check on query/pay + scheduled job (G1-04E).
 */
public interface CheckoutService {

    /**
     * Initiate checkout from an ACTIVE cart.
     *
     * <p>Locks cart as CHECKOUT, generates server-side session token,
     * writes CHECKOUT_STARTED event log. Idempotent via checkout_idempotent.
     *
     * @param reqVO initiate request (must contain idempotentKey, customerUserId, shopId)
     * @return CheckoutSessionVO with INITIATED status
     */
    CheckoutSessionVO initiateCheckout(CheckoutInitiateReqVO reqVO);

    /**
     * Query a checkout session by session token.
     *
     * <p>Lazy expiry check: if session is INITIATED and expired, transitions to EXPIRED,
     * unlocks cart (CHECKOUT → ACTIVE), writes CHECKOUT_ABANDONED event log.
     *
     * @param sessionToken server-generated session token
     * @return CheckoutSessionVO with current status
     */
    CheckoutSessionVO querySession(String sessionToken);

    /**
     * Cancel a checkout session.
     *
     * <p>Transitions session to ABANDONED, unlocks cart (CHECKOUT → ACTIVE),
     * writes CHECKOUT_ABANDONED event log. No expiry check (active operation).
     * Idempotent: if already ABANDONED/EXPIRED/PAID/FAILED, returns current state.
     *
     * @param sessionToken server-generated session token
     * @return CheckoutSessionVO with ABANDONED status
     */
    CheckoutSessionVO cancelSession(String sessionToken);

    /**
     * Simulated local payment for a checkout session.
     *
     * <p>G1-04D: Auto-conversion is always on. After simulated payment succeeds and
     * the transaction commits, {@link #convertToOrder(String)} is called in a separate
     * transaction. If conversion fails, the session remains PAID with orderId = null
     * (per Decision 2: "保金不丢"). The pay response returns HTTP 200 with
     * status = PAID, orderId = null — the client retries via /convert.
     *
     * <p>Idempotent: re-pay on an already-PAID session returns the existing session
     * with orderId (if conversion previously succeeded) without creating a duplicate.
     * If PAID + orderId null (previous conversion failure), re-pay retries conversion.
     *
     * <p>CG-9: Simulated local payment bridge only. NOT real WeChat Pay.
     * No signature verification. No external gateway call.
     *
     * <p>On success: session → PAID, cart → CONVERTED, auto-convert creates order.
     * On failure: session → FAILED, cart → ACTIVE, writes CHECKOUT_ABANDONED event log.
     * Expired sessions are rejected with CHECKOUT_EXPIRED.
     *
     * @param sessionToken server-generated session token
     * @param reqVO        pay request (paymentMethod, optional simulateFail)
     * @return CheckoutSessionVO with PAID or FAILED status
     */
    CheckoutSessionVO paySession(String sessionToken, CheckoutPayReqVO reqVO);

    /**
     * Internal transactional pay method — called by {@link #paySession} via self-injection proxy.
     *
     * <p>G1-04D Decision 1: The pay transaction (INITIATED → PAID) commits in this method.
     * After commit, {@link #paySession} calls {@link #convertToOrder} in a separate transaction.
     * This ensures conversion failure does NOT roll back the PAID state.
     *
     * <p>This method is NOT intended for direct controller use. It is exposed on the interface
     * solely to enable Spring proxy-based transaction demarcation.
     *
     * @param sessionToken server-generated session token
     * @param reqVO        pay request
     * @return CheckoutSessionVO with PAID or FAILED status (orderId is null — conversion happens later)
     */
    CheckoutSessionVO paySessionInternal(String sessionToken, CheckoutPayReqVO reqVO);

    /**
     * Convert a PAID checkout session into a real order.
     *
     * <p>G1-04C: Closes the CG-7 Option C gap by creating and linking exactly one order.
     * Idempotent: if session.orderId is already non-null, returns existing session with orderId.
     *
     * <p>Delegates to {@link com.geihou.module.finance.order.service.OrderService#createFromCheckout}
     * which performs the atomic create-order + link-session in a single transaction.
     *
     * <p>Not @Transactional itself — the transaction boundary is on createFromCheckout.
     * This allows convertToOrder to catch race-condition exceptions and re-query
     * for the winning order without being in a rolled-back transaction.
     *
     * @param sessionToken server-generated session token
     * @return CheckoutSessionVO with orderId populated
     */
    CheckoutSessionVO convertToOrder(String sessionToken);

    /**
     * Expire all overdue INITIATED checkout sessions across all tenants.
     *
     * <p>G1-04E: Called by {@link com.geihou.module.finance.checkout.job.CheckoutSessionExpireJob}.
     * Scans for INITIATED sessions with {@code expire_time < now()} and transitions them to EXPIRED,
     * unlocks the cart (CHECKOUT → ACTIVE), and writes a CHECKOUT_ABANDONED event log with
     * operator_role = SYSTEM.
     *
     * <p>This method is NOT transactional — each session is expired in its own transaction
     * via {@link #expireOverdueSession(Long)}. If one session fails, others are still processed.
     * Cross-tenant scanning uses TenantContextHolder.setIgnore(true) to bypass tenant SQL filter.
     */
    void expireOverdueSessions();

    /**
     * Expire a single overdue checkout session. Transactional.
     *
     * <p>Called by {@link #expireOverdueSessions()} via self-injection proxy to ensure
     * per-row transaction demarcation. Re-fetches the session, validates it is still
     * INITIATED and overdue, then expires it with operator_role = SYSTEM.
     *
     * <p>This method is NOT intended for direct controller use. It is exposed on the interface
     * solely to enable Spring proxy-based transaction demarcation.
     *
     * @param sessionId checkout session ID
     */
    void expireOverdueSession(Long sessionId);

    // --- G1-04J: Staff checkout session methods ---

    /**
     * Staff-initiated checkout from an ACTIVE cart (G1-04J).
     *
     * <p>Reuses customer initiate logic but attributes session creator/updater, cart updater,
     * and event log operator to the authenticated staff user.
     *
     * <p>Event log uses {@code operatorRole = "STAFF"} and {@code operatorUserId = staffUserId}.
     *
     * @param staffUserId authenticated staff user ID (from GeihouSecurityContextHolder, never HTTP)
     * @param reqVO       initiate request (must contain idempotentKey, customerUserId, shopId)
     * @return CheckoutSessionVO with INITIATED status
     */
    CheckoutSessionVO staffInitiateCheckout(Long staffUserId, CheckoutInitiateReqVO reqVO);

    /**
     * Staff query of a checkout session by session token (G1-04J).
     *
     * <p>Readonly on non-expired sessions. If lazy expiry is triggered, the expiry event log
     * is attributed to staff ({@code operatorRole = "STAFF"}, {@code operatorUserId = staffUserId}).
     *
     * @param staffUserId  authenticated staff user ID (from GeihouSecurityContextHolder, never HTTP)
     * @param sessionToken server-generated session token
     * @return CheckoutSessionVO with current status
     */
    CheckoutSessionVO staffQuerySession(Long staffUserId, String sessionToken);

    /**
     * Staff cancel of a checkout session (G1-04J).
     *
     * <p>Reuses customer cancel logic but attributes updater and event log operator to staff.
     * Event log uses {@code operatorRole = "STAFF"} and {@code operatorUserId = staffUserId}.
     * Idempotent: if already in terminal state, returns current state.
     *
     * @param staffUserId  authenticated staff user ID (from GeihouSecurityContextHolder, never HTTP)
     * @param sessionToken server-generated session token
     * @return CheckoutSessionVO with ABANDONED status
     */
    CheckoutSessionVO staffCancelSession(Long staffUserId, String sessionToken);

    // --- G1-04L: Staff checkout pay + convert methods ---

    /**
     * Staff-initiated simulated payment for a checkout session (G1-04L).
     *
     * <p>Reuses customer pay logic but attributes session updater, cart updater,
     * and event log operator to the authenticated staff user.
     *
     * <p>Idempotent: re-pay on an already-PAID session returns the existing session.
     * If PAID + orderId non-null, returns as-is. If PAID + orderId null, retries
     * staff convert.
     *
     * <p>On success: session → PAID, cart → CONVERTED, auto-convert creates order.
     * On failure: session → FAILED, cart → ACTIVE, writes CHECKOUT_ABANDONED with
     * operatorRole = "STAFF".
     * Expired sessions are rejected with CHECKOUT_EXPIRED (lazy expiry attributed to staff).
     *
     * @param staffUserId  authenticated staff user ID (from GeihouSecurityContextHolder, never HTTP)
     * @param sessionToken server-generated session token
     * @param reqVO        pay request (paymentMethod, optional simulateFail)
     * @return CheckoutSessionVO with PAID or FAILED status
     */
    CheckoutSessionVO staffPaySession(Long staffUserId, String sessionToken, CheckoutPayReqVO reqVO);

    /**
     * Internal transactional staff pay method — called by {@link #staffPaySession} via self-injection proxy.
     *
     * <p>Commits the PAID (or FAILED) state in its own transaction. After commit,
     * {@link #staffPaySession} calls {@link #staffConvertToOrder} in a separate transaction.
     * This ensures conversion failure does NOT roll back the PAID state.
     *
     * <p>This method is NOT intended for direct controller use. It is exposed on the interface
     * solely to enable Spring proxy-based transaction demarcation.
     *
     * @param staffUserId  authenticated staff user ID (from GeihouSecurityContextHolder, never HTTP)
     * @param sessionToken server-generated session token
     * @param reqVO        pay request
     * @return CheckoutSessionVO with PAID or FAILED status (orderId is null — conversion happens later)
     */
    CheckoutSessionVO staffPaySessionInternal(Long staffUserId, String sessionToken, CheckoutPayReqVO reqVO);

    /**
     * Staff-initiated conversion of a PAID checkout session into a real order (G1-04L).
     *
     * <p>Delegates to existing {@link #convertToOrder(String)} which calls
     * {@link com.geihou.module.finance.order.service.OrderService#createFromCheckout}.
     * Does not change order attribution in this slice.
     *
     * <p>Idempotent: if session.orderId is already non-null, returns existing session.
     * Non-PAID sessions are rejected.
     *
     * @param staffUserId  authenticated staff user ID (from GeihouSecurityContextHolder, never HTTP)
     * @param sessionToken server-generated session token
     * @return CheckoutSessionVO with orderId populated
     */
    CheckoutSessionVO staffConvertToOrder(Long staffUserId, String sessionToken);
}
