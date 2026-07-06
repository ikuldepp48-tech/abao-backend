package com.geihou.module.system.framework.jwt.kms;

import com.aliyun.dkms.gcs.openapi.util.models.RuntimeOptions;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueRequest;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueResponse;
import com.geihou.module.system.framework.jwt.GeihouActiveKidProvider;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the issuer-side active signing key id from one reserved KMS marker secret.
 */
public final class GeihouKmsActiveKidProvider implements GeihouActiveKidProvider {

    static final String MARKER_KID = "__active-kid";

    private static final String TEXT_SECRET_DATA_TYPE = "text";

    private final GeihouKmsProperties properties;
    private final GeihouKmsSecretNameMapper secretNameMapper;
    private final GeihouKmsSecretClient secretClient;

    public GeihouKmsActiveKidProvider(
            GeihouKmsProperties properties,
            GeihouKmsSecretNameMapper secretNameMapper,
            GeihouKmsSecretClient secretClient) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.secretNameMapper = Objects.requireNonNull(secretNameMapper, "secretNameMapper must not be null");
        this.secretClient = Objects.requireNonNull(secretClient, "secretClient must not be null");
    }

    @Override
    public Optional<String> currentKid() {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        try {
            GetSecretValueRequest request = GeihouKmsGetSecretValueRequestFactory.create(
                    secretNameMapper.toSecretName(MARKER_KID));
            RuntimeOptions runtimeOptions = GeihouKmsRuntimeOptionsFactory.create(properties);
            return decode(secretClient.getSecretValue(request, runtimeOptions));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<String> decode(GetSecretValueResponse response) {
        if (response == null || !TEXT_SECRET_DATA_TYPE.equals(response.getSecretDataType())) {
            return Optional.empty();
        }

        String activeKid = response.getSecretData();
        if (activeKid == null || activeKid.isBlank() || MARKER_KID.equals(activeKid)) {
            return Optional.empty();
        }

        try {
            secretNameMapper.toSecretName(activeKid);
            return Optional.of(activeKid);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }
}
