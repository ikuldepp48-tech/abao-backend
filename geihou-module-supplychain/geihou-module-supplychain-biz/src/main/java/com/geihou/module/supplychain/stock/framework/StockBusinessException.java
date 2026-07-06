package com.geihou.module.supplychain.stock.framework;

import com.geihou.common.error.ErrorCode;

/**
 * Business exception for the stock module.
 * Wraps an ErrorCode with its code and message.
 */
public class StockBusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public StockBusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }

    public StockBusinessException(ErrorCode errorCode, String detail) {
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
