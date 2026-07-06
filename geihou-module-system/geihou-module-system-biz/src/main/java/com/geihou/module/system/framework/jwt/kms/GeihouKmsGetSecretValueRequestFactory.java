package com.geihou.module.system.framework.jwt.kms;

import com.aliyun.dkms.gcs.sdk.models.GetSecretValueRequest;

/**
 * Builds the minimal GetSecretValue request approved for the KMS compile gate.
 */
public final class GeihouKmsGetSecretValueRequestFactory {

    private GeihouKmsGetSecretValueRequestFactory() {
    }

    public static GetSecretValueRequest create(String secretName) {
        if (secretName == null || secretName.isBlank()) {
            throw new IllegalArgumentException("secret-name must not be blank");
        }
        return new GetSecretValueRequest()
                .setSecretName(secretName)
                .setFetchExtendedConfig(false);
    }
}
