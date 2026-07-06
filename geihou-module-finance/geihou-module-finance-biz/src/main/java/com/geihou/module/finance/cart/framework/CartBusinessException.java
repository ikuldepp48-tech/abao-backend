package com.geihou.module.finance.cart.framework;

import com.geihou.common.error.ErrorCode;

/**
 * Business exception for the cart module.
 * Wraps an ErrorCode with its code and message.
 */
public class CartBusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public CartBusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }

    public CartBusinessException(ErrorCode errorCode, String detail) {
        super(errorCode.getMsg() + ": " + detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Integer getCode() {
        return errorCode.getCode();
    }
}
