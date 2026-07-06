package com.geihou.module.finance.api.order.enums;

import java.util.Objects;

/**
 * Order channel enum (ENUM_ORDER_CHANNEL).
 *
 * <p>Source of truth: Global Enum Table V2, Section B, ENUM_ORDER_CHANNEL.
 * 9 values: DINE_IN / SELF_PICKUP / MEITUAN_TAKEOUT / ELEME_TAKEOUT /
 * DOUYIN_GROUP / MEITUAN_GROUP / WX_PRIVATE / OWN_TAKEOUT / OTHER.
 */
public enum OrderChannelEnum {

    DINE_IN("DINE_IN", "堂食"),
    SELF_PICKUP("SELF_PICKUP", "自提"),
    MEITUAN_TAKEOUT("MEITUAN_TAKEOUT", "美团外卖"),
    ELEME_TAKEOUT("ELEME_TAKEOUT", "饿了么外卖"),
    DOUYIN_GROUP("DOUYIN_GROUP", "抖音团购"),
    MEITUAN_GROUP("MEITUAN_GROUP", "美团团购"),
    WX_PRIVATE("WX_PRIVATE", "私域社群"),
    OWN_TAKEOUT("OWN_TAKEOUT", "自有外卖"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String label;

    OrderChannelEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static OrderChannelEnum fromCode(String code) {
        Objects.requireNonNull(code, "status code must not be null");
        for (OrderChannelEnum channel : values()) {
            if (channel.code.equals(code)) {
                return channel;
            }
        }
        throw new IllegalArgumentException("Unknown order channel code: " + code);
    }
}
