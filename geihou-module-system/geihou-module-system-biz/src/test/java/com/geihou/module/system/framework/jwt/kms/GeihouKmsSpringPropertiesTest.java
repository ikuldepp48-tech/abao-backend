package com.geihou.module.system.framework.jwt.kms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geihou.framework.security.config.GeihouSecurityProperties;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

class GeihouKmsSpringPropertiesTest {

    @Test
    void shouldConvertEmptyBindingToDisabledDomainDefaults() {
        GeihouKmsSpringProperties springProperties = bind(Map.of());

        GeihouKmsProperties domain = springProperties.toDomainProperties();

        assertThat(domain.enabled()).isFalse();
        assertThat(domain.secretNamePrefix()).isEmpty();
        assertThat(domain.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(domain.readTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(domain.cacheTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(domain.staleCacheTtl()).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldBindKebabCasePropertiesAndConvertToDomain() {
        GeihouKmsSpringProperties springProperties = bind(validEnabledProperties());

        GeihouKmsProperties domain = springProperties.toDomainProperties();

        assertThat(domain.enabled()).isTrue();
        assertThat(domain.endpoint()).isEqualTo("kms-example.cryptoservice.kms.aliyuncs.com");
        assertThat(domain.clientKeyFile()).isEqualTo("/run/secrets/geihou/client-key.json");
        assertThat(domain.clientKeyPasswordEnv()).isEqualTo("GEIHOU_KMS_CLIENT_KEY_PASSWORD");
        assertThat(domain.caCertFile()).isEqualTo("/run/secrets/geihou/kms-ca.pem");
        assertThat(domain.secretNamePrefix()).isEqualTo("geihou/auth/jwt/");
        assertThat(domain.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(domain.readTimeout()).isEqualTo(Duration.ofSeconds(4));
        assertThat(domain.cacheTtl()).isEqualTo(Duration.ofMinutes(3));
        assertThat(domain.staleCacheTtl()).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldKeepExistingSecurityPropertiesDefaultsWhenKmsSubNamespaceExists() {
        Binder binder = binder(Map.of(
                "geihou.security.kms.enabled", "true",
                "geihou.security.kms.endpoint", "kms-example.cryptoservice.kms.aliyuncs.com"));

        GeihouSecurityProperties securityProperties = binder
                .bind("geihou.security", Bindable.of(GeihouSecurityProperties.class))
                .orElseGet(GeihouSecurityProperties::new);

        assertThat(securityProperties.getProtectedPathPatterns()).containsExactly("/admin-api/**");
        assertThat(securityProperties.getPermitPathPatterns()).containsExactly("/admin-api/auth/**");
    }

    @Test
    void shouldDelegateDomainValidationForInvalidEnabledProperties() {
        assertThatThrownBy(() -> bind(validEnabledPropertiesWith("geihou.security.kms.endpoint", " ")).toDomainProperties())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint");
        assertThatThrownBy(() -> bind(validEnabledPropertiesWith(
                "geihou.security.kms.client-key-file", "client-key.json")).toDomainProperties())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client-key-file");
        assertThatThrownBy(() -> bind(validEnabledPropertiesWith(
                "geihou.security.kms.client-key-password-env", "actual-password-123")).toDomainProperties())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client-key-password-env");
        assertThatThrownBy(() -> bind(validEnabledPropertiesWith(
                "geihou.security.kms.secret-name-prefix", "geihou/auth/jwt")).toDomainProperties())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secret-name-prefix");
    }

    @Test
    void shouldRemainOnlyConfigurationPropertiesBinderAdapter() {
        assertThat(GeihouKmsSpringProperties.class)
                .hasAnnotation(ConfigurationProperties.class);
        assertThat(GeihouKmsSpringProperties.class.isAnnotationPresent(Component.class)).isFalse();
        assertThat(GeihouKmsSpringProperties.class.isAnnotationPresent(Service.class)).isFalse();
        assertThat(GeihouKmsSpringProperties.class.isAnnotationPresent(Configuration.class)).isFalse();
        assertThat(GeihouKmsSpringProperties.class.isAnnotationPresent(Bean.class)).isFalse();
        assertThat(GeihouKmsSpringProperties.class.isAnnotationPresent(ComponentScan.class)).isFalse();
        assertThat(GeihouKmsSpringProperties.class.getAnnotation(ConfigurationProperties.class).prefix())
                .isEqualTo("geihou.security.kms");
    }

    @Test
    void shouldNotExposeRawSecretOrPasswordFields() {
        assertThat(GeihouKmsSpringProperties.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("clientKeyPassword", "password", "secretData", "secret", "token", "keyBytes");
    }

    private static GeihouKmsSpringProperties bind(Map<String, String> properties) {
        return binder(properties)
                .bind("geihou.security.kms", Bindable.of(GeihouKmsSpringProperties.class))
                .orElseGet(GeihouKmsSpringProperties::new);
    }

    private static Binder binder(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties));
    }

    private static Map<String, String> validEnabledProperties() {
        return Map.of(
                "geihou.security.kms.enabled", "true",
                "geihou.security.kms.endpoint", "kms-example.cryptoservice.kms.aliyuncs.com",
                "geihou.security.kms.client-key-file", "/run/secrets/geihou/client-key.json",
                "geihou.security.kms.client-key-password-env", "GEIHOU_KMS_CLIENT_KEY_PASSWORD",
                "geihou.security.kms.ca-cert-file", "/run/secrets/geihou/kms-ca.pem",
                "geihou.security.kms.secret-name-prefix", "geihou/auth/jwt/",
                "geihou.security.kms.connect-timeout", "2s",
                "geihou.security.kms.read-timeout", "4s",
                "geihou.security.kms.cache-ttl", "3m",
                "geihou.security.kms.stale-cache-ttl", "0s");
    }

    private static Map<String, String> validEnabledPropertiesWith(String key, String value) {
        java.util.HashMap<String, String> properties = new java.util.HashMap<>(validEnabledProperties());
        properties.put(key, value);
        return properties;
    }
}
