package com.geihou.module.system.service.auth;

import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.framework.security.core.spi.TokenValidationResult;
import com.geihou.framework.security.core.spi.TokenValidator;
import com.geihou.module.system.api.auth.AuthApi;
import com.geihou.module.system.api.auth.dto.AuthTokenVerifyRespDTO;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Compile-only adapter from system auth API to the security token validator SPI.
 */
public class AuthApiTokenValidator implements TokenValidator {

    private static final String DEFAULT_INVALID_TOKEN_MESSAGE = "Token is invalid";

    private final AuthApi authApi;

    public AuthApiTokenValidator(AuthApi authApi) {
        this.authApi = Objects.requireNonNull(authApi, "authApi must not be null");
    }

    @Override
    public TokenValidationResult validate(String token) {
        AuthTokenVerifyRespDTO response;
        try {
            response = authApi.verifyToken(token);
        } catch (RuntimeException ex) {
            return invalid();
        }

        if (response == null) {
            return invalid();
        }
        if (Boolean.TRUE.equals(response.getValid())) {
            return TokenValidationResult.valid(toPrincipal(response));
        }
        return TokenValidationResult.invalid(errorCode(response), errorMessage(response));
    }

    private static GeihouPrincipal toPrincipal(AuthTokenVerifyRespDTO response) {
        return new GeihouPrincipal(
                response.getUserId(),
                response.getUserName(),
                response.getTenantId(),
                toTokenScopes(response.getRoles()),
                Set.of(),
                response.getTokenId());
    }

    private static Set<String> toTokenScopes(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        Set<String> scopes = new LinkedHashSet<>();
        for (String role : roles) {
            if (role != null) {
                scopes.add(role);
            }
        }
        return Set.copyOf(scopes);
    }

    private static TokenValidationResult invalid() {
        return TokenValidationResult.invalid(GeihouAuthErrorCodes.INVALID_TOKEN, DEFAULT_INVALID_TOKEN_MESSAGE);
    }

    private static int errorCode(AuthTokenVerifyRespDTO response) {
        Integer errorCode = response.getErrorCode();
        if (errorCode == null || errorCode == 0) {
            return GeihouAuthErrorCodes.INVALID_TOKEN;
        }
        return errorCode;
    }

    private static String errorMessage(AuthTokenVerifyRespDTO response) {
        String errorMessage = response.getErrorMessage();
        if (errorMessage == null || errorMessage.isBlank()) {
            return DEFAULT_INVALID_TOKEN_MESSAGE;
        }
        return errorMessage;
    }
}
