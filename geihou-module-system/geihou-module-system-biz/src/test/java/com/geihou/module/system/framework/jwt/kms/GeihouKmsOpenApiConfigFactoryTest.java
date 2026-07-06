package com.geihou.module.system.framework.jwt.kms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aliyun.dkms.gcs.openapi.models.Config;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GeihouKmsOpenApiConfigFactoryTest {

    @Test
    void shouldBuildAllowlistedOpenApiConfigWithoutReadingFiles() {
        GeihouKmsProperties properties = validProperties();

        Config config = GeihouKmsOpenApiConfigFactory.create(properties, "runtime-password");

        assertThat(config.getProtocol()).isEqualTo("https");
        assertThat(config.getEndpoint()).isEqualTo("kms-example.cryptoservice.kms.aliyuncs.com");
        assertThat(config.getClientKeyFile()).isEqualTo("/missing/geihou/client-key.json");
        assertThat(config.getPassword()).isEqualTo("runtime-password");
        assertThat(config.getCaFilePath()).isEqualTo("/missing/geihou/kms-ca.pem");
        assertThat(config.getConnectTimeout()).isEqualTo(2_000L);
        assertThat(config.getReadTimeout()).isEqualTo(4_000L);
        assertThat(config.getIgnoreSSL()).isFalse();
    }

    @Test
    void shouldNotSetForbiddenCredentialOrProxyFields() {
        Config config = GeihouKmsOpenApiConfigFactory.create(validProperties(), "runtime-password");

        assertThat(config.getAccessKeyId()).isNull();
        assertThat(config.getPrivateKey()).isNull();
        assertThat(config.getCredential()).isNull();
        assertThat(config.getClientKeyContent()).isNull();
        assertThat(config.getCa()).isNull();
        assertThat(config.getHttpProxy()).isNull();
        assertThat(config.getHttpsProxy()).isNull();
        assertThat(config.getNoProxy()).isNull();
        assertThat(config.getSocks5Proxy()).isNull();
        assertThat(config.getSocks5NetWork()).isNull();
    }

    @Test
    void shouldSetOptionalRedactedUserAgent() {
        Config config = GeihouKmsOpenApiConfigFactory.create(
                validProperties(), "runtime-password", "geihou-auth-kms/h94");

        assertThat(config.getUserAgent()).isEqualTo("geihou-auth-kms/h94");
    }

    @Test
    void shouldRejectDisabledPropertiesAndBlankPassword() {
        assertThatThrownBy(() -> GeihouKmsOpenApiConfigFactory.create(
                GeihouKmsProperties.disabledDefaults(), "runtime-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enabled");

        assertThatThrownBy(() -> GeihouKmsOpenApiConfigFactory.create(validProperties(), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
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
