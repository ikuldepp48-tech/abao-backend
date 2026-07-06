package com.geihou.framework.security.core.spi;

import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;

/**
 * Fail-closed default token validator.
 */
public class DenyAllTokenValidator implements TokenValidator {

    @Override
    public TokenValidationResult validate(String token) {
        return TokenValidationResult.invalid(GeihouAuthErrorCodes.INVALID_TOKEN, "Token is invalid");
    }
}
