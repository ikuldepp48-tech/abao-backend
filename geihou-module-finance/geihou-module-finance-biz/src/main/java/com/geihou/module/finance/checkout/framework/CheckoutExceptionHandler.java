package com.geihou.module.finance.checkout.framework;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.finance.cart.framework.CartBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Finance checkout exception handler mapping {@link CartBusinessException} to
 * real HTTP status codes.
 *
 * <p>Scope: {@code com.geihou.module.finance.checkout.controller} only. Does not
 * affect cart/order/product controllers or other modules.
 *
 * <p>Mapping:
 * <ul>
 *   <li>{@code 1004080} ({@code CHECKOUT_STOCK_CLASSIFICATION_UNMAPPED}) -&gt;
 *       HTTP 409 Conflict + body {@code code=1004080} + frozen Chinese message.</li>
 *   <li>Other {@code CartBusinessException} codes -&gt; HTTP 200 OK + body
 *       code/msg (preserves existing behavior for non-1004080 errors).</li>
 * </ul>
 *
 * <p>This handler only triggers when {@code CartBusinessException} propagates
 * beyond the controller (i.e., re-thrown from {@code /initiate} catch block for
 * 1004080). Other endpoints catch {@code CartBusinessException} internally and
 * return {@code CommonResult.error} directly, so this handler is not invoked
 * for them.
 *
 * <p>G0-04H185 FIN-CONSISTENCY slice 2C-2C-A2.
 */
@RestControllerAdvice(basePackages = "com.geihou.module.finance.checkout.controller")
public class CheckoutExceptionHandler {

    private static final int UNMAPPED_HTTP_409_CODE = 1004080;

    @ExceptionHandler(CartBusinessException.class)
    public ResponseEntity<CommonResult<?>> handleCartBusinessException(CartBusinessException e) {
        if (e.getCode() == UNMAPPED_HTTP_409_CODE) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(CommonResult.error(e.getCode(), e.getMessage()));
        }
        return ResponseEntity.ok()
                .body(CommonResult.error(e.getCode(), e.getMessage()));
    }
}
