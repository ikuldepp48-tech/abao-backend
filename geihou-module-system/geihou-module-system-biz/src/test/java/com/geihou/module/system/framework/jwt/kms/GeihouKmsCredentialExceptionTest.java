package com.geihou.module.system.framework.jwt.kms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GeihouKmsCredentialExceptionTest {

    @Test
    void shouldCarryOnlyRedactedMessage() {
        GeihouKmsCredentialException exception = new GeihouKmsCredentialException(
                "KMS ClientKey password is unavailable");

        assertThat(exception)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("KMS ClientKey password is unavailable")
                .hasNoCause();
    }

    @Test
    void shouldRejectBlankMessage() {
        assertThatThrownBy(() -> new GeihouKmsCredentialException(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }
}
