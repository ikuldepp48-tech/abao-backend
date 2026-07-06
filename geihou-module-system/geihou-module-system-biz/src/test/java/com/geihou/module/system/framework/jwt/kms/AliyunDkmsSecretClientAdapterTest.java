package com.geihou.module.system.framework.jwt.kms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aliyun.dkms.gcs.openapi.util.models.RuntimeOptions;
import com.aliyun.dkms.gcs.sdk.Client;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueRequest;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueResponse;
import org.junit.jupiter.api.Test;

class AliyunDkmsSecretClientAdapterTest {

    @Test
    void shouldDelegateToGetSecretValueWithOptions() throws Exception {
        Client sdkClient = mock(Client.class);
        GetSecretValueRequest request = new GetSecretValueRequest().setSecretName("geihou/auth/jwt/kid-0001");
        RuntimeOptions runtimeOptions = new RuntimeOptions().setIgnoreSSL(false);
        GetSecretValueResponse response = new GetSecretValueResponse().setSecretData("secret-data");
        when(sdkClient.getSecretValueWithOptions(same(request), same(runtimeOptions))).thenReturn(response);
        GeihouKmsSecretClient adapter = new AliyunDkmsSecretClientAdapter(sdkClient);

        GetSecretValueResponse actual = adapter.getSecretValue(request, runtimeOptions);

        assertThat(actual).isSameAs(response);
        verify(sdkClient).getSecretValueWithOptions(same(request), same(runtimeOptions));
    }

    @Test
    void shouldWrapSdkExceptionWithRedactedMessage() throws Exception {
        Client sdkClient = mock(Client.class);
        GetSecretValueRequest request = new GetSecretValueRequest().setSecretName("geihou/auth/jwt/kid-0001");
        RuntimeOptions runtimeOptions = new RuntimeOptions().setIgnoreSSL(false);
        when(sdkClient.getSecretValueWithOptions(same(request), same(runtimeOptions)))
                .thenThrow(new Exception("password=raw-secret SecretData=raw-data endpoint=kms-example"));
        GeihouKmsSecretClient adapter = new AliyunDkmsSecretClientAdapter(sdkClient);

        assertThatThrownBy(() -> adapter.getSecretValue(request, runtimeOptions))
                .isInstanceOf(GeihouKmsClientException.class)
                .hasMessage("KMS GetSecretValue failed")
                .hasMessageNotContaining("raw-secret")
                .hasMessageNotContaining("raw-data")
                .hasMessageNotContaining("kms-example");
    }

    @Test
    void shouldRejectNullRequestOrRuntimeOptionsBeforeDelegating() throws Exception {
        Client sdkClient = mock(Client.class);
        GeihouKmsSecretClient adapter = new AliyunDkmsSecretClientAdapter(sdkClient);
        RuntimeOptions runtimeOptions = new RuntimeOptions().setIgnoreSSL(false);
        GetSecretValueRequest request = new GetSecretValueRequest().setSecretName("geihou/auth/jwt/kid-0001");

        assertThatThrownBy(() -> adapter.getSecretValue(null, runtimeOptions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request");
        assertThatThrownBy(() -> adapter.getSecretValue(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime-options");
        verify(sdkClient, never()).getSecretValueWithOptions(same(request), same(runtimeOptions));
    }

    @Test
    void shouldRejectNullSdkResponse() throws Exception {
        Client sdkClient = mock(Client.class);
        GetSecretValueRequest request = new GetSecretValueRequest().setSecretName("geihou/auth/jwt/kid-0001");
        RuntimeOptions runtimeOptions = new RuntimeOptions().setIgnoreSSL(false);
        when(sdkClient.getSecretValueWithOptions(same(request), same(runtimeOptions))).thenReturn(null);
        GeihouKmsSecretClient adapter = new AliyunDkmsSecretClientAdapter(sdkClient);

        assertThatThrownBy(() -> adapter.getSecretValue(request, runtimeOptions))
                .isInstanceOf(GeihouKmsClientException.class)
                .hasMessage("KMS GetSecretValue returned empty response");
    }

    @Test
    void shouldRejectNullSdkClient() {
        assertThatThrownBy(() -> new AliyunDkmsSecretClientAdapter(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sdk-client");
    }
}
