package com.geihou.module.finance.cart.controller.app.staff;

import com.geihou.common.pojo.CommonResult;
import com.geihou.framework.security.core.annotation.RequirePermission;
import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.framework.security.core.context.GeihouSecurityContextHolder;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartUpdateQuantityReqVO;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartVO;
import com.geihou.module.finance.cart.framework.CartBusinessException;
import com.geihou.module.finance.cart.framework.CartErrorCodeConstants;
import com.geihou.module.finance.cart.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Staff-assisted cart controller (G1-04F).
 *
 * <p>Provides staff-facing cart endpoints for assisting customers.
 * Base path: /app-api/staff/cart
 *
 * <p>Security rules:
 * <ul>
 *   <li>Every endpoint carries {@code @RequirePermission("cart:staff-assisted")}.</li>
 *   <li>{@code staffUserId} is extracted exclusively from
 *       {@link GeihouSecurityContextHolder#getPrincipal()} — never from HTTP
 *       parameters, path variables, or request body fields.</li>
 *   <li>If principal or principal user id is missing, fail closed.</li>
 *   <li>{@code customerUserId} is the target customer and may be passed as a
 *       request parameter, following existing customer cart controller style.</li>
 * </ul>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /app-api/staff/cart/current — Get current active cart for a customer</li>
 *   <li>POST /app-api/staff/cart/items — Add item to customer's cart</li>
 *   <li>PUT /app-api/staff/cart/items/{itemId}/quantity — Update item quantity</li>
 *   <li>DELETE /app-api/staff/cart/items/{itemId} — Remove item from customer's cart</li>
 *   <li>DELETE /app-api/staff/cart/clear — Clear customer's cart</li>
 * </ul>
 */
@RestController
@RequestMapping("/app-api/staff/cart")
public class StaffCartController {

    private final CartService cartService;

    public StaffCartController(CartService cartService) {
        this.cartService = cartService;
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
     * Get the current active cart for a customer (staff-assisted).
     *
     * @param customerUserId target customer user ID (from query param)
     * @param shopId         shop ID (from header)
     * @param channel        order channel (default DINE_IN)
     * @return CommonResult containing CartVO
     */
    @GetMapping("/current")
    @RequirePermission("cart:staff-assisted")
    public CommonResult<CartVO> getCurrentCart(
            @RequestParam Long customerUserId,
            @RequestHeader(value = "shop-id", required = false) Long shopId,
            @RequestParam(defaultValue = "DINE_IN") String channel) {
        try {
            Long staffUserId = resolveStaffUserId();
            CartVO vo = cartService.staffGetCurrentCart(staffUserId, customerUserId,
                    shopId != null ? shopId : 1L, channel);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Add an item to a customer's cart (staff-assisted).
     *
     * @param customerUserId target customer user ID
     * @param shopId         shop ID (from header)
     * @param channel        order channel (default DINE_IN)
     * @param reqVO          add item request (validated)
     * @return CommonResult containing updated CartVO
     */
    @PostMapping("/items")
    @RequirePermission("cart:staff-assisted")
    public CommonResult<CartVO> addItem(
            @RequestParam Long customerUserId,
            @RequestHeader(value = "shop-id", required = false) Long shopId,
            @RequestParam(defaultValue = "DINE_IN") String channel,
            @Valid @RequestBody CartAddItemReqVO reqVO) {
        try {
            Long staffUserId = resolveStaffUserId();
            CartVO vo = cartService.staffAddItem(staffUserId, customerUserId,
                    shopId != null ? shopId : 1L, channel, reqVO);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Update quantity of a cart item (staff-assisted).
     *
     * @param customerUserId target customer user ID
     * @param itemId         cart item ID
     * @param reqVO          update quantity request (validated)
     * @return CommonResult containing updated CartVO
     */
    @PutMapping("/items/{itemId}/quantity")
    @RequirePermission("cart:staff-assisted")
    public CommonResult<CartVO> updateQuantity(
            @RequestParam Long customerUserId,
            @RequestHeader(value = "shop-id", required = false) Long shopId,
            @PathVariable Long itemId,
            @Valid @RequestBody CartUpdateQuantityReqVO reqVO) {
        try {
            Long staffUserId = resolveStaffUserId();
            CartVO vo = cartService.staffUpdateQuantity(staffUserId, customerUserId,
                    shopId != null ? shopId : 1L, itemId, reqVO.getQuantity());
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Remove an item from a customer's cart (staff-assisted).
     *
     * @param customerUserId target customer user ID
     * @param itemId         cart item ID
     * @return CommonResult containing updated CartVO
     */
    @DeleteMapping("/items/{itemId}")
    @RequirePermission("cart:staff-assisted")
    public CommonResult<CartVO> removeItem(
            @RequestParam Long customerUserId,
            @RequestHeader(value = "shop-id", required = false) Long shopId,
            @PathVariable Long itemId) {
        try {
            Long staffUserId = resolveStaffUserId();
            CartVO vo = cartService.staffRemoveItem(staffUserId, customerUserId,
                    shopId != null ? shopId : 1L, itemId);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Clear all items from a customer's cart (staff-assisted).
     *
     * @param customerUserId target customer user ID
     * @return CommonResult containing updated (empty) CartVO
     */
    @DeleteMapping("/clear")
    @RequirePermission("cart:staff-assisted")
    public CommonResult<CartVO> clearCart(
            @RequestParam Long customerUserId,
            @RequestHeader(value = "shop-id", required = false) Long shopId) {
        try {
            Long staffUserId = resolveStaffUserId();
            CartVO vo = cartService.staffClearCart(staffUserId, customerUserId,
                    shopId != null ? shopId : 1L);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }
}
