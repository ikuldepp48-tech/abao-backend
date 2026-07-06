package com.geihou.module.system.framework.jwt.kms;

import com.aliyun.dkms.gcs.openapi.models.Config;
import com.aliyun.dkms.gcs.sdk.Client;

/**
 * Creates Aliyun DKMS SDK clients behind a small, testable construction boundary.
 */
public final class AliyunDkmsSdkClientFactory {

    private static final String FAILURE_MESSAGE = "KMS SDK client initialization failed";

    private final ClientConstructor clientConstructor;

    public AliyunDkmsSdkClientFactory() {
        this(Client::new);
    }

    AliyunDkmsSdkClientFactory(ClientConstructor clientConstructor) {
        if (clientConstructor == null) {
            throw new IllegalArgumentException("client-constructor must not be null");
        }
        this.clientConstructor = clientConstructor;
    }

    public Client create(Config config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        try {
            Client client = clientConstructor.create(config);
            if (client == null) {
                throw new GeihouKmsClientException(FAILURE_MESSAGE);
            }
            return client;
        } catch (GeihouKmsClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GeihouKmsClientException(FAILURE_MESSAGE);
        }
    }

    @FunctionalInterface
    interface ClientConstructor {
        Client create(Config config) throws Exception;
    }
}
