package com.geihou.module.finance.cart.controller.app.customer;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartUpdateQuantityReqVO;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartVO;
import com.geihou.module.finance.cart.framework.CartBusinessException;
import com.geihou.module.finance.cart.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Customer cart controller (G1-04A first code slice).
 *
 * <p>Provides customer-facing cart endpoints.
 * Base path: /app-api/customer/cart
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /app-api/customer/cart/current — Get current active cart</li>
 *   <li>POST /app-api/customer/cart/items — Add item to cart</li>
 *   <li>PUT /app-api/customer/cart/items/{itemId}/quantity — Update item quantity</li>
 *   <li>DELETE /app-api/customer/cart/items/{itemId} — Remove item from cart</li>
 *   <li>DELETE /app-api/customer/cart/clear — Clear cart</li>
 * </ul>
 *
 * <p>No staff/admin cart endpoints in this slice (deferred to G1-04D).
 * No checkout/session/payment endpoints in this slice (deferred to G1-04B).
 */
@RestController
@RequestMapping("/app-api/customer/cart")
public class CustomerCartController {

    private final CartService cartService;

    public CustomerCartController(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * Get the current active cart for the customer.
     *
     * @param customerUserId customer user ID (from query param; auth integration later)
     * @param shopId         shop ID (from header)
     * @param channel        order channel (default DINE_IN)
     * @return CommonResult containing CartVO
     */
    @GetMapping("/current")
    public CommonResult<CartVO> getCurrentCart(
            @RequestParam Long customerUserId,
            @RequestHeader(value = "shop-id", required = false) Long shopId,
            @RequestParam(defaultValue = "DINE_IN") String channel) {
        try {
            CartVO vo = cartService.getCurrentCart(customerUserId,
                    shopId != null ? shopId : 1L, channel);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Add an item to the cart.
     *
     * @param customerUserId customer user ID
     * @param shopId         shop ID (from header)
     * @param channel        order channel (default DINE_IN)
     * @param reqVO          add item request (validated)
     * @return CommonResult containing updated CartVO
     */
    @PostMapping("/items")
    public CommonResult<CartVO> addItem(
            @RequestParam Long customerUserId,
            @RequestHeader(value = "shop-id", required = false) Long shopId,
            @RequestParam(defaultValue = "DINE_IN") String channel,
            @Valid @RequestBody CartAddItemReqVO reqVO) {
        try {
            CartVO vo = cartService.addItem(customerUserId,
                    shopId != null ? shopId : 1L, channel, reqVO);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Update quantity of a cart item.
     *
     * @param customerUserId customer user ID
     * @param itemId         cart item ID
     * @param reqVO          update quantity request (validated)
     * @return CommonResult containing updated CartVO
     */
    @PutMapping("/items/{itemId}/quantity")
    public CommonResult<CartVO> updateQuantity(
            @RequestParam Long customerUserId,
            @RequestHeader(value = "shop-id", required = false) Long shopId,
            @PathVariable Long itemId,
            @Valid @RequestBody CartUpdateQuantityReqVO reqVO) {
        try {
            CartVO vo = cartService.updateQuantity(customerUserId,
                    shopId != null ? shopId : 1L, itemId, reqVO.getQuantity());
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Remove an item from the cart.
     *
     * @param customerUserId customer user ID
     * @param itemId         cart item ID
     * @return CommonResult containing updated CartVO
     */
    @DeleteMapping("/items/{itemId}")
    public CommonResult<CartVO> removeItem(
            @RequestParam Long customerUserId,
            @RequestHeader(value = "shop-id", required = false) Long shopId,
            @PathVariable Long itemId) {
        try {
            CartVO vo = cartService.removeItem(customerUserId,
                    shopId != null ? shopId : 1L, itemId);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Clear all items from the cart.
     *
     * @param customerUserId customer user ID
     * @return CommonResult containing updated (empty) CartVO
     */
    @DeleteMapping("/clear")
    public CommonResult<CartVO> clearCart(
            @RequestParam Long customerUserId,
            @RequestHeader(value = "shop-id", required = false) Long shopId) {
        try {
            CartVO vo = cartService.clearCart(customerUserId,
                    shopId != null ? shopId : 1L);
            return CommonResult.success(vo);
        } catch (CartBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }
}
