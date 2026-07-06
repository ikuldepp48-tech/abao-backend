package com.geihou.module.system.framework.jwt.kms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GeihouKmsSecretNameMapperTest {

    @Test
    void shouldMapValidKidToPrefixAndKidWithoutNormalization() {
        GeihouKmsSecretNameMapper mapper = new GeihouKmsSecretNameMapper("geihou/auth/jwt/");

        String secretName = mapper.toSecretName("Tenant.JWT_KID-01:AB");

        assertThat(secretName).isEqualTo("geihou/auth/jwt/Tenant.JWT_KID-01:AB");
    }

    @Test
    void shouldAllowEmptyPrefix() {
        GeihouKmsSecretNameMapper mapper = new GeihouKmsSecretNameMapper("");

        assertThat(mapper.toSecretName("jwt-kid-0001")).isEqualTo("jwt-kid-0001");
    }

    @Test
    void shouldRejectPrefixWithoutTrailingSlash() {
        assertThatThrownBy(() -> new GeihouKmsSecretNameMapper("geihou/auth/jwt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secret-name-prefix");
    }

    @Test
    void shouldRejectBlankOrShortKid() {
        GeihouKmsSecretNameMapper mapper = new GeihouKmsSecretNameMapper("geihou/auth/jwt/");

        assertThatThrownBy(() -> mapper.toSecretName(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kid");
        assertThatThrownBy(() -> mapper.toSecretName(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kid");
        assertThatThrownBy(() -> mapper.toSecretName("short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kid");
    }

    @Test
    void shouldRejectUnsafeKidCharacters() {
        GeihouKmsSecretNameMapper mapper = new GeihouKmsSecretNameMapper("geihou/auth/jwt/");

        assertThatThrownBy(() -> mapper.toSecretName("kid/with/slash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kid");
        assertThatThrownBy(() -> mapper.toSecretName("kid with space"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kid");
        assertThatThrownBy(() -> mapper.toSecretName("../jwt-kid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kid");
    }

    @Test
    void shouldRejectKidLongerThanLimit() {
        GeihouKmsSecretNameMapper mapper = new GeihouKmsSecretNameMapper("geihou/auth/jwt/");

        assertThatThrownBy(() -> mapper.toSecretName("a".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kid");
    }
}
