package com.geihou.module.system.framework.jwt.kms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.aliyun.dkms.gcs.openapi.models.Config;
import com.aliyun.dkms.gcs.sdk.Client;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AliyunDkmsSdkClientFactoryTest {

    @Test
    void shouldRejectNullConfigBeforeConstructingClient() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        AliyunDkmsSdkClientFactory factory = new AliyunDkmsSdkClientFactory(config -> {
            invoked.set(true);
            return mock(Client.class);
        });

        assertThatThrownBy(() -> factory.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config");
        assertThat(invoked).isFalse();
    }

    @Test
    void shouldDelegateToInjectedConstructorAndReturnClient() {
        Config config = new Config().setEndpoint("kms.example.internal");
        Client client = mock(Client.class);
        AtomicReference<Config> capturedConfig = new AtomicReference<>();
        AliyunDkmsSdkClientFactory factory = new AliyunDkmsSdkClientFactory(candidate -> {
            capturedConfig.set(candidate);
            return client;
        });

        Client actual = factory.create(config);

        assertThat(actual).isSameAs(client);
        assertThat(capturedConfig).hasValue(config);
    }

    @Test
    void shouldFailClosedWhenConstructorReturnsNull() {
        Config config = new Config().setEndpoint("kms.example.internal");
        AliyunDkmsSdkClientFactory factory = new AliyunDkmsSdkClientFactory(candidate -> null);

        assertThatThrownBy(() -> factory.create(config))
                .isInstanceOf(GeihouKmsClientException.class)
                .hasMessage("KMS SDK client initialization failed");
    }

    @Test
    void shouldWrapConstructorExceptionWithRedactedMessage() {
        Config config = new Config()
                .setEndpoint("kms.example.internal")
                .setClientKeyFile("/secret/client.key")
                .setPassword("raw-password")
                .setCaFilePath("/secret/ca.pem");
        AliyunDkmsSdkClientFactory factory = new AliyunDkmsSdkClientFactory(candidate -> {
            throw new Exception("raw-password /secret/client.key /secret/ca.pem kms.example.internal");
        });

        assertThatThrownBy(() -> factory.create(config))
                .isInstanceOf(GeihouKmsClientException.class)
                .hasMessage("KMS SDK client initialization failed")
                .hasMessageNotContaining("raw-password")
                .hasMessageNotContaining("/secret/client.key")
                .hasMessageNotContaining("/secret/ca.pem")
                .hasMessageNotContaining("kms.example.internal");
    }

    @Test
    void shouldRejectNullInjectedConstructor() {
        assertThatThrownBy(() -> new AliyunDkmsSdkClientFactory(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client-constructor");
    }
}
