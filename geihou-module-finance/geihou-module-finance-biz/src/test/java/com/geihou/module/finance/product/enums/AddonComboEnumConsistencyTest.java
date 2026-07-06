package com.geihou.module.finance.product.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Enum consistency test for G1-02F addon/combo enums.
 *
 * <p>AC-4: AddonOptionStatusEnum values match global enum table
 * ENUM_ADDON_OPTION_STATUS (ACTIVE/SOLD_OUT/DISABLED).
 * AC-5: ComboStatusEnum values match global enum table
 * ENUM_COMBO_STATUS (ACTIVE/PAUSED/DEPRECATED).
 * AC-14: enum javadoc references global enum table registration names.
 */
class AddonComboEnumConsistencyTest {

    @Test
    void addonOptionStatusEnumShouldHaveExactlyThreeValues() {
        assertThat(AddonOptionStatusEnum.values()).hasSize(3);
    }

    @Test
    void addonOptionStatusEnumCodesShouldMatchGlobalEnumTable() {
        // ENUM_ADDON_OPTION_STATUS: ACTIVE/SOLD_OUT/DISABLED
        assertThat(AddonOptionStatusEnum.ACTIVE.getCode()).isEqualTo("ACTIVE");
        assertThat(AddonOptionStatusEnum.SOLD_OUT.getCode()).isEqualTo("SOLD_OUT");
        assertThat(AddonOptionStatusEnum.DISABLED.getCode()).isEqualTo("DISABLED");
    }

    @Test
    void addonOptionStatusFromCodeShouldRoundTrip() {
        for (AddonOptionStatusEnum status : AddonOptionStatusEnum.values()) {
            assertThat(AddonOptionStatusEnum.fromCode(status.getCode())).isEqualTo(status);
        }
    }

    @Test
    void addonOptionStatusFromCodeShouldRejectUnknownCode() {
        assertThatThrownBy(() -> AddonOptionStatusEnum.fromCode("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addonOptionStatusActiveCanTransitionToSoldOutAndDisabled() {
        assertThat(AddonOptionStatusEnum.ACTIVE.canTransitionTo(AddonOptionStatusEnum.SOLD_OUT)).isTrue();
        assertThat(AddonOptionStatusEnum.ACTIVE.canTransitionTo(AddonOptionStatusEnum.DISABLED)).isTrue();
    }

    @Test
    void comboStatusEnumShouldHaveExactlyThreeValues() {
        assertThat(ComboStatusEnum.values()).hasSize(3);
    }

    @Test
    void comboStatusEnumCodesShouldMatchGlobalEnumTable() {
        // ENUM_COMBO_STATUS: ACTIVE/PAUSED/DEPRECATED
        assertThat(ComboStatusEnum.ACTIVE.getCode()).isEqualTo("ACTIVE");
        assertThat(ComboStatusEnum.PAUSED.getCode()).isEqualTo("PAUSED");
        assertThat(ComboStatusEnum.DEPRECATED.getCode()).isEqualTo("DEPRECATED");
    }

    @Test
    void comboStatusFromCodeShouldRoundTrip() {
        for (ComboStatusEnum status : ComboStatusEnum.values()) {
            assertThat(ComboStatusEnum.fromCode(status.getCode())).isEqualTo(status);
        }
    }

    @Test
    void comboStatusFromCodeShouldRejectUnknownCode() {
        assertThatThrownBy(() -> ComboStatusEnum.fromCode("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comboStatusActiveCanTransitionToPausedAndDeprecated() {
        assertThat(ComboStatusEnum.ACTIVE.canTransitionTo(ComboStatusEnum.PAUSED)).isTrue();
        assertThat(ComboStatusEnum.ACTIVE.canTransitionTo(ComboStatusEnum.DEPRECATED)).isTrue();
    }

    @Test
    void comboStatusDeprecatedIsTerminal() {
        assertThat(ComboStatusEnum.DEPRECATED.canTransitionTo(ComboStatusEnum.ACTIVE)).isFalse();
        assertThat(ComboStatusEnum.DEPRECATED.canTransitionTo(ComboStatusEnum.PAUSED)).isFalse();
    }

    @Test
    void comboStatusPausedCanTransitionToActive() {
        // PAUSED → ACTIVE (manual recovery, G1-02F R-4)
        assertThat(ComboStatusEnum.PAUSED.canTransitionTo(ComboStatusEnum.ACTIVE)).isTrue();
    }
}
