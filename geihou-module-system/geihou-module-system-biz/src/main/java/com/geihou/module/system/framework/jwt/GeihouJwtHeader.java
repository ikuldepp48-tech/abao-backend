package com.geihou.module.system.framework.jwt;

import java.util.Objects;

/**
 * Verified JWT header metadata needed before selecting a signing secret.
 */
public final class GeihouJwtHeader {

    private final String kid;
    private final String algorithm;
    private final String type;

    public GeihouJwtHeader(String kid, String algorithm, String type) {
        this.kid = requireText(kid, "kid must not be blank");
        this.algorithm = requireText(algorithm, "algorithm must not be blank");
        this.type = requireText(type, "type must not be blank");
    }

    public String kid() {
        return kid;
    }

    public String algorithm() {
        return algorithm;
    }

    public String type() {
        return type;
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
