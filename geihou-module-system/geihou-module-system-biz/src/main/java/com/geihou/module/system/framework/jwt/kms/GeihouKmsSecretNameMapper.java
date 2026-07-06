package com.geihou.module.system.framework.jwt.kms;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Maps a JWT key id to a KMS secret name without network access or normalization.
 */
public final class GeihouKmsSecretNameMapper {

    private static final Pattern SAFE_KID = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");

    private final String secretNamePrefix;

    public GeihouKmsSecretNameMapper(String secretNamePrefix) {
        this.secretNamePrefix = secretNamePrefix == null ? "" : secretNamePrefix;
        if (!this.secretNamePrefix.isEmpty() && !this.secretNamePrefix.endsWith("/")) {
            throw new IllegalArgumentException("secret-name-prefix must be empty or end with '/'");
        }
    }

    public String toSecretName(String kid) {
        if (kid == null || kid.isBlank()) {
            throw new IllegalArgumentException("kid must not be blank");
        }
        if (!SAFE_KID.matcher(kid).matches()) {
            throw new IllegalArgumentException("kid must match ^[A-Za-z0-9._:-]{8,128}$");
        }
        return secretNamePrefix + kid;
    }

    public String secretNamePrefix() {
        return secretNamePrefix;
    }

    @Override
    public String toString() {
        return "GeihouKmsSecretNameMapper{secretNamePrefix='" + secretNamePrefix + "'}";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GeihouKmsSecretNameMapper that)) {
            return false;
        }
        return Objects.equals(secretNamePrefix, that.secretNamePrefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(secretNamePrefix);
    }
}
