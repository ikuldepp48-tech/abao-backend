package com.geihou.module.system.framework.jwt.kms;

import com.aliyun.dkms.gcs.openapi.util.models.RuntimeOptions;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueRequest;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueResponse;

public interface GeihouKmsSecretClient {

    GetSecretValueResponse getSecretValue(GetSecretValueRequest request, RuntimeOptions runtimeOptions);
}
