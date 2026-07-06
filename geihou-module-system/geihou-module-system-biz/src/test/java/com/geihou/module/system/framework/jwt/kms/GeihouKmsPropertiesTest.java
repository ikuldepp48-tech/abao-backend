package com.geihou.module.system.framework.jwt.kms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GeihouKmsPropertiesTest {

    @Test
    void shouldCreateDisabledDefaultsWithoutRequiredRuntimeCredentials() {
        GeihouKmsProperties properties = GeihouKmsProperties.disabledDefaults();

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.secretNamePrefix()).isEmpty();
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.cacheTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.staleCacheTtl()).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldAcceptEnabledPropertiesWithAbsoluteCredentialPathsAndPasswordEnvName() {
        GeihouKmsProperties properties = GeihouKmsProperties.of(
                true,
                "kms-example.cryptoservice.kms.aliyuncs.com",
                "/run/secrets/geihou/client-key.json",
                "GEIHOU_KMS_CLIENT_KEY_PASSWORD",
                "/run/secrets/geihou/kms-ca.pem",
                "geihou/auth/jwt/",
                Duration.ofSeconds(2),
                Duration.ofSeconds(4),
                Duration.ofMinutes(3),
                Duration.ZERO);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.endpoint()).isEqualTo("kms-example.cryptoservice.kms.aliyuncs.com");
        assertThat(properties.clientKeyFile()).isEqualTo("/run/secrets/geihou/client-key.json");
        assertThat(properties.clientKeyPasswordEnv()).isEqualTo("GEIHOU_KMS_CLIENT_KEY_PASSWORD");
        assertThat(properties.caCertFile()).isEqualTo("/run/secrets/geihou/kms-ca.pem");
        assertThat(properties.secretNamePrefix()).isEqualTo("geihou/auth/jwt/");
    }

    @Test
    void shouldRejectProtocolBearingEndpoint() {
        assertThatThrownBy(() -> validEnabledBuilder().endpoint("https://kms-example").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void shouldRejectRelativeClientKeyPath() {
        assertThatThrownBy(() -> validEnabledBuilder().clientKeyFile("client-key.json").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client-key-file");
    }

    @Test
    void shouldRejectRelativeCaCertificatePath() {
        assertThatThrownBy(() -> validEnabledBuilder().caCertFile("kms-ca.pem").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ca-cert-file");
    }

    @Test
    void shouldRejectPasswordValueInsteadOfEnvironmentVariableName() {
        assertThatThrownBy(() -> validEnabledBuilder().clientKeyPasswordEnv("actual-password-123").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client-key-password-env");
    }

    @Test
    void shouldRejectSecretNamePrefixWithoutTrailingSlash() {
        assertThatThrownBy(() -> validEnabledBuilder().secretNamePrefix("geihou/auth/jwt").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secret-name-prefix");
    }

    @Test
    void shouldRejectNonPositiveTimeoutsAndCacheTtl() {
        assertThatThrownBy(() -> validEnabledBuilder().connectTimeout(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connect-timeout");
        assertThatThrownBy(() -> validEnabledBuilder().readTimeout(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("read-timeout");
        assertThatThrownBy(() -> validEnabledBuilder().cacheTtl(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cache-ttl");
    }

    @Test
    void shouldRejectNegativeStaleCacheTtl() {
        assertThatThrownBy(() -> validEnabledBuilder().staleCacheTtl(Duration.ofSeconds(-1)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stale-cache-ttl");
    }

    private static GeihouKmsPropertiesBuilder validEnabledBuilder() {
        return new GeihouKmsPropertiesBuilder();
    }

    private static final class GeihouKmsPropertiesBuilder {

        private String endpoint = "kms-example.cryptoservice.kms.aliyuncs.com";
        private String clientKeyFile = "/run/secrets/geihou/client-key.json";
        private String clientKeyPasswordEnv = "GEIHOU_KMS_CLIENT_KEY_PASSWORD";
        private String caCertFile = "/run/secrets/geihou/kms-ca.pem";
        private String secretNamePrefix = "geihou/auth/jwt/";
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(5);
        private Duration cacheTtl = Duration.ofMinutes(5);
        private Duration staleCacheTtl = Duration.ZERO;

        private GeihouKmsPropertiesBuilder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        private GeihouKmsPropertiesBuilder clientKeyFile(String clientKeyFile) {
            this.clientKeyFile = clientKeyFile;
            return this;
        }

        private GeihouKmsPropertiesBuilder clientKeyPasswordEnv(String clientKeyPasswordEnv) {
            this.clientKeyPasswordEnv = clientKeyPasswordEnv;
            return this;
        }

        private GeihouKmsPropertiesBuilder caCertFile(String caCertFile) {
            this.caCertFile = caCertFile;
            return this;
        }

        private GeihouKmsPropertiesBuilder secretNamePrefix(String secretNamePrefix) {
            this.secretNamePrefix = secretNamePrefix;
            return this;
        }

        private GeihouKmsPropertiesBuilder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        private GeihouKmsPropertiesBuilder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        private GeihouKmsPropertiesBuilder cacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
            return this;
        }

        private GeihouKmsPropertiesBuilder staleCacheTtl(Duration staleCacheTtl) {
            this.staleCacheTtl = staleCacheTtl;
            return this;
        }

        private GeihouKmsProperties build() {
            return GeihouKmsProperties.of(
                    true,
                    endpoint,
                    clientKeyFile,
                    clientKeyPasswordEnv,
                    caCertFile,
                    secretNamePrefix,
                    connectTimeout,
                    readTimeout,
                    cacheTtl,
                    staleCacheTtl);
        }
    }
}
