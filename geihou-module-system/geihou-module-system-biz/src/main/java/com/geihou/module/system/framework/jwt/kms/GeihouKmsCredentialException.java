package com.geihou.module.system.framework.jwt.kms;

/**
 * Redacted credential-resolution exception.
 */
public final class GeihouKmsCredentialException extends RuntimeException {

    public GeihouKmsCredentialException(String message) {
        super(requireMessage(message));
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return message;
    }
}
