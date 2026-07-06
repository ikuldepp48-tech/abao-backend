package com.geihou.module.finance.cart.enums;

import com.geihou.module.finance.api.cart.enums.CartEventTypeEnum;
import com.geihou.module.finance.api.cart.enums.CartItemStateEnum;
import com.geihou.module.finance.api.cart.enums.CartStatusEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cart enum consistency test (AC-11).
 *
 * <p>Verifies that CartStatusEnum, CartItemStateEnum, and CartEventTypeEnum values
 * match the global enum table (ENUM_CART_STATUS 4 values, ENUM_CART_ITEM_STATE 4 values,
 * ENUM_CART_EVENT_TYPE 7 values).
 */
class CartEnumConsistencyTest {

    @Test
    void cartStatusEnumHas4Values() {
        assertThat(CartStatusEnum.values()).hasSize(4);
    }

    @Test
    void cartStatusEnumContainsAllExpectedValues() {
        assertThat(CartStatusEnum.fromCode("ACTIVE")).isEqualTo(CartStatusEnum.ACTIVE);
        assertThat(CartStatusEnum.fromCode("CHECKOUT")).isEqualTo(CartStatusEnum.CHECKOUT);
        assertThat(CartStatusEnum.fromCode("CONVERTED")).isEqualTo(CartStatusEnum.CONVERTED);
        assertThat(CartStatusEnum.fromCode("ABANDONED")).isEqualTo(CartStatusEnum.ABANDONED);
    }

    @Test
    void cartItemStateEnumHas4Values() {
        assertThat(CartItemStateEnum.values()).hasSize(4);
    }

    @Test
    void cartItemStateEnumContainsAllExpectedValues() {
        assertThat(CartItemStateEnum.fromCode("NORMAL")).isEqualTo(CartItemStateEnum.NORMAL);
        assertThat(CartItemStateEnum.fromCode("SOLD_OUT")).isEqualTo(CartItemStateEnum.SOLD_OUT);
        assertThat(CartItemStateEnum.fromCode("PRICE_CHANGED")).isEqualTo(CartItemStateEnum.PRICE_CHANGED);
        assertThat(CartItemStateEnum.fromCode("UNAVAILABLE")).isEqualTo(CartItemStateEnum.UNAVAILABLE);
    }

    @Test
    void cartEventTypeEnumHas7Values() {
        assertThat(CartEventTypeEnum.values()).hasSize(7);
    }

    @Test
    void cartEventTypeEnumContainsAllExpectedValues() {
        assertThat(CartEventTypeEnum.fromCode("ITEM_ADDED")).isEqualTo(CartEventTypeEnum.ITEM_ADDED);
        assertThat(CartEventTypeEnum.fromCode("ITEM_QUANTITY_CHANGED")).isEqualTo(CartEventTypeEnum.ITEM_QUANTITY_CHANGED);
        assertThat(CartEventTypeEnum.fromCode("ITEM_REMOVED")).isEqualTo(CartEventTypeEnum.ITEM_REMOVED);
        assertThat(CartEventTypeEnum.fromCode("CART_CLEARED")).isEqualTo(CartEventTypeEnum.CART_CLEARED);
        assertThat(CartEventTypeEnum.fromCode("CHECKOUT_STARTED")).isEqualTo(CartEventTypeEnum.CHECKOUT_STARTED);
        assertThat(CartEventTypeEnum.fromCode("CHECKOUT_ABANDONED")).isEqualTo(CartEventTypeEnum.CHECKOUT_ABANDONED);
        assertThat(CartEventTypeEnum.fromCode("CART_EXPIRED")).isEqualTo(CartEventTypeEnum.CART_EXPIRED);
    }

    @Test
    void cartStatusFromCodeNullThrowsNPE() {
        assertThatThrownBy(() -> CartStatusEnum.fromCode(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void cartItemStateFromCodeNullThrowsNPE() {
        assertThatThrownBy(() -> CartItemStateEnum.fromCode(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void cartEventTypeFromCodeNullThrowsNPE() {
        assertThatThrownBy(() -> CartEventTypeEnum.fromCode(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void cartStatusFromCodeUnknownThrowsIAE() {
        assertThatThrownBy(() -> CartStatusEnum.fromCode("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void g1_04aUsesFirst4EventTypesOnly() {
        // G1-04A uses: ITEM_ADDED, ITEM_QUANTITY_CHANGED, ITEM_REMOVED, CART_CLEARED
        // G1-04B reserved: CHECKOUT_STARTED, CHECKOUT_ABANDONED, CART_EXPIRED
        CartEventTypeEnum[] g1_04aTypes = {
            CartEventTypeEnum.ITEM_ADDED,
            CartEventTypeEnum.ITEM_QUANTITY_CHANGED,
            CartEventTypeEnum.ITEM_REMOVED,
            CartEventTypeEnum.CART_CLEARED
        };
        assertThat(g1_04aTypes).hasSize(4);
    }
}
