package com.geihou.module.system.framework.jwt.kms;

import com.aliyun.dkms.gcs.openapi.util.models.RuntimeOptions;
import com.aliyun.dkms.gcs.sdk.Client;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueRequest;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueResponse;

/**
 * Thin adapter over a pre-supplied Aliyun DKMS SDK client.
 */
public final class AliyunDkmsSecretClientAdapter implements GeihouKmsSecretClient {

    private static final String FAILURE_MESSAGE = "KMS GetSecretValue failed";
    private static final String EMPTY_RESPONSE_MESSAGE = "KMS GetSecretValue returned empty response";

    private final Client sdkClient;

    public AliyunDkmsSecretClientAdapter(Client sdkClient) {
        if (sdkClient == null) {
            throw new IllegalArgumentException("sdk-client must not be null");
        }
        this.sdkClient = sdkClient;
    }

    @Override
    public GetSecretValueResponse getSecretValue(GetSecretValueRequest request, RuntimeOptions runtimeOptions) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (runtimeOptions == null) {
            throw new IllegalArgumentException("runtime-options must not be null");
        }
        try {
            GetSecretValueResponse response = sdkClient.getSecretValueWithOptions(request, runtimeOptions);
            if (response == null) {
                throw new GeihouKmsClientException(EMPTY_RESPONSE_MESSAGE);
            }
            return response;
        } catch (GeihouKmsClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GeihouKmsClientException(FAILURE_MESSAGE);
        }
    }
}
