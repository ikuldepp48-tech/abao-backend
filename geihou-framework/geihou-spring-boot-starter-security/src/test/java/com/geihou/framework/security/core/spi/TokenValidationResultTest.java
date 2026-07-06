package com.geihou.framework.security.core.spi;

import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.framework.security.core.context.GeihouPrincipal;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenValidationResultTest {

    @Test
    void shouldCreateValidResult() {
        GeihouPrincipal principal = principal();

        TokenValidationResult result = TokenValidationResult.valid(principal);

        assertThat(result.valid()).isTrue();
        assertThat(result.principal()).isEqualTo(principal);
        assertThat(result.errorCode()).isZero();
        assertThat(result.errorMessage()).isEmpty();
    }

    @Test
    void shouldCreateInvalidResult() {
        TokenValidationResult result = TokenValidationResult.invalid(
                GeihouAuthErrorCodes.INVALID_TOKEN, "Token is invalid");

        assertThat(result.valid()).isFalse();
        assertThat(result.principal()).isNull();
        assertThat(result.errorCode()).isEqualTo(GeihouAuthErrorCodes.INVALID_TOKEN);
        assertThat(result.errorMessage()).isEqualTo("Token is invalid");
    }

    @Test
    void shouldRejectInvalidShape() {
        GeihouPrincipal principal = principal();

        assertThatThrownBy(() -> new TokenValidationResult(true, null, 0, ""))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TokenValidationResult(true, principal, 1, "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenValidationResult(false, principal, 1, "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenValidationResult(false, null, 0, "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenValidationResult(false, null, 1, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private GeihouPrincipal principal() {
        return new GeihouPrincipal(1L, "admin", 10L, Set.of("read"), Set.of("microservice:read"), "token-1");
    }
}
