package com.geihou.common.pojo;

import java.util.Objects;

/**
 * Generic REST result wrapper.
 *
 * @param <T> response data type
 */
public final class CommonResult<T> {

    public static final int SUCCESS_CODE = 0;
    public static final String SUCCESS_MSG = "success";

    private final Integer code;
    private final String msg;
    private final T data;

    private CommonResult(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> CommonResult<T> success(T data) {
        return new CommonResult<>(SUCCESS_CODE, SUCCESS_MSG, data);
    }

    public static <T> CommonResult<T> error(Integer code, String msg) {
        Objects.requireNonNull(code, "code must not be null");
        if (code == SUCCESS_CODE) {
            throw new IllegalArgumentException("error code must not be 0");
        }
        if (msg == null || msg.isBlank()) {
            throw new IllegalArgumentException("msg must not be blank");
        }
        return new CommonResult<>(code, msg, null);
    }

    public Integer getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public T getData() {
        return data;
    }
}
