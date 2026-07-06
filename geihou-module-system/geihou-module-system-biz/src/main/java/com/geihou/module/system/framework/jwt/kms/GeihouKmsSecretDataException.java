package com.geihou.module.system.framework.jwt.kms;

/**
 * Redacted failure while converting KMS secret data to signing-secret material.
 */
public final class GeihouKmsSecretDataException extends RuntimeException {

    public GeihouKmsSecretDataException(String message) {
        super(requireMessage(message));
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return message;
    }
}
