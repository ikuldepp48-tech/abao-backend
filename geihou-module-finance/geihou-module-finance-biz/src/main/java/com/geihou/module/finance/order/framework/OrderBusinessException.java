package com.geihou.module.finance.order.framework;

import com.geihou.common.error.ErrorCode;

/**
 * Business exception for the order module.
 * Wraps an ErrorCode with its code and message.
 */
public class OrderBusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public OrderBusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }

    public OrderBusinessException(ErrorCode errorCode, String detail) {
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
