package com.geihou.framework.security.core.spi;

import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DenyAllTokenValidatorTest {

    @Test
    void shouldRejectEveryToken() {
        DenyAllTokenValidator validator = new DenyAllTokenValidator();

        assertRejected(validator.validate("valid-looking-token"));
        assertRejected(validator.validate(""));
        assertRejected(validator.validate(null));
    }

    private void assertRejected(TokenValidationResult result) {
        assertThat(result.valid()).isFalse();
        assertThat(result.principal()).isNull();
        assertThat(result.errorCode()).isEqualTo(GeihouAuthErrorCodes.INVALID_TOKEN);
        assertThat(result.errorMessage()).isNotBlank();
    }
}
