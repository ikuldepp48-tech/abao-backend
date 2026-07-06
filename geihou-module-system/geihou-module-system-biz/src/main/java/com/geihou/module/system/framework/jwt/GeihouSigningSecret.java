package com.geihou.module.system.framework.jwt;

import java.util.Arrays;
import java.util.Objects;

/**
 * Signing secret selected by JWT key id.
 */
public final class GeihouSigningSecret {

    private static final String HS512 = "HS512";
    private static final int HS512_MINIMUM_KEY_BYTES = 64;

    private final String kid;
    private final String algorithm;
    private final byte[] keyBytes;

    public GeihouSigningSecret(String kid, String algorithm, byte[] keyBytes) {
        this.kid = requireText(kid, "kid must not be blank");
        this.algorithm = requireText(algorithm, "algorithm must not be blank");
        if (!HS512.equals(this.algorithm)) {
            throw new IllegalArgumentException("Only HS512 signing secrets are supported");
        }
        if (keyBytes == null || keyBytes.length < HS512_MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("HS512 key bytes must be at least 64 bytes");
        }
        this.keyBytes = Arrays.copyOf(keyBytes, keyBytes.length);
    }

    public String kid() {
        return kid;
    }

    public String algorithm() {
        return algorithm;
    }

    public byte[] keyBytes() {
        return Arrays.copyOf(keyBytes, keyBytes.length);
    }

    @Override
    public String toString() {
        return "GeihouSigningSecret{kid='" + kid + "', algorithm='" + algorithm + "', keyBytes=<redacted>}";
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
