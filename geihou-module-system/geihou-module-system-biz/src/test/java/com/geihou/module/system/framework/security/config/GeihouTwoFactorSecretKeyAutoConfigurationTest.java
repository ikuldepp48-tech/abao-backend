package com.geihou.module.system.framework.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.geihou.module.system.framework.jwt.kms.GeihouKmsProperties;
import com.geihou.module.system.framework.jwt.kms.GeihouKmsSecretClient;
import com.geihou.module.system.service.auth.EnvTwoFactorSecretKeyProvider;
import com.geihou.module.system.service.auth.GeihouTwoFactorSpringProperties;
import com.geihou.module.system.service.auth.KmsTwoFactorSecretKeyProvider;
import com.geihou.module.system.service.auth.TwoFactorSecretKeyProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GeihouTwoFactorSecretKeyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GeihouTwoFactorSecretKeyAutoConfiguration.class));

    @Test
    void shouldNotRegisterAnyBeanWhenKeySourceIsNotConfigured() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(TwoFactorSecretKeyProvider.class);
            assertThat(context).doesNotHaveBean(GeihouTwoFactorSpringProperties.class);
        });
    }

    @Test
    void shouldFailStartupWhenKeySourceIsEnvButEnvVarIsMissing() {
        contextRunner
                .withPropertyValues(
                        "geihou.security.two-factor.key-source=ENV",
                        "geihou.security.two-factor.aes-key-env=GEIHOU_2FA_NONEXISTENT_VAR")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(org.springframework.beans.factory.BeanCreationException.class)
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("2FA AES key environment variable is missing or blank");
                });
    }

    @Test
    void shouldFailStartupWhenKeySourceIsEnvButAesKeyEnvIsBlank() {
        contextRunner
                .withPropertyValues(
                        "geihou.security.two-factor.key-source=ENV",
                        "geihou.security.two-factor.aes-key-env=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(org.springframework.beans.factory.BeanCreationException.class)
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("aes-key-env must not be blank");
                });
    }

    @Test
    void shouldBackOffWhenUserProvidesCustomTwoFactorSecretKeyProvider() {
        TwoFactorSecretKeyProvider customProvider = mock(TwoFactorSecretKeyProvider.class);

        contextRunner
                .withPropertyValues(
                        "geihou.security.two-factor.key-source=ENV",
                        "geihou.security.two-factor.aes-key-env=GEIHOU_2FA_NONEXISTENT_VAR")
                .withBean(TwoFactorSecretKeyProvider.class, () -> customProvider)
                .run(context -> {
                    assertThat(context).hasSingleBean(TwoFactorSecretKeyProvider.class);
                    assertThat(context.getBean(TwoFactorSecretKeyProvider.class)).isSameAs(customProvider);
                    assertThat(context).doesNotHaveBean(EnvTwoFactorSecretKeyProvider.class);
                });
    }

    @Test
    void shouldNotRegisterKmsProviderWhenKmsSecretClientIsMissing() {
        contextRunner
                .withPropertyValues(
                        "geihou.security.two-factor.key-source=KMS",
                        "geihou.security.two-factor.kms.secret-name=geihou/auth/2fa/aes-key")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TwoFactorSecretKeyProvider.class);
                    assertThat(context).doesNotHaveBean(KmsTwoFactorSecretKeyProvider.class);
                });
    }

    @Test
    void shouldRegisterKmsProviderWhenKmsSecretClientExists() {
        GeihouKmsProperties kmsProperties = GeihouKmsProperties.of(
                true,
                "kms-example.cryptoservice.kms.aliyuncs.com",
                "/missing/geihou/client-key.json",
                "GEIHOU_KMS_CLIENT_KEY_PASSWORD",
                "/missing/geihou/kms-ca.pem",
                "geihou/auth/",
                Duration.ofSeconds(2),
                Duration.ofSeconds(4),
                Duration.ofMinutes(3),
                Duration.ZERO);

        contextRunner
                .withPropertyValues(
                        "geihou.security.two-factor.key-source=KMS",
                        "geihou.security.two-factor.kms.secret-name=geihou/auth/2fa/aes-key")
                .withBean(GeihouKmsProperties.class, () -> kmsProperties)
                .withBean(GeihouKmsSecretClient.class, () -> mock(GeihouKmsSecretClient.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(TwoFactorSecretKeyProvider.class);
                    assertThat(context.getBean(TwoFactorSecretKeyProvider.class))
                            .isInstanceOf(KmsTwoFactorSecretKeyProvider.class);
                });
    }

    @Test
    void shouldFailStartupWhenKeySourceIsKmsButSecretNameIsBlank() {
        GeihouKmsProperties kmsProperties = GeihouKmsProperties.of(
                true,
                "kms-example.cryptoservice.kms.aliyuncs.com",
                "/missing/geihou/client-key.json",
                "GEIHOU_KMS_CLIENT_KEY_PASSWORD",
                "/missing/geihou/kms-ca.pem",
                "geihou/auth/",
                Duration.ofSeconds(2),
                Duration.ofSeconds(4),
                Duration.ofMinutes(3),
                Duration.ZERO);

        contextRunner
                .withPropertyValues(
                        "geihou.security.two-factor.key-source=KMS",
                        "geihou.security.two-factor.kms.secret-name=")
                .withBean(GeihouKmsProperties.class, () -> kmsProperties)
                .withBean(GeihouKmsSecretClient.class, () -> mock(GeihouKmsSecretClient.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(org.springframework.beans.factory.BeanCreationException.class)
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("secret-name must not be blank");
                });
    }

    @Test
    void shouldBackOffForKmsModeWhenUserProvidesCustomProvider() {
        TwoFactorSecretKeyProvider customProvider = mock(TwoFactorSecretKeyProvider.class);
        GeihouKmsProperties kmsProperties = GeihouKmsProperties.of(
                true,
                "kms-example.cryptoservice.kms.aliyuncs.com",
                "/missing/geihou/client-key.json",
                "GEIHOU_KMS_CLIENT_KEY_PASSWORD",
                "/missing/geihou/kms-ca.pem",
                "geihou/auth/",
                Duration.ofSeconds(2),
                Duration.ofSeconds(4),
                Duration.ofMinutes(3),
                Duration.ZERO);

        contextRunner
                .withPropertyValues(
                        "geihou.security.two-factor.key-source=KMS",
                        "geihou.security.two-factor.kms.secret-name=geihou/auth/2fa/aes-key")
                .withBean(GeihouKmsProperties.class, () -> kmsProperties)
                .withBean(GeihouKmsSecretClient.class, () -> mock(GeihouKmsSecretClient.class))
                .withBean(TwoFactorSecretKeyProvider.class, () -> customProvider)
                .run(context -> {
                    assertThat(context).hasSingleBean(TwoFactorSecretKeyProvider.class);
                    assertThat(context.getBean(TwoFactorSecretKeyProvider.class)).isSameAs(customProvider);
                    assertThat(context).doesNotHaveBean(KmsTwoFactorSecretKeyProvider.class);
                });
    }

    @Test
    void shouldRegisterTwoFactorBeanGraphWhenKmsProviderIsRegistered() {
        GeihouKmsProperties kmsProperties = GeihouKmsProperties.of(
                true,
                "kms-example.cryptoservice.kms.aliyuncs.com",
                "/missing/geihou/client-key.json",
                "GEIHOU_KMS_CLIENT_KEY_PASSWORD",
                "/missing/geihou/kms-ca.pem",
                "geihou/auth/",
                Duration.ofSeconds(2),
                Duration.ofSeconds(4),
                Duration.ofMinutes(3),
                Duration.ZERO);

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        GeihouTwoFactorSecretKeyAutoConfiguration.class,
                        GeihouAuthLoginTokenServiceAutoConfiguration.class))
                .withPropertyValues(
                        "geihou.security.two-factor.key-source=KMS",
                        "geihou.security.two-factor.kms.secret-name=geihou/auth/2fa/aes-key")
                .withBean(GeihouKmsProperties.class, () -> kmsProperties)
                .withBean(GeihouKmsSecretClient.class, () -> mock(GeihouKmsSecretClient.class))
                .withBean(com.geihou.module.system.framework.jwt.GeihouAccessTokenIssuer.class,
                        () -> mock(com.geihou.module.system.framework.jwt.GeihouAccessTokenIssuer.class))
                .withBean(com.geihou.module.system.dal.mysql.auth.AuthUserRepository.class,
                        () -> mock(com.geihou.module.system.dal.mysql.auth.AuthUserRepository.class))
                .withBean(com.geihou.module.system.dal.mysql.auth.AuthLoginAttemptRepository.class,
                        () -> mock(com.geihou.module.system.dal.mysql.auth.AuthLoginAttemptRepository.class))
                .withBean(com.geihou.module.system.service.auth.GeihouAuthLoginTokenWiringService.class,
                        () -> mock(com.geihou.module.system.service.auth.GeihouAuthLoginTokenWiringService.class))
                .withBean(com.geihou.module.system.service.auth.GeihouRefreshTokenPort.class,
                        () -> mock(com.geihou.module.system.service.auth.GeihouRefreshTokenPort.class))
                .withBean(com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class,
                        () -> mock(com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(TwoFactorSecretKeyProvider.class);
                    assertThat(context).hasSingleBean(
                            com.geihou.module.system.service.auth.TwoFactorSecretCryptoProvider.class);
                    assertThat(context).hasSingleBean(com.geihou.module.system.service.auth.GeihouTotpVerifier.class);
                });
    }

    @Test
    void shouldNotRegisterTwoFactorBeanGraphWhenKeySourceIsNotConfigured() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        GeihouTwoFactorSecretKeyAutoConfiguration.class,
                        GeihouAuthLoginTokenServiceAutoConfiguration.class))
                .withBean(com.geihou.module.system.framework.jwt.GeihouAccessTokenIssuer.class,
                        () -> mock(com.geihou.module.system.framework.jwt.GeihouAccessTokenIssuer.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TwoFactorSecretKeyProvider.class);
                    assertThat(context).doesNotHaveBean(
                            com.geihou.module.system.service.auth.TwoFactorSecretCryptoProvider.class);
                    assertThat(context).doesNotHaveBean(com.geihou.module.system.service.auth.GeihouTotpVerifier.class);
                });
    }
}
