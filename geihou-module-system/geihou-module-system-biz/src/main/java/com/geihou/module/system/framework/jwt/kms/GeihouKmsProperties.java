package com.geihou.module.system.framework.jwt.kms;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * No-network KMS configuration value used before any production KMS provider is allowed.
 */
public final class GeihouKmsProperties {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration DEFAULT_STALE_CACHE_TTL = Duration.ZERO;
    private static final Pattern ENV_NAME = Pattern.compile("^[A-Z_][A-Z0-9_]{2,127}$");

    private final boolean enabled;
    private final String endpoint;
    private final String clientKeyFile;
    private final String clientKeyPasswordEnv;
    private final String caCertFile;
    private final String secretNamePrefix;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration cacheTtl;
    private final Duration staleCacheTtl;

    private GeihouKmsProperties(boolean enabled,
                                String endpoint,
                                String clientKeyFile,
                                String clientKeyPasswordEnv,
                                String caCertFile,
                                String secretNamePrefix,
                                Duration connectTimeout,
                                Duration readTimeout,
                                Duration cacheTtl,
                                Duration staleCacheTtl) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.clientKeyFile = clientKeyFile;
        this.clientKeyPasswordEnv = clientKeyPasswordEnv;
        this.caCertFile = caCertFile;
        this.secretNamePrefix = normalizePrefix(secretNamePrefix);
        this.connectTimeout = defaultIfNull(connectTimeout, DEFAULT_CONNECT_TIMEOUT);
        this.readTimeout = defaultIfNull(readTimeout, DEFAULT_READ_TIMEOUT);
        this.cacheTtl = defaultIfNull(cacheTtl, DEFAULT_CACHE_TTL);
        this.staleCacheTtl = defaultIfNull(staleCacheTtl, DEFAULT_STALE_CACHE_TTL);
        validate();
    }

    public static GeihouKmsProperties disabledDefaults() {
        return new GeihouKmsProperties(
                false, null, null, null, null, "", null, null, null, null);
    }

    public static GeihouKmsProperties of(boolean enabled,
                                         String endpoint,
                                         String clientKeyFile,
                                         String clientKeyPasswordEnv,
                                         String caCertFile,
                                         String secretNamePrefix,
                                         Duration connectTimeout,
                                         Duration readTimeout,
                                         Duration cacheTtl,
                                         Duration staleCacheTtl) {
        return new GeihouKmsProperties(
                enabled,
                endpoint,
                clientKeyFile,
                clientKeyPasswordEnv,
                caCertFile,
                secretNamePrefix,
                connectTimeout,
                readTimeout,
                cacheTtl,
                staleCacheTtl);
    }

    public boolean enabled() {
        return enabled;
    }

    public String endpoint() {
        return endpoint;
    }

    public String clientKeyFile() {
        return clientKeyFile;
    }

    public String clientKeyPasswordEnv() {
        return clientKeyPasswordEnv;
    }

    public String caCertFile() {
        return caCertFile;
    }

    public String secretNamePrefix() {
        return secretNamePrefix;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    public Duration cacheTtl() {
        return cacheTtl;
    }

    public Duration staleCacheTtl() {
        return staleCacheTtl;
    }

    private void validate() {
        validatePositive(connectTimeout, "connect-timeout");
        validatePositive(readTimeout, "read-timeout");
        validatePositive(cacheTtl, "cache-ttl");
        if (staleCacheTtl.isNegative()) {
            throw new IllegalArgumentException("stale-cache-ttl must not be negative");
        }
        if (!secretNamePrefix.isEmpty() && !secretNamePrefix.endsWith("/")) {
            throw new IllegalArgumentException("secret-name-prefix must be empty or end with '/'");
        }
        if (!enabled) {
            return;
        }
        validateRequiredEndpoint(endpoint);
        validateAbsolutePath(clientKeyFile, "client-key-file");
        validateAbsolutePath(caCertFile, "ca-cert-file");
        validatePasswordEnvName(clientKeyPasswordEnv);
    }

    private static void validateRequiredEndpoint(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be blank");
        }
        if (value.contains("://")) {
            throw new IllegalArgumentException("endpoint must not include protocol");
        }
        if (value.contains("/")) {
            throw new IllegalArgumentException("endpoint must not include path");
        }
    }

    private static void validateAbsolutePath(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        try {
            if (!Path.of(value).isAbsolute()) {
                throw new IllegalArgumentException(fieldName + " must be an absolute path");
            }
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid absolute path", ex);
        }
    }

    private static void validatePasswordEnvName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("client-key-password-env must not be blank");
        }
        if (!ENV_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("client-key-password-env must be an environment variable name");
        }
    }

    private static void validatePositive(Duration value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static Duration defaultIfNull(Duration value, Duration defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static String normalizePrefix(String value) {
        return value == null ? "" : value;
    }
}
