package com.geihou.module.system.service.auth;

import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.module.system.api.auth.AuthApi;
import com.geihou.module.system.api.auth.dto.AuthTokenVerifyRespDTO;
import java.util.Objects;

/**
 * Pure Java AuthApi delegate. Runtime registration is intentionally out of scope.
 */
public class AuthApiImpl implements AuthApi {

    private static final String INVALID_TOKEN_MESSAGE = "Token is invalid";

    private final GeihouAuthTokenVerifier tokenVerifier;

    public AuthApiImpl(GeihouAuthTokenVerifier tokenVerifier) {
        this.tokenVerifier = Objects.requireNonNull(tokenVerifier, "tokenVerifier must not be null");
    }

    @Override
    public AuthTokenVerifyRespDTO verifyToken(String token) {
        try {
            AuthTokenVerifyRespDTO response = tokenVerifier.verify(token);
            if (response == null) {
                return invalid();
            }
            return response;
        } catch (RuntimeException ex) {
            return invalid();
        }
    }

    private static AuthTokenVerifyRespDTO invalid() {
        AuthTokenVerifyRespDTO response = new AuthTokenVerifyRespDTO();
        response.setValid(false);
        response.setErrorCode(GeihouAuthErrorCodes.INVALID_TOKEN);
        response.setErrorMessage(INVALID_TOKEN_MESSAGE);
        return response;
    }
}
