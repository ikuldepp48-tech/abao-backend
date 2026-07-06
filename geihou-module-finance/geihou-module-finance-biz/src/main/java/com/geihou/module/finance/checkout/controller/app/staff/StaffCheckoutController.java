package com.geihou.module.finance.checkout.controller.app.staff;

import com.geihou.common.pojo.CommonResult;
import com.geihou.framework.security.core.annotation.RequirePermission;
import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.framework.security.core.context.GeihouSecurityContextHolder;
import com.geihou.module.finance.cart.framework.CartBusinessException;
import com.geihou.module.finance.cart.framework.CartErrorCodeConstants;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutInitiateReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutPayReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutSessionVO;
import com.geihou.module.finance.checkout.service.CheckoutService;
import org.springframework.web.bind.annotation.*;

/**
 * Staff-assisted checkout controller (G1-04J).
 *
 * <p>Provides staff-facing checkout session endpoints: initiate, query, cancel.
 * Base path: /app-api/staff/checkout
 *
 * <p>Security rules:
 * <ul>
 *   <li>Every endpoint carries {@code @RequirePermission("cart:staff-assisted")}.</li>
 *   <li>{@code staffUserId} is extracted exclusively from
 *       {@link GeihouSecurityContextHolder#get()} — never from HTTP
 *       parameters, path variables, or request body fields.</li>
 *   <li>If principal or principal user id is missing, fail closed.</li>
 *   <li>{@code customerUserId} is the target customer and appears in the request body
 *       (as part of {@link CheckoutInitiateReqVO}).</li>
 * </ul>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /app-api/staff/checkout/initiate — Initiate checkout from active cart</li>
 *   <li>GET /app-api/staff/checkout/{sessionToken} — Query checkout session</li>
 *   <li>POST /app-api/staff/checkout/{sessionToken}/cancel — Cancel checkout session</li>
 *   <li>POST /app-api/staff/checkout/{sessionToken}/pay — Simulated payment (G1-04L)</li>
 *   <li>POST /app-api/staff/checkout/{sessionToken}/convert — Convert PAID session to order (G1-04L)</li>
 * </ul>
 */
@RestController
@RequestMapping("/app-api/staff/checkout")
public class StaffCheckoutController {

    private final CheckoutService checkoutService;

    public StaffCheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    /**
     * Resolve the authenticated staff user ID from the security context.
     * Fails closed if principal or user id is missing.
     *
     * @return authenticated staff user ID
     * @throws CartBusinessException if principal is missing or has no user id
     */
    private Long resolveStaffUserId() {
        GeihouPrincipal principal = GeihouSecurityContextHolder.get();
        if (principal == null || principal.userId() == null) {
            throw new CartBusinessException(CartErrorCodeConstants.STAFF_NOT_AUTHENTICATED,
                    "GeihouPrincipal or userId is missing");
        }
        return principal.userId();
    }

    /**
     * Initiate checkout from an ACTIVE cart (staff-assisted).
     *
     * <p>Locks cart as CHECKOUT, generates server-side session token,
     * writes CHECKOUT_STARTED event log with operatorRole = "STAFF".
     * Idempotent via idempotentKey.
     *
     * @param reqVO initiate request (must contain idempotentKey, customerUserId, shopId)
     * @return CommonResult containing CheckoutSessionVO with INITIATED status
     */
    @PostMapping("/initiate")
    @RequirePermission("cart:staff-assisted")
    public CommonResult<CheckoutSessionVO> initiateCheckout(@RequestBody CheckoutInitiateReqVO reqVO) {
        try {
            Long staffUserId = resolveStaffUserId();
            CheckoutSessionVO vo = checkoutService.staffInitiateCheckout(staffUserId, reqVO);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Query a checkout session by session token (staff-assisted).
     *
     * <p>Readonly on non-expired sessions. Lazy expiry check: expired INITIATED sessions
     * transition to EXPIRED with staff-attributed event log.
     *
     * @param sessionToken server-generated session token
     * @return CommonResult containing CheckoutSessionVO
     */
    @GetMapping("/{sessionToken}")
    @RequirePermission("cart:staff-assisted")
    public CommonResult<CheckoutSessionVO> querySession(@PathVariable String sessionToken) {
        try {
            Long staffUserId = resolveStaffUserId();
            CheckoutSessionVO vo = checkoutService.staffQuerySession(staffUserId, sessionToken);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Cancel a checkout session (staff-assisted).
     *
     * <p>Transitions session to ABANDONED, unlocks cart (CHECKOUT → ACTIVE),
     * writes CHECKOUT_ABANDONED event log with operatorRole = "STAFF".
     *
     * @param sessionToken server-generated session token
     * @return CommonResult containing CheckoutSessionVO with ABANDONED status
     */
    @PostMapping("/{sessionToken}/cancel")
    @RequirePermission("cart:staff-assisted")
    public CommonResult<CheckoutSessionVO> cancelSession(@PathVariable String sessionToken) {
        try {
            Long staffUserId = resolveStaffUserId();
            CheckoutSessionVO vo = checkoutService.staffCancelSession(staffUserId, sessionToken);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Simulated payment for a checkout session (staff-assisted, G1-04L).
     *
     * <p>Reuses customer pay logic but attributes session/cart updates and event logs
     * to the authenticated staff user. On success: session → PAID, cart → CONVERTED,
     * then auto-converts to order in a separate transaction. If auto-convert fails,
     * returns PAID with orderId = null so client can retry via /convert.
     *
     * <p>Idempotent: re-pay on already-PAID session returns existing session.
     * On failure: session → FAILED, cart → ACTIVE, CHECKOUT_ABANDONED with STAFF attribution.
     * Expired sessions are rejected with CHECKOUT_EXPIRED (lazy expiry attributed to staff).
     *
     * @param sessionToken server-generated session token
     * @param reqVO        pay request (paymentMethod, optional simulateFail)
     * @return CommonResult containing CheckoutSessionVO with PAID or FAILED status
     */
    @PostMapping("/{sessionToken}/pay")
    @RequirePermission("cart:staff-assisted")
    public CommonResult<CheckoutSessionVO> paySession(@PathVariable String sessionToken,
                                                      @RequestBody CheckoutPayReqVO reqVO) {
        try {
            Long staffUserId = resolveStaffUserId();
            CheckoutSessionVO vo = checkoutService.staffPaySession(staffUserId, sessionToken, reqVO);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Convert a PAID checkout session into a real order (staff-assisted, G1-04L).
     *
     * <p>Delegates to existing orderService.createFromCheckout without modifying order attribution.
     * Idempotent: if session.orderId is already non-null, returns existing session.
     * Non-PAID sessions are rejected.
     *
     * @param sessionToken server-generated session token
     * @return CommonResult containing CheckoutSessionVO with orderId populated
     */
    @PostMapping("/{sessionToken}/convert")
    @RequirePermission("cart:staff-assisted")
    public CommonResult<CheckoutSessionVO> convertToOrder(@PathVariable String sessionToken) {
        try {
            Long staffUserId = resolveStaffUserId();
            CheckoutSessionVO vo = checkoutService.staffConvertToOrder(staffUserId, sessionToken);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }
}
