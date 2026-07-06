package com.geihou.module.system.framework.jwt.kms;

import com.aliyun.dkms.gcs.openapi.models.Config;
import java.time.Duration;

/**
 * Builds Aliyun DKMS OpenAPI configuration without constructing an SDK client.
 */
public final class GeihouKmsOpenApiConfigFactory {

    private static final String HTTPS = "https";

    private GeihouKmsOpenApiConfigFactory() {
    }

    public static Config create(GeihouKmsProperties properties, String clientKeyPassword) {
        return create(properties, clientKeyPassword, null);
    }

    public static Config create(GeihouKmsProperties properties, String clientKeyPassword, String userAgent) {
        requireEnabled(properties);
        if (clientKeyPassword == null || clientKeyPassword.isBlank()) {
            throw new IllegalArgumentException("client-key password must not be blank");
        }
        Config config = new Config()
                .setProtocol(HTTPS)
                .setEndpoint(properties.endpoint())
                .setClientKeyFile(properties.clientKeyFile())
                .setPassword(clientKeyPassword)
                .setCaFilePath(properties.caCertFile())
                .setConnectTimeout(toPositiveMillis(properties.connectTimeout(), "connect-timeout"))
                .setReadTimeout(toPositiveMillis(properties.readTimeout(), "read-timeout"))
                .setIgnoreSSL(false);
        if (userAgent != null) {
            if (userAgent.isBlank()) {
                throw new IllegalArgumentException("user-agent must not be blank");
            }
            config.setUserAgent(userAgent);
        }
        return config;
    }

    private static void requireEnabled(GeihouKmsProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("kms properties must not be null");
        }
        if (!properties.enabled()) {
            throw new IllegalArgumentException("kms properties must be enabled");
        }
    }

    private static long toPositiveMillis(Duration value, String fieldName) {
        long millis = value.toMillis();
        if (millis <= 0) {
            throw new IllegalArgumentException(fieldName + " must be at least 1ms");
        }
        return millis;
    }
}
