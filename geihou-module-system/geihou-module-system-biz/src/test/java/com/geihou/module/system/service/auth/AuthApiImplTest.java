package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.framework.security.core.spi.TokenValidationResult;
import com.geihou.module.system.api.auth.AuthApi;
import com.geihou.module.system.api.auth.dto.AuthTokenVerifyRespDTO;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AuthApiImplTest {

    @Test
    void shouldImplementAuthApiWithoutRuntimeAnnotations() {
        assertThat(AuthApi.class.isAssignableFrom(AuthApiImpl.class)).isTrue();
        assertThat(AuthApiImpl.class.getAnnotations()).isEmpty();
    }

    @Test
    void shouldRejectNullVerifier() {
        assertThatThrownBy(() -> new AuthApiImpl(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tokenVerifier must not be null");
    }

    @Test
    void shouldReturnValidVerifierResponseUnchanged() {
        GeihouAuthTokenVerifier verifier = mock(GeihouAuthTokenVerifier.class);
        AuthTokenVerifyRespDTO expected = validResponse();
        when(verifier.verify("raw-token")).thenReturn(expected);

        AuthTokenVerifyRespDTO actual = new AuthApiImpl(verifier).verifyToken("raw-token");

        assertThat(actual).isSameAs(expected);
        verify(verifier).verify("raw-token");
    }

    @Test
    void shouldReturnInvalidVerifierResponseUnchanged() {
        GeihouAuthTokenVerifier verifier = mock(GeihouAuthTokenVerifier.class);
        AuthTokenVerifyRespDTO expected = invalidResponse(GeihouAuthErrorCodes.TOKEN_REVOKED, "Token revoked");
        when(verifier.verify("raw-token")).thenReturn(expected);

        AuthTokenVerifyRespDTO actual = new AuthApiImpl(verifier).verifyToken("raw-token");

        assertThat(actual).isSameAs(expected);
        verify(verifier).verify("raw-token");
    }

    @Test
    void shouldFailClosedWhenVerifierReturnsNull() {
        GeihouAuthTokenVerifier verifier = mock(GeihouAuthTokenVerifier.class);
        when(verifier.verify("raw-token")).thenReturn(null);

        AuthTokenVerifyRespDTO response = new AuthApiImpl(verifier).verifyToken("raw-token");

        assertInvalid(response);
    }

    @Test
    void shouldFailClosedWithoutLeakingVerifierExceptionText() {
        GeihouAuthTokenVerifier verifier = mock(GeihouAuthTokenVerifier.class);
        when(verifier.verify("raw-token")).thenThrow(new IllegalStateException("secret-db-down"));

        AuthTokenVerifyRespDTO response = new AuthApiImpl(verifier).verifyToken("raw-token");

        assertInvalid(response);
        assertThat(response.getErrorMessage()).doesNotContain("secret-db-down");
    }

    @Test
    void shouldDelegateBlankTokenToVerifier() {
        GeihouAuthTokenVerifier verifier = mock(GeihouAuthTokenVerifier.class);
        AuthTokenVerifyRespDTO expected = invalidResponse(GeihouAuthErrorCodes.INVALID_TOKEN, "Token is invalid");
        when(verifier.verify(" ")).thenReturn(expected);

        AuthTokenVerifyRespDTO actual = new AuthApiImpl(verifier).verifyToken(" ");

        assertThat(actual).isSameAs(expected);
        verify(verifier).verify(" ");
    }

    @Test
    void shouldDeclareOnlyCurrentPublicAuthApiMethod() {
        Set<String> declaredPublicMethods = Arrays.stream(AuthApiImpl.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName() + ":" + method.getParameterCount())
                .collect(Collectors.toSet());

        assertThat(declaredPublicMethods).containsExactly("verifyToken:1");
    }

    @Test
    void shouldWorkThroughAuthApiTokenValidatorForValidAndInvalidResults() {
        GeihouAuthTokenVerifier verifier = mock(GeihouAuthTokenVerifier.class);
        when(verifier.verify("valid-token")).thenReturn(validResponse());
        when(verifier.verify("invalid-token")).thenReturn(
                invalidResponse(GeihouAuthErrorCodes.TOKEN_EXPIRED, "Token expired"));
        AuthApi authApi = new AuthApiImpl(verifier);
        AuthApiTokenValidator validator = new AuthApiTokenValidator(authApi);

        TokenValidationResult valid = validator.validate("valid-token");
        TokenValidationResult invalid = validator.validate("invalid-token");

        assertThat(valid.valid()).isTrue();
        GeihouPrincipal principal = valid.principal();
        assertThat(principal.userId()).isEqualTo(100L);
        assertThat(principal.userName()).isEqualTo("admin");
        assertThat(principal.tenantId()).isEqualTo(200L);
        assertThat(principal.tokenId()).isEqualTo("token-id-1");
        assertThat(principal.tokenScopes()).containsExactly("OWNER");
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.errorCode()).isEqualTo(GeihouAuthErrorCodes.TOKEN_EXPIRED);
        assertThat(invalid.errorMessage()).isEqualTo("Token expired");
    }

    private static void assertInvalid(AuthTokenVerifyRespDTO response) {
        assertThat(response.getValid()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(GeihouAuthErrorCodes.INVALID_TOKEN);
        assertThat(response.getErrorMessage()).isEqualTo("Token is invalid");
    }

    private static AuthTokenVerifyRespDTO validResponse() {
        AuthTokenVerifyRespDTO response = new AuthTokenVerifyRespDTO();
        response.setValid(true);
        response.setUserId(100L);
        response.setUserName("admin");
        response.setTenantId(200L);
        response.setTokenId("token-id-1");
        response.setAudience("admin");
        response.setUserRole("PLATFORM_OPERATOR");
        response.setRoles(List.of("OWNER"));
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
