package com.geihou.framework.security.core.spi;

import com.geihou.framework.security.core.context.GeihouPrincipal;

import java.util.Objects;

/**
 * Result returned by a {@link TokenValidator}.
 */
public record TokenValidationResult(
        boolean valid,
        GeihouPrincipal principal,
        int errorCode,
        String errorMessage) {

    public TokenValidationResult {
        if (valid) {
            Objects.requireNonNull(principal, "principal must not be null when valid");
            if (errorCode != 0) {
                throw new IllegalArgumentException("valid result errorCode must be 0");
            }
            errorMessage = errorMessage == null ? "" : errorMessage;
        } else {
            if (principal != null) {
                throw new IllegalArgumentException("invalid result principal must be null");
            }
            if (errorCode == 0) {
                throw new IllegalArgumentException("invalid result errorCode must not be 0");
            }
            if (errorMessage == null || errorMessage.isBlank()) {
                throw new IllegalArgumentException("invalid result errorMessage must not be blank");
            }
        }
    }

    public static TokenValidationResult valid(GeihouPrincipal principal) {
        return new TokenValidationResult(true, principal, 0, "");
    }

    public static TokenValidationResult invalid(int errorCode, String errorMessage) {
        return new TokenValidationResult(false, null, errorCode, errorMessage);
    }
}
