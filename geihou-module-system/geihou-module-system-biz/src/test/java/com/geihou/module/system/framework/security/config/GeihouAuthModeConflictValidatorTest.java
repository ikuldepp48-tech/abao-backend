package com.geihou.module.system.framework.security.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GeihouAuthModeConflictValidatorTest {

    @Test
    void shouldThrowWhenAuthDisabledAndKmsEnabled() {
        GeihouAuthModeConflictValidator validator =
                new GeihouAuthModeConflictValidator(true, true);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth-disabled=true")
                .hasMessageContaining("kms.enabled=true");
    }

    @Test
    void shouldPassWhenAuthDisabledAndKmsDisabled() {
        GeihouAuthModeConflictValidator validator =
                new GeihouAuthModeConflictValidator(true, false);

        assertThatCode(validator::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPassWhenAuthEnabledAndKmsEnabled() {
        GeihouAuthModeConflictValidator validator =
                new GeihouAuthModeConflictValidator(false, true);

        assertThatCode(validator::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPassWhenAuthEnabledAndKmsDisabled() {
        GeihouAuthModeConflictValidator validator =
                new GeihouAuthModeConflictValidator(false, false);

        assertThatCode(validator::afterPropertiesSet)
                .doesNotThrowAnyException();
    }
}
