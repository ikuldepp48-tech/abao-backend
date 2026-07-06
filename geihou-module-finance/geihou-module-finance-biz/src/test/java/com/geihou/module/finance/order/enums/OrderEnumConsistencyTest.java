package com.geihou.module.finance.order.enums;

import com.geihou.module.finance.api.order.enums.OrderChannelEnum;
import com.geihou.module.finance.api.order.enums.OrderStatusEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Order enum consistency test.
 *
 * <p>Verifies that OrderStatusEnum and OrderChannelEnum values match
 * the global enum table (ENUM_ORDER_STATUS 12 values, ENUM_ORDER_CHANNEL 9 values).
 */
class OrderEnumConsistencyTest {

    @Test
    void orderStatusEnumHas12Values() {
        assertThat(OrderStatusEnum.values()).hasSize(12);
    }

    @Test
    void orderStatusEnumContainsAllExpectedValues() {
        assertThat(OrderStatusEnum.fromCode("PENDING")).isEqualTo(OrderStatusEnum.PENDING);
        assertThat(OrderStatusEnum.fromCode("PAID")).isEqualTo(OrderStatusEnum.PAID);
        assertThat(OrderStatusEnum.fromCode("ACCEPTED")).isEqualTo(OrderStatusEnum.ACCEPTED);
        assertThat(OrderStatusEnum.fromCode("PREPARING")).isEqualTo(OrderStatusEnum.PREPARING);
        assertThat(OrderStatusEnum.fromCode("READY")).isEqualTo(OrderStatusEnum.READY);
        assertThat(OrderStatusEnum.fromCode("DELIVERING")).isEqualTo(OrderStatusEnum.DELIVERING);
        assertThat(OrderStatusEnum.fromCode("DELIVERED")).isEqualTo(OrderStatusEnum.DELIVERED);
        assertThat(OrderStatusEnum.fromCode("COMPLETED")).isEqualTo(OrderStatusEnum.COMPLETED);
        assertThat(OrderStatusEnum.fromCode("REFUNDING")).isEqualTo(OrderStatusEnum.REFUNDING);
        assertThat(OrderStatusEnum.fromCode("REFUNDED")).isEqualTo(OrderStatusEnum.REFUNDED);
        assertThat(OrderStatusEnum.fromCode("CANCELLED")).isEqualTo(OrderStatusEnum.CANCELLED);
        assertThat(OrderStatusEnum.fromCode("EXPIRED")).isEqualTo(OrderStatusEnum.EXPIRED);
    }

    @Test
    void orderStatusEnumDoesNotContainOldPENDING_PAY() {
        assertThatThrownBy(() -> OrderStatusEnum.fromCode("PENDING_PAY"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderStatusEnumDoesNotContainOldIN_KITCHEN() {
        assertThatThrownBy(() -> OrderStatusEnum.fromCode("IN_KITCHEN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderChannelEnumHas9Values() {
        assertThat(OrderChannelEnum.values()).hasSize(9);
    }

    @Test
    void orderChannelEnumContainsAllExpectedValues() {
        assertThat(OrderChannelEnum.fromCode("DINE_IN")).isEqualTo(OrderChannelEnum.DINE_IN);
        assertThat(OrderChannelEnum.fromCode("SELF_PICKUP")).isEqualTo(OrderChannelEnum.SELF_PICKUP);
        assertThat(OrderChannelEnum.fromCode("MEITUAN_TAKEOUT")).isEqualTo(OrderChannelEnum.MEITUAN_TAKEOUT);
        assertThat(OrderChannelEnum.fromCode("ELEME_TAKEOUT")).isEqualTo(OrderChannelEnum.ELEME_TAKEOUT);
        assertThat(OrderChannelEnum.fromCode("DOUYIN_GROUP")).isEqualTo(OrderChannelEnum.DOUYIN_GROUP);
        assertThat(OrderChannelEnum.fromCode("MEITUAN_GROUP")).isEqualTo(OrderChannelEnum.MEITUAN_GROUP);
        assertThat(OrderChannelEnum.fromCode("WX_PRIVATE")).isEqualTo(OrderChannelEnum.WX_PRIVATE);
        assertThat(OrderChannelEnum.fromCode("OWN_TAKEOUT")).isEqualTo(OrderChannelEnum.OWN_TAKEOUT);
        assertThat(OrderChannelEnum.fromCode("OTHER")).isEqualTo(OrderChannelEnum.OTHER);
    }

    @Test
    void orderChannelEnumDoesNotContainOldTAKEAWAY() {
        assertThatThrownBy(() -> OrderChannelEnum.fromCode("TAKEAWAY"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderChannelEnumDoesNotContainOldMEITUAN() {
        assertThatThrownBy(() -> OrderChannelEnum.fromCode("MEITUAN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderStatusFromCodeNullThrowsNPE() {
        assertThatThrownBy(() -> OrderStatusEnum.fromCode(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void orderChannelFromCodeNullThrowsNPE() {
        assertThatThrownBy(() -> OrderChannelEnum.fromCode(null))
                .isInstanceOf(NullPointerException.class);
    }
}
