package com.geihou.module.finance.order.enums;

import java.util.Objects;

/**
 * Payment method enum (ENUM_PAYMENT_METHOD).
 *
 * <p>Source of truth: Global Enum Table V2, ENUM_PAYMENT_METHOD.
 * 7 values: WECHAT_PAY / ALIPAY / CASH / CARD_BALANCE / COUPON_FULL_DEDUCT /
 * ENTERPRISE_PAY / MIXED.
 *
 * <p>CASH requires manual entry + operation log (不变性 11).
 */
public enum PaymentMethodEnum {

    WECHAT_PAY("WECHAT_PAY", "微信支付"),
    ALIPAY("ALIPAY", "支付宝"),
    CASH("CASH", "现金"),
    CARD_BALANCE("CARD_BALANCE", "储值卡"),
    COUPON_FULL_DEDUCT("COUPON_FULL_DEDUCT", "优惠券抵全款"),
    ENTERPRISE_PAY("ENTERPRISE_PAY", "企业账户支付"),
    MIXED("MIXED", "混合支付");

    private final String code;
    private final String label;

    PaymentMethodEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static PaymentMethodEnum fromCode(String code) {
        Objects.requireNonNull(code, "payment method code must not be null");
        for (PaymentMethodEnum method : values()) {
            if (method.code.equals(code)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Unknown payment method code: " + code);
    }
}
