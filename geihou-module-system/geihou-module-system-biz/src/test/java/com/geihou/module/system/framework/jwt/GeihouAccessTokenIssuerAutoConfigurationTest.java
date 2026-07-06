package com.geihou.module.system.framework.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aliyun.dkms.gcs.sdk.Client;
import com.geihou.framework.security.core.spi.TokenValidator;
import com.geihou.module.system.api.auth.AuthApi;
import com.geihou.module.system.dal.mysql.auth.AuthTokenRevokedRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import com.geihou.module.system.framework.jwt.kms.GeihouKmsActiveKidProvider;
import com.geihou.module.system.framework.jwt.kms.GeihouKmsAutoConfiguration;
import com.geihou.module.system.framework.jwt.kms.GeihouKmsSecretClient;
import com.geihou.module.system.framework.jwt.kms.GeihouKmsSigningSecretProvider;
import com.geihou.module.system.framework.security.config.GeihouAuthTokenAutoConfiguration;
import com.geihou.module.system.service.auth.GeihouAuthTokenVerifier;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GeihouAccessTokenIssuerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GeihouAccessTokenIssuerAutoConfiguration.class));

    @Test
    void shouldNotCreateIssuerWhenActiveSigningSecretProviderIsMissing() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(GeihouAccessTokenIssuer.class));
    }

    @Test
    void shouldCreateIssuerWhenActiveSigningSecretProviderExistsWithoutCallingProvider() {
        GeihouActiveSigningSecretProvider activeSigningSecretProvider =
                mock(GeihouActiveSigningSecretProvider.class);

        contextRunner
                .withBean(GeihouActiveSigningSecretProvider.class, () -> activeSigningSecretProvider)
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouAccessTokenIssuer.class);
                    verifyNoInteractions(activeSigningSecretProvider);
                });
    }

    @Test
    void shouldNotOverwriteCustomIssuer() {
        GeihouActiveSigningSecretProvider activeSigningSecretProvider =
                mock(GeihouActiveSigningSecretProvider.class);
        GeihouAccessTokenIssuer customIssuer = new GeihouAccessTokenIssuer(activeSigningSecretProvider);

        contextRunner
                .withBean(GeihouActiveSigningSecretProvider.class, () -> activeSigningSecretProvider)
                .withBean(GeihouAccessTokenIssuer.class, () -> customIssuer)
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouAccessTokenIssuer.class);
                    assertThat(context.getBean(GeihouAccessTokenIssuer.class)).isSameAs(customIssuer);
                    verifyNoInteractions(activeSigningSecretProvider);
                });
    }

    @Test
    void shouldNotCreateIssuerWhenKmsIsMissingOrDisabled() {
        ApplicationContextRunner kmsBridgeIssuerRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        GeihouKmsAutoConfiguration.class,
                        GeihouActiveSigningSecretAutoConfiguration.class,
                        GeihouAccessTokenIssuerAutoConfiguration.class));

        kmsBridgeIssuerRunner.run(context -> {
            assertThat(context).doesNotHaveBean(GeihouActiveKidProvider.class);
            assertThat(context).doesNotHaveBean(GeihouSigningSecretProvider.class);
            assertThat(context).doesNotHaveBean(GeihouActiveSigningSecretProvider.class);
            assertThat(context).doesNotHaveBean(GeihouAccessTokenIssuer.class);
        });

        kmsBridgeIssuerRunner
                .withPropertyValues("geihou.security.kms.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(GeihouActiveKidProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouSigningSecretProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouActiveSigningSecretProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouAccessTokenIssuer.class);
                });
    }

    @Test
    void shouldCreateIssuerAfterKmsBridgeProvidersExistWithoutCallingSecretClient() {
        GeihouKmsSecretClient secretClient = mock(GeihouKmsSecretClient.class);

        kmsBridgeIssuerRunner()
                .withBean(GeihouKmsSecretClient.class, () -> secretClient)
                .withPropertyValues(validEnabledProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouActiveKidProvider.class);
                    assertThat(context.getBean(GeihouActiveKidProvider.class))
                            .isInstanceOf(GeihouKmsActiveKidProvider.class);
                    assertThat(context).hasSingleBean(GeihouSigningSecretProvider.class);
                    assertThat(context.getBean(GeihouSigningSecretProvider.class))
                            .isInstanceOf(GeihouKmsSigningSecretProvider.class);
                    assertThat(context).hasSingleBean(GeihouActiveSigningSecretProvider.class);
                    assertThat(context.getBean(GeihouActiveSigningSecretProvider.class))
                            .isInstanceOf(GeihouResolvingActiveSigningSecretProvider.class);
                    assertThat(context).hasSingleBean(GeihouAccessTokenIssuer.class);
                    verifyNoInteractions(secretClient);
                });
    }

    @Test
    void shouldNotChangeVerifierChainCondition() {
        kmsBridgeIssuerAuthRunner()
                .withBean(AuthTokenRevokedRepository.class, () -> mock(AuthTokenRevokedRepository.class))
                .withBean(AuthUserRepository.class, () -> mock(AuthUserRepository.class))
                .withPropertyValues(validEnabledProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouSigningSecretProvider.class);
                    assertThat(context).hasSingleBean(GeihouActiveSigningSecretProvider.class);
                    assertThat(context).hasSingleBean(GeihouAccessTokenIssuer.class);
                    assertThat(context).hasSingleBean(AuthApi.class);
                    assertThat(context).hasSingleBean(TokenValidator.class);
                    assertThat(context).hasSingleBean(GeihouAuthTokenVerifier.class);
                });
    }

    @Test
    void shouldAllowVerifierChainWithoutIssuerWhenActiveSigningSecretProviderIsMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        GeihouAccessTokenIssuerAutoConfiguration.class,
                        GeihouAuthTokenAutoConfiguration.class))
                .withBean(GeihouSigningSecretProvider.class, () -> kid -> Optional.empty())
                .withBean(AuthTokenRevokedRepository.class, () -> mock(AuthTokenRevokedRepository.class))
                .withBean(AuthUserRepository.class, () -> mock(AuthUserRepository.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouSigningSecretProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouActiveSigningSecretProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouAccessTokenIssuer.class);
                    assertThat(context).hasSingleBean(AuthApi.class);
                    assertThat(context).hasSingleBean(TokenValidator.class);
                    assertThat(context).hasSingleBean(GeihouAuthTokenVerifier.class);
                });
    }

    @Test
    void autoConfigurationImportsShouldContainIssuerBetweenBridgeAndAuthAutoConfigurations() throws Exception {
        String path = "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream(path), StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));

            String kms = "com.geihou.module.system.framework.jwt.kms.GeihouKmsAutoConfiguration";
            String bridge = "com.geihou.module.system.framework.jwt.GeihouActiveSigningSecretAutoConfiguration";
            String issuer = "com.geihou.module.system.framework.jwt.GeihouAccessTokenIssuerAutoConfiguration";
            String auth = "com.geihou.module.system.framework.security.config.GeihouAuthTokenAutoConfiguration";
            assertThat(content).contains(kms).contains(bridge).contains(issuer).contains(auth);
            assertThat(content.indexOf(kms)).isLessThan(content.indexOf(bridge));
            assertThat(content.indexOf(bridge)).isLessThan(content.indexOf(issuer));
            assertThat(content.indexOf(issuer)).isLessThan(content.indexOf(auth));
        }
    }

    private ApplicationContextRunner kmsBridgeIssuerRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        GeihouKmsAutoConfiguration.class,
                        GeihouActiveSigningSecretAutoConfiguration.class,
                        GeihouAccessTokenIssuerAutoConfiguration.class))
                .withBean(Client.class, () -> mock(Client.class));
    }

    private ApplicationContextRunner kmsBridgeIssuerAuthRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        GeihouKmsAutoConfiguration.class,
                        GeihouActiveSigningSecretAutoConfiguration.class,
                        GeihouAccessTokenIssuerAutoConfiguration.class,
                        GeihouAuthTokenAutoConfiguration.class))
                .withBean(Client.class, () -> mock(Client.class));
    }

    private static String[] validEnabledProperties() {
        return new String[]{
                "geihou.security.kms.enabled=true",
                "geihou.security.kms.endpoint=kms-unit.local.invalid",
                "geihou.security.kms.client-key-file=/tmp/geihou-kms-client-key.json",
                "geihou.security.kms.client-key-password-env=GEIHOU_KMS_TEST_PASSWORD",
                "geihou.security.kms.ca-cert-file=/tmp/geihou-kms-ca.pem",
                "geihou.security.kms.secret-name-prefix=geihou/auth/jwt/",
                "geihou.security.kms.connect-timeout=2s",
                "geihou.security.kms.read-timeout=4s",
                "geihou.security.kms.cache-ttl=3m",
                "geihou.security.kms.stale-cache-ttl=0s"
        };
    }
}
