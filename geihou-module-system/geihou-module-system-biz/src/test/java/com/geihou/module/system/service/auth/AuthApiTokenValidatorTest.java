package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.framework.security.core.spi.TokenValidationResult;
import com.geihou.module.system.api.auth.AuthApi;
import com.geihou.module.system.api.auth.dto.AuthTokenVerifyRespDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthApiTokenValidatorTest {

    @Test
    void shouldMapValidTokenToPrincipalWithRolesAndEmptyPermissions() {
        AuthApi authApi = token -> validResponse(List.of("OWNER", "SHOP_MANAGER"));

        TokenValidationResult result = new AuthApiTokenValidator(authApi).validate("token-raw");

        assertThat(result.valid()).isTrue();
        assertThat(result.errorCode()).isZero();
        assertThat(result.errorMessage()).isEmpty();
        GeihouPrincipal principal = result.principal();
        assertThat(principal.userId()).isEqualTo(100L);
        assertThat(principal.userName()).isEqualTo("admin");
        assertThat(principal.tenantId()).isEqualTo(200L);
        assertThat(principal.tokenId()).isEqualTo("token-id-1");
        assertThat(principal.tokenScopes()).containsExactlyInAnyOrder("OWNER", "SHOP_MANAGER");
        assertThat(principal.permissions()).isEmpty();
    }

    @Test
    void shouldMapNullRolesToEmptyTokenScopes() {
        AuthApi authApi = token -> validResponse(null);

        TokenValidationResult result = new AuthApiTokenValidator(authApi).validate("token-raw");

        assertThat(result.valid()).isTrue();
        assertThat(result.principal().tokenScopes()).isEmpty();
        assertThat(result.principal().permissions()).isEmpty();
    }

    @Test
    void shouldPreserveKnownErrorCodeAndSafeMessageWhenInvalid() {
        AuthApi authApi = token -> invalidResponse(GeihouAuthErrorCodes.TOKEN_EXPIRED, "Token expired");

        TokenValidationResult result = new AuthApiTokenValidator(authApi).validate("token-raw");

        assertThat(result.valid()).isFalse();
        assertThat(result.principal()).isNull();
        assertThat(result.errorCode()).isEqualTo(GeihouAuthErrorCodes.TOKEN_EXPIRED);
        assertThat(result.errorMessage()).isEqualTo("Token expired");
    }

    @Test
    void shouldFallbackWhenInvalidErrorCodeIsNullAndMessageIsBlank() {
        AuthApi authApi = token -> invalidResponse(null, " ");

        TokenValidationResult result = new AuthApiTokenValidator(authApi).validate("token-raw");

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo(GeihouAuthErrorCodes.INVALID_TOKEN);
        assertThat(result.errorMessage()).isEqualTo("Token is invalid");
    }

    @Test
    void shouldFallbackWhenInvalidErrorCodeIsZeroAndMessageIsNull() {
        AuthApi authApi = token -> invalidResponse(0, null);

        TokenValidationResult result = new AuthApiTokenValidator(authApi).validate("token-raw");

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo(GeihouAuthErrorCodes.INVALID_TOKEN);
        assertThat(result.errorMessage()).isEqualTo("Token is invalid");
    }

    @Test
    void shouldFailClosedWhenAuthApiReturnsNull() {
        AuthApi authApi = token -> null;

        TokenValidationResult result = new AuthApiTokenValidator(authApi).validate("token-raw");

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo(GeihouAuthErrorCodes.INVALID_TOKEN);
        assertThat(result.errorMessage()).isEqualTo("Token is invalid");
    }

    @Test
    void shouldFailClosedWithoutLeakingExceptionText() {
        AuthApi authApi = token -> {
            throw new IllegalStateException("secret-token-db is unavailable");
        };

        TokenValidationResult result = new AuthApiTokenValidator(authApi).validate("token-raw");

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo(GeihouAuthErrorCodes.INVALID_TOKEN);
        assertThat(result.errorMessage()).isEqualTo("Token is invalid");
        assertThat(result.errorMessage()).doesNotContain("secret-token-db");
    }

    private static AuthTokenVerifyRespDTO validResponse(List<String> roles) {
        AuthTokenVerifyRespDTO response = new AuthTokenVerifyRespDTO();
        response.setValid(true);
        response.setUserId(100L);
        response.setUserName("admin");
        response.setTenantId(200L);
        response.setTokenId("token-id-1");
        response.setAudience("admin");
        response.setUserRole("PLATFORM_ADMIN");
        response.setRoles(roles);
        return response;
    }

    private static AuthTokenVerifyRespDTO invalidResponse(Integer errorCode, String errorMessage) {
        AuthTokenVerifyRespDTO response = new AuthTokenVerifyRespDTO();
        response.setValid(false);
        response.setErrorCode(errorCode);
        response.setErrorMessage(errorMessage);
        return response;
    }
}
