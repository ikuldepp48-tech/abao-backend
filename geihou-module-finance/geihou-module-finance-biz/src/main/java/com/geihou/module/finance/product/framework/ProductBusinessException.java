package com.geihou.module.finance.product.framework;

import com.geihou.common.error.ErrorCode;

/**
 * Business exception for the product module.
 * Wraps an ErrorCode with its code and message.
 */
public class ProductBusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public ProductBusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }

    public ProductBusinessException(ErrorCode errorCode, String detail) {
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
