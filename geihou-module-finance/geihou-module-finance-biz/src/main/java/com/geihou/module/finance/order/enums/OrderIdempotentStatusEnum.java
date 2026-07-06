package com.geihou.module.finance.order.enums;

import java.util.Objects;

/**
 * Order idempotent status enum.
 *
 * <p>Values: PROCESSING / SUCCESS / FAILED.
 * Used in order_idempotent.status.
 */
public enum OrderIdempotentStatusEnum {

    PROCESSING("PROCESSING", "处理中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String label;

    OrderIdempotentStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static OrderIdempotentStatusEnum fromCode(String code) {
        Objects.requireNonNull(code, "status code must not be null");
        for (OrderIdempotentStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown idempotent status code: " + code);
    }
}
