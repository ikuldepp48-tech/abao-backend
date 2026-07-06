package com.geihou.module.system.service.auth;

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Function;

/**
 * ENV-backed {@link TwoFactorSecretKeyProvider} that reads a Base64-encoded
 * AES key from a named environment variable.
 *
 * <p>H157S-M: the provider is fail-closed — if the environment variable is
 * missing, blank, not valid Base64, or does not decode to exactly 16, 24, or
 * 32 bytes, the constructor throws {@link IllegalStateException} so that the
 * application context fails to start rather than silently running with an
 * absent 2FA key.
 *
 * <p>The env-lookup function is injectable via the package-private constructor
 * to enable unit testing without manipulating real environment variables.
 */
public final class EnvTwoFactorSecretKeyProvider implements TwoFactorSecretKeyProvider {

    private final byte[] key;

    /**
     * Public constructor used by the auto-configuration; reads from
     * {@link System#getenv}.
     *
     * @param envVarName the name of the environment variable holding the
     *                   Base64-encoded AES key
     */
    public EnvTwoFactorSecretKeyProvider(String envVarName) {
        this(envVarName, System::getenv);
    }

    /**
     * Test-friendly constructor that accepts a custom env-lookup function.
     *
     * @param envVarName the name of the environment variable
     * @param envLookup  a function that maps env-var names to their values
     */
    EnvTwoFactorSecretKeyProvider(String envVarName, Function<String, String> envLookup) {
        Objects.requireNonNull(envLookup, "envLookup must not be null");
        if (envVarName == null || envVarName.isBlank()) {
            throw new IllegalArgumentException("aes-key-env must not be blank");
        }
        String base64Value = envLookup.apply(envVarName);
        if (base64Value == null || base64Value.isBlank()) {
            throw new IllegalStateException(
                    "2FA AES key environment variable is missing or blank: " + envVarName);
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "2FA AES key environment variable is not valid Base64: " + envVarName);
        }
        if (!isValidAesKeyLength(decoded.length)) {
            int invalidLength = decoded.length;
            Arrays.fill(decoded, (byte) 0);
            throw new IllegalStateException(
                    "2FA AES key must be 16, 24, or 32 bytes but decoded to " + invalidLength
                            + " bytes from env var: " + envVarName);
        }
        this.key = decoded;
    }

    @Override
    public byte[] currentKey() {
        return Arrays.copyOf(key, key.length);
    }

    private static boolean isValidAesKeyLength(int length) {
        return length == 16 || length == 24 || length == 32;
    }

    @Override
    public String toString() {
        return "EnvTwoFactorSecretKeyProvider{key=<redacted>}";
    }
}
