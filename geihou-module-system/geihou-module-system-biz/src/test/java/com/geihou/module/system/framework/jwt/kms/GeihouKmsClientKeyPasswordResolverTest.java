package com.geihou.module.system.framework.jwt.kms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class GeihouKmsClientKeyPasswordResolverTest {

    @Test
    void shouldRejectNullPasswordSource() {
        assertThatThrownBy(() -> new GeihouKmsClientKeyPasswordResolver(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password-source");
    }

    @Test
    void shouldResolveUsingConfiguredEnvironmentVariableNameWithoutTrimming() {
        AtomicReference<String> requestedName = new AtomicReference<>();
        GeihouKmsClientKeyPasswordResolver resolver = new GeihouKmsClientKeyPasswordResolver(name -> {
            requestedName.set(name);
            return "  runtime-password  ";
        });

        String password = resolver.resolve(validProperties());

        assertThat(requestedName).hasValue("GEIHOU_KMS_CLIENT_KEY_PASSWORD");
        assertThat(password).isEqualTo("  runtime-password  ");
    }

    @Test
    void shouldFailClosedWhenPropertiesAreDisabled() {
        GeihouKmsClientKeyPasswordResolver resolver = new GeihouKmsClientKeyPasswordResolver(name -> "password");

        assertThatThrownBy(() -> resolver.resolve(GeihouKmsProperties.disabledDefaults()))
                .isInstanceOf(GeihouKmsCredentialException.class)
                .hasMessage("KMS ClientKey password is unavailable");
    }

    @Test
    void shouldFailClosedWhenSourceReturnsNullOrBlank() {
        assertThatThrownBy(() -> new GeihouKmsClientKeyPasswordResolver(name -> null).resolve(validProperties()))
                .isInstanceOf(GeihouKmsCredentialException.class)
                .hasMessage("KMS ClientKey password is unavailable");

        assertThatThrownBy(() -> new GeihouKmsClientKeyPasswordResolver(name -> "  ").resolve(validProperties()))
                .isInstanceOf(GeihouKmsCredentialException.class)
                .hasMessage("KMS ClientKey password is unavailable");
    }

    @Test
    void shouldFailClosedWithRedactedMessageWhenSourceThrows() {
        Function<String, String> throwingSource = name -> {
            throw new IllegalStateException("password=raw-secret endpoint=kms-example");
        };
        GeihouKmsClientKeyPasswordResolver resolver = new GeihouKmsClientKeyPasswordResolver(throwingSource);

        assertThatThrownBy(() -> resolver.resolve(validProperties()))
                .isInstanceOf(GeihouKmsCredentialException.class)
                .hasMessage("KMS ClientKey password resolution failed")
                .hasMessageNotContaining("raw-secret")
                .hasMessageNotContaining("kms-example")
                .hasNoCause();
    }

    @Test
    void shouldNotStoreResolvedPasswordInFields() {
        Field[] fields = GeihouKmsClientKeyPasswordResolver.class.getDeclaredFields();

        assertThat(fields).singleElement()
                .extracting(Field::getName)
                .isEqualTo("passwordSource");
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
