package com.geihou.module.system.framework.jwt.kms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aliyun.dkms.gcs.sdk.models.GetSecretValueRequest;
import org.junit.jupiter.api.Test;

class GeihouKmsGetSecretValueRequestFactoryTest {

    @Test
    void shouldSetOnlySecretNameAndFetchExtendedConfigFalse() {
        GetSecretValueRequest request = GeihouKmsGetSecretValueRequestFactory.create(
                "geihou/auth/jwt/Tenant.JWT_KID-01:AB");

        assertThat(request.getSecretName()).isEqualTo("geihou/auth/jwt/Tenant.JWT_KID-01:AB");
        assertThat(request.getFetchExtendedConfig()).isFalse();
        assertThat(request.getVersionStage()).isNull();
        assertThat(request.getVersionId()).isNull();
        assertThat(request.getRequestHeaders()).isNull();
    }

    @Test
    void shouldRejectBlankSecretName() {
        assertThatThrownBy(() -> GeihouKmsGetSecretValueRequestFactory.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secret-name");
        assertThatThrownBy(() -> GeihouKmsGetSecretValueRequestFactory.create(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secret-name");
    }
}
