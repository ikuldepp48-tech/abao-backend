package com.geihou.module.finance.checkout.enums;

import com.geihou.module.finance.checkout.framework.CheckoutStatusEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checkout enum consistency test.
 *
 * <p>Verifies ENUM_CHECKOUT_STATUS has exactly 5 values matching global enum table.
 */
class CheckoutEnumConsistencyTest {

    @Test
    void checkoutStatusHasExactlyFiveValues() {
        assertThat(CheckoutStatusEnum.values()).hasSize(5);
    }

    @Test
    void checkoutStatusCodesMatchGlobalEnum() {
        assertThat(CheckoutStatusEnum.INITIATED.getCode()).isEqualTo("INITIATED");
        assertThat(CheckoutStatusEnum.PAID.getCode()).isEqualTo("PAID");
        assertThat(CheckoutStatusEnum.ABANDONED.getCode()).isEqualTo("ABANDONED");
        assertThat(CheckoutStatusEnum.EXPIRED.getCode()).isEqualTo("EXPIRED");
        assertThat(CheckoutStatusEnum.FAILED.getCode()).isEqualTo("FAILED");
    }

    @Test
    void fromCodeValidValues() {
        assertThat(CheckoutStatusEnum.fromCode("INITIATED")).isEqualTo(CheckoutStatusEnum.INITIATED);
        assertThat(CheckoutStatusEnum.fromCode("PAID")).isEqualTo(CheckoutStatusEnum.PAID);
        assertThat(CheckoutStatusEnum.fromCode("ABANDONED")).isEqualTo(CheckoutStatusEnum.ABANDONED);
        assertThat(CheckoutStatusEnum.fromCode("EXPIRED")).isEqualTo(CheckoutStatusEnum.EXPIRED);
        assertThat(CheckoutStatusEnum.fromCode("FAILED")).isEqualTo(CheckoutStatusEnum.FAILED);
    }

    @Test
    void fromCodeInvalidThrows() {
        assertThatThrownBy(() -> CheckoutStatusEnum.fromCode("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromCodeNullThrows() {
        assertThatThrownBy(() -> CheckoutStatusEnum.fromCode(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void labelsAreNotNull() {
        for (CheckoutStatusEnum status : CheckoutStatusEnum.values()) {
            assertThat(status.getLabel()).isNotNull();
            assertThat(status.getLabel()).isNotBlank();
        }
    }
}
