package com.geihou.module.system.framework.jwt.kms;

import com.aliyun.dkms.gcs.openapi.util.models.RuntimeOptions;
import java.time.Duration;

/**
 * Builds runtime options for one fail-fast DKMS request.
 */
public final class GeihouKmsRuntimeOptionsFactory {

    private GeihouKmsRuntimeOptionsFactory() {
    }

    public static RuntimeOptions create(GeihouKmsProperties properties) {
        requireEnabled(properties);
        return new RuntimeOptions()
                .setConnectTimeout(toPositiveMillis(properties.connectTimeout(), "connect-timeout"))
                .setReadTimeout(toPositiveMillis(properties.readTimeout(), "read-timeout"))
                .setIgnoreSSL(false)
                .setAutoretry(false)
                .setMaxAttempts(1);
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
