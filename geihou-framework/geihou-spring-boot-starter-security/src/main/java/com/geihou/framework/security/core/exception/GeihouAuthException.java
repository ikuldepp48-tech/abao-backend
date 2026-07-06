package com.geihou.framework.security.core.exception;

/**
 * Authentication or authorization failure with a Geihou integer error code.
 */
public class GeihouAuthException extends RuntimeException {

    private final int errorCode;

    public GeihouAuthException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
