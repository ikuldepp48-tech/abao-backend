package com.geihou.module.system.framework.jwt.kms;

/**
 * Redacted KMS client exception. The original SDK exception message is deliberately not copied.
 */
public final class GeihouKmsClientException extends RuntimeException {

    public GeihouKmsClientException(String message) {
        super(requireMessage(message));
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return message;
    }
}
