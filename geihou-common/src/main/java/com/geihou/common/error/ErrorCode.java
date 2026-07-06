package com.geihou.common.error;

import java.util.Objects;

/**
 * Immutable error code value object.
 */
public final class ErrorCode {

    private final Integer code;
    private final String msg;

    public ErrorCode(Integer code, String msg) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        if (msg == null || msg.isBlank()) {
            throw new IllegalArgumentException("msg must not be blank");
        }
        this.msg = msg;
    }

    public Integer getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ErrorCode errorCode)) {
            return false;
        }
        return code.equals(errorCode.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return "ErrorCode{" +
                "code=" + code +
                ", msg='" + msg + '\'' +
                '}';
    }
}
