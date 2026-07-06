package com.geihou.module.system.framework.jwt.kms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aliyun.dkms.gcs.openapi.util.models.RuntimeOptions;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GeihouKmsRuntimeOptionsFactoryTest {

    @Test
    void shouldBuildSingleAttemptSslVerifyingRuntimeOptions() {
        RuntimeOptions options = GeihouKmsRuntimeOptionsFactory.create(validProperties());

        assertThat(options.getConnectTimeout()).isEqualTo(2_000L);
        assertThat(options.getReadTimeout()).isEqualTo(4_000L);
        assertThat(options.getIgnoreSSL()).isFalse();
        assertThat(options.getAutoretry()).isFalse();
        assertThat(options.getMaxAttempts()).isEqualTo(1);
    }

    @Test
    void shouldNotSetProxyOrBackoffFields() {
        RuntimeOptions options = GeihouKmsRuntimeOptionsFactory.create(validProperties());

        assertThat(options.getHttpProxy()).isNull();
        assertThat(options.getHttpsProxy()).isNull();
        assertThat(options.getNoProxy()).isNull();
        assertThat(options.getSocks5Proxy()).isNull();
        assertThat(options.getSocks5NetWork()).isNull();
        assertThat(options.getBackoffPolicy()).isNull();
        assertThat(options.getBackoffPeriod()).isNull();
        assertThat(options.getResponseHeaders()).isNull();
    }

    @Test
    void shouldRejectDisabledProperties() {
        assertThatThrownBy(() -> GeihouKmsRuntimeOptionsFactory.create(GeihouKmsProperties.disabledDefaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enabled");
    }

    private static GeihouKmsProperties validProperties() {
        return GeihouKmsProperties.of(
                true,
                "kms-example.cryptoservice.kms.aliyuncs.com",
                "/missing/geihou/client-key.json",
                "GEIHOU_KMS_CLIENT_KEY_PASSWORD",
                "/missing/geihou/kms-ca.pem",
                "geihou/auth/jwt/",
                Duration.ofSeconds(2),
                Duration.ofSeconds(4),
                Duration.ofMinutes(3),
                Duration.ZERO);
    }
}
