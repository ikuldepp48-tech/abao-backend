package com.geihou.module.finance.checkout.controller.app.customer;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.finance.cart.framework.CartBusinessException;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutInitiateReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutPayReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutSessionVO;
import com.geihou.module.finance.checkout.service.CheckoutService;
import org.springframework.web.bind.annotation.*;

/**
 * Customer checkout controller (G1-04B checkout session slice, G1-04D auto-conversion).
 *
 * <p>Provides customer-facing checkout endpoints.
 * Base path: /app-api/customer/checkout
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /app-api/customer/checkout/initiate — Initiate checkout from active cart</li>
 *   <li>GET /app-api/customer/checkout/{sessionToken} — Query checkout session</li>
 *   <li>POST /app-api/customer/checkout/{sessionToken}/cancel — Cancel checkout session</li>
 *   <li>POST /app-api/customer/checkout/{sessionToken}/pay — Simulated local payment (CG-9) with auto-conversion</li>
 *   <li>POST /app-api/customer/checkout/{sessionToken}/convert — Explicit conversion (backfill/retry/idempotent)</li>
 * </ul>
 *
 * <p>NOT implemented (deferred):
 * <ul>
 *   <li>POST /app-api/customer/checkout/payment-callback — CG-9 ruling: no callback endpoint</li>
 * </ul>
 *
 * <p>G1-04D: Auto-conversion is always on. paySession commits PAID first, then calls
 * convertToOrder in a separate transaction. If conversion fails, PAID is preserved
 * (orderId = null). The /convert endpoint allows retry/backfill.
 * <p>CG-8 StockApi degraded: No reserve/commit. Oversell risk documented.
 * <p>CG-9 Simulated local payment bridge: NOT real WeChat Pay, no signature verification.
 * <p>CG-11 PromotionApi missing: Reject couponIds with COUPON_INVALID.
 */
@RestController
@RequestMapping("/app-api/customer/checkout")
public class CustomerCheckoutController {

    private final CheckoutService checkoutService;

    public CustomerCheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    /**
     * Initiate checkout from an ACTIVE cart.
     *
     * <p>Locks cart as CHECKOUT, generates server-side session token,
     * writes CHECKOUT_STARTED event log. Idempotent via idempotentKey.
     *
     * @param reqVO initiate request
     * @return CommonResult containing CheckoutSessionVO with INITIATED status
     */
    @PostMapping("/initiate")
    public CommonResult<CheckoutSessionVO> initiateCheckout(@RequestBody CheckoutInitiateReqVO reqVO) {
        try {
            CheckoutSessionVO vo = checkoutService.initiateCheckout(reqVO);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Query a checkout session by session token.
     *
     * <p>Lazy expiry check: expired INITIATED sessions transition to EXPIRED.
     *
     * @param sessionToken server-generated session token
     * @return CommonResult containing CheckoutSessionVO
     */
    @GetMapping("/{sessionToken}")
    public CommonResult<CheckoutSessionVO> querySession(@PathVariable String sessionToken) {
        try {
            CheckoutSessionVO vo = checkoutService.querySession(sessionToken);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Cancel a checkout session.
     *
     * <p>Transitions session to ABANDONED, unlocks cart (CHECKOUT → ACTIVE).
     *
     * @param sessionToken server-generated session token
     * @return CommonResult containing CheckoutSessionVO with ABANDONED status
     */
    @PostMapping("/{sessionToken}/cancel")
    public CommonResult<CheckoutSessionVO> cancelSession(@PathVariable String sessionToken) {
        try {
            CheckoutSessionVO vo = checkoutService.cancelSession(sessionToken);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Simulated local payment for a checkout session.
     *
     * <p>CG-9: Simulated local payment bridge only.
     * NOT real WeChat Pay. No signature verification.
     * No external gateway call.
     *
     * @param sessionToken server-generated session token
     * @param reqVO        pay request (paymentMethod, optional simulateFail)
     * @return CommonResult containing CheckoutSessionVO with PAID status
     */
    @PostMapping("/{sessionToken}/pay")
    public CommonResult<CheckoutSessionVO> paySession(
            @PathVariable String sessionToken,
            @RequestBody CheckoutPayReqVO reqVO) {
        try {
            CheckoutSessionVO vo = checkoutService.paySession(sessionToken, reqVO);
            // CG-9: If simulated payment failed, return PAYMENT_FAILED error code
            if ("FAILED".equals(vo.getStatus())) {
                return CommonResult.error(1004013, "Payment failed");
            }
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Explicit checkout-to-order conversion (G1-04D).
     *
     * <p>Converts a PAID checkout session into an order. Used for:
     * <ul>
     *   <li>Backfill: PAID sessions from G1-04B that have orderId = null</li>
     *   <li>Retry: PAID sessions where auto-conversion during pay failed</li>
     *   <li>Idempotent re-call: already-converted sessions return existing orderId</li>
     * </ul>
     *
     * <p>G1-04D Decision 3: No request body. Returns 200 with CheckoutSessionVO.
     * <p>G1-04D Decision 7: 200 OK for both first-time and duplicate (idempotent) conversion.
     *
     * <p>Error responses:
     * <ul>
     *   <li>CART_NOT_FOUND (1004020): session token not found or cross-tenant</li>
     *   <li>CHECKOUT_DUPLICATE (1004010): session is not PAID (INITIATED/EXPIRED/ABANDONED/FAILED)</li>
     * </ul>
     *
     * @param sessionToken server-generated session token
     * @return CommonResult containing CheckoutSessionVO with orderId populated
     */
    @PostMapping("/{sessionToken}/convert")
    public CommonResult<CheckoutSessionVO> convertToOrder(@PathVariable String sessionToken) {
        try {
            CheckoutSessionVO vo = checkoutService.convertToOrder(sessionToken);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }
}
