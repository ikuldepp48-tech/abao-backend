package com.geihou.module.system.framework.jwt.kms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aliyun.dkms.gcs.openapi.models.Config;
import com.aliyun.dkms.gcs.sdk.Client;
import com.geihou.framework.security.core.spi.TokenValidator;
import com.geihou.module.system.api.auth.AuthApi;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenIssuer;
import com.geihou.module.system.framework.jwt.GeihouActiveKidProvider;
import com.geihou.module.system.framework.jwt.GeihouActiveSigningSecretProvider;
import com.geihou.module.system.framework.jwt.GeihouResolvingActiveSigningSecretProvider;
import com.geihou.module.system.framework.jwt.GeihouSigningSecretProvider;
import com.geihou.module.system.dal.mysql.auth.AuthTokenRevokedRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import com.geihou.module.system.framework.security.config.GeihouAuthTokenAutoConfiguration;
import com.geihou.module.system.service.auth.GeihouAuthTokenVerifier;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GeihouKmsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GeihouKmsAutoConfiguration.class));

    @Test
    void shouldNotCreateKmsPropertiesWhenKmsIsMissingOrDisabled() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(GeihouKmsSpringProperties.class);
            assertThat(context).doesNotHaveBean(GeihouKmsProperties.class);
            assertThat(context).doesNotHaveBean(GeihouKmsClientKeyPasswordResolver.class);
            assertThat(context).doesNotHaveBean(GeihouKmsSecretNameMapper.class);
            assertThat(context).doesNotHaveBean(GeihouKmsSigningSecretDecoder.class);
            assertThat(context).doesNotHaveBean(AliyunDkmsSdkClientFactory.class);
            assertThat(context).doesNotHaveBean(Config.class);
            assertThat(context).doesNotHaveBean(Client.class);
            assertThat(context).doesNotHaveBean(GeihouSigningSecretProvider.class);
            assertThat(context).doesNotHaveBean(GeihouKmsSigningSecretProvider.class);
            assertThat(context).doesNotHaveBean(GeihouActiveKidProvider.class);
            assertThat(context).doesNotHaveBean(GeihouKmsActiveKidProvider.class);
        });

        contextRunner
                .withPropertyValues("geihou.security.kms.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(GeihouKmsSpringProperties.class);
                    assertThat(context).doesNotHaveBean(GeihouKmsProperties.class);
                    assertThat(context).doesNotHaveBean(GeihouKmsClientKeyPasswordResolver.class);
                    assertThat(context).doesNotHaveBean(GeihouKmsSecretNameMapper.class);
                    assertThat(context).doesNotHaveBean(GeihouKmsSigningSecretDecoder.class);
                    assertThat(context).doesNotHaveBean(AliyunDkmsSdkClientFactory.class);
                    assertThat(context).doesNotHaveBean(Config.class);
                    assertThat(context).doesNotHaveBean(Client.class);
                    assertThat(context).doesNotHaveBean(GeihouSigningSecretProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouKmsSigningSecretProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouActiveKidProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouKmsActiveKidProvider.class);
                });
    }

    @Test
    void shouldNotOverwriteCustomGeihouActiveKidProvider() {
        GeihouActiveKidProvider customKidProvider = () -> java.util.Optional.of("custom-kid");

        contextRunner
                .withBean(GeihouActiveKidProvider.class, () -> customKidProvider)
                .withBean(GeihouKmsClientKeyPasswordResolver.class,
                        () -> new GeihouKmsClientKeyPasswordResolver(name -> "runtime-password"))
                .withBean(AliyunDkmsSdkClientFactory.class,
                        () -> new AliyunDkmsSdkClientFactory(config -> mock(Client.class)))
                .withPropertyValues(validEnabledProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouActiveKidProvider.class);
                    assertThat(context.getBean(GeihouActiveKidProvider.class))
                            .isSameAs(customKidProvider);
                    assertThat(context).doesNotHaveBean(GeihouKmsActiveKidProvider.class);
                });
    }

    @Test
    void shouldNotCreateBridgeOrIssuerBeansWhenActiveKidProviderIsRegistered() {
        contextRunner
                .withBean(GeihouKmsClientKeyPasswordResolver.class,
                        () -> new GeihouKmsClientKeyPasswordResolver(name -> "runtime-password"))
                .withBean(AliyunDkmsSdkClientFactory.class,
                        () -> new AliyunDkmsSdkClientFactory(config -> mock(Client.class)))
                .withPropertyValues(validEnabledProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouActiveKidProvider.class);
                    assertThat(context.getBean(GeihouActiveKidProvider.class))
                            .isInstanceOf(GeihouKmsActiveKidProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouActiveSigningSecretProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouResolvingActiveSigningSecretProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouAccessTokenIssuer.class);
                });
    }

    @Test
    void shouldNotCallSecretClientDuringActiveKidBeanConstruction() {
        GeihouKmsSecretClient secretClient = mock(GeihouKmsSecretClient.class);

        contextRunner
                .withBean(GeihouKmsClientKeyPasswordResolver.class,
                        () -> new GeihouKmsClientKeyPasswordResolver(name -> "runtime-password"))
                .withBean(AliyunDkmsSdkClientFactory.class,
                        () -> new AliyunDkmsSdkClientFactory(config -> mock(Client.class)))
                .withBean(GeihouKmsSecretClient.class, () -> secretClient)
                .withPropertyValues(validEnabledProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouActiveKidProvider.class);
                    assertThat(context.getBean(GeihouActiveKidProvider.class))
                            .isInstanceOf(GeihouKmsActiveKidProvider.class);
                    verifyNoInteractions(secretClient);
                });
    }

    @Test
    void shouldCreateSdkClientWithLocalConfigWhenEnabledAndTestDependenciesProvided() {
        AtomicReference<Config> capturedConfig = new AtomicReference<>();
        Client sdkClient = mock(Client.class);

        contextRunner
                .withBean(GeihouKmsClientKeyPasswordResolver.class,
                        () -> new GeihouKmsClientKeyPasswordResolver(name -> "runtime-password"))
                .withBean(AliyunDkmsSdkClientFactory.class,
                        () -> new AliyunDkmsSdkClientFactory(config -> {
                            capturedConfig.set(config);
                            return sdkClient;
                        }))
                .withPropertyValues(validEnabledProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouKmsSpringProperties.class);
                    assertThat(context).hasSingleBean(GeihouKmsProperties.class);
                    assertThat(context).hasSingleBean(GeihouKmsClientKeyPasswordResolver.class);
                    assertThat(context).hasSingleBean(GeihouKmsSecretNameMapper.class);
                    assertThat(context).hasSingleBean(GeihouKmsSigningSecretDecoder.class);
                    assertThat(context).hasSingleBean(AliyunDkmsSdkClientFactory.class);
                    assertThat(context).hasSingleBean(Client.class);
                    assertThat(context.getBean(Client.class)).isSameAs(sdkClient);
                    GeihouKmsProperties properties = context.getBean(GeihouKmsProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.endpoint()).isEqualTo("kms-unit.local.invalid");
                    assertThat(properties.clientKeyFile()).isEqualTo("/tmp/geihou-kms-client-key.json");
                    assertThat(properties.clientKeyPasswordEnv()).isEqualTo("GEIHOU_KMS_TEST_PASSWORD");
                    assertThat(properties.caCertFile()).isEqualTo("/tmp/geihou-kms-ca.pem");
                    assertThat(properties.secretNamePrefix()).isEqualTo("geihou/auth/jwt/");
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(4));
                    assertThat(properties.cacheTtl()).isEqualTo(Duration.ofMinutes(3));
                    assertThat(properties.staleCacheTtl()).isEqualTo(Duration.ZERO);
                    assertThat(context.getBean(GeihouKmsSecretNameMapper.class).secretNamePrefix())
                            .isEqualTo("geihou/auth/jwt/");

                    Config config = capturedConfig.get();
                    assertThat(config).isNotNull();
                    assertThat(config.getProtocol()).isEqualTo("https");
                    assertThat(config.getEndpoint()).isEqualTo("kms-unit.local.invalid");
                    assertThat(config.getClientKeyFile()).isEqualTo("/tmp/geihou-kms-client-key.json");
                    assertThat(config.getPassword()).isEqualTo("runtime-password");
                    assertThat(config.getCaFilePath()).isEqualTo("/tmp/geihou-kms-ca.pem");
                    assertThat(config.getConnectTimeout()).isEqualTo(2_000L);
                    assertThat(config.getReadTimeout()).isEqualTo(4_000L);
                    assertThat(config.getIgnoreSSL()).isFalse();
                    assertThat(config.getUserAgent()).isEqualTo("geihou-auth-kms/h111");

                    assertThat(context).hasSingleBean(GeihouSigningSecretProvider.class);
                    assertThat(context).hasSingleBean(GeihouKmsSigningSecretProvider.class);
                    assertThat(context.getBean(GeihouSigningSecretProvider.class))
                            .isSameAs(context.getBean(GeihouKmsSigningSecretProvider.class));
                    assertThat(context).hasSingleBean(GeihouKmsSecretClient.class);
                    assertThat(context.getBean(GeihouKmsSecretClient.class))
                            .isInstanceOf(AliyunDkmsSecretClientAdapter.class);

                    assertThat(context).hasSingleBean(GeihouActiveKidProvider.class);
                    assertThat(context.getBean(GeihouActiveKidProvider.class))
                            .isInstanceOf(GeihouKmsActiveKidProvider.class);

                    assertThat(context).doesNotHaveBean(GeihouActiveSigningSecretProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouResolvingActiveSigningSecretProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouAccessTokenIssuer.class);

                    verifyNoInteractions(sdkClient);
                    assertThat(context).doesNotHaveBean(Config.class);
                });
    }

    @Test
    void shouldFailStartupWhenEnabledPropertiesAreInvalid() {
        contextRunner
                .withPropertyValues(validEnabledPropertiesWith(
                        "geihou.security.kms.client-key-file=client-key.json"))
                .run(context -> assertThat(context.getStartupFailure())
                        .isInstanceOf(org.springframework.beans.factory.BeanCreationException.class)
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("client-key-file"));
    }

    @Test
    void shouldFailStartupWithRedactedMessageWhenPasswordResolverFails() {
        contextRunner
                .withBean(GeihouKmsClientKeyPasswordResolver.class,
                        () -> new GeihouKmsClientKeyPasswordResolver(name -> {
                            throw new IllegalStateException("password=raw-secret endpoint=kms-unit.local.invalid");
                        }))
                .withBean(AliyunDkmsSdkClientFactory.class,
                        () -> new AliyunDkmsSdkClientFactory(config -> mock(Client.class)))
                .withPropertyValues(validEnabledProperties())
                .run(context -> assertThat(context.getStartupFailure())
                        .isInstanceOf(org.springframework.beans.factory.BeanCreationException.class)
                        .hasRootCauseInstanceOf(GeihouKmsCredentialException.class)
                        .hasMessageContaining("KMS ClientKey password resolution failed")
                        .hasMessageNotContaining("raw-secret"));
    }

    @Test
    void shouldCreateProviderWithoutCallingCustomSecretClient() {
        GeihouKmsSecretClient secretClient = mock(GeihouKmsSecretClient.class);

        contextRunner
                .withBean(GeihouKmsClientKeyPasswordResolver.class,
                        () -> new GeihouKmsClientKeyPasswordResolver(name -> "runtime-password"))
                .withBean(AliyunDkmsSdkClientFactory.class,
                        () -> new AliyunDkmsSdkClientFactory(config -> mock(Client.class)))
                .withBean(GeihouKmsSecretClient.class, () -> secretClient)
                .withPropertyValues(validEnabledProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouSigningSecretProvider.class);
                    assertThat(context).hasSingleBean(GeihouKmsSigningSecretProvider.class);
                    assertThat(context.getBean(GeihouSigningSecretProvider.class))
                            .isSameAs(context.getBean(GeihouKmsSigningSecretProvider.class));
                    assertThat(context).hasSingleBean(GeihouActiveKidProvider.class);
                    assertThat(context.getBean(GeihouActiveKidProvider.class))
                            .isInstanceOf(GeihouKmsActiveKidProvider.class);
                    verifyNoInteractions(secretClient);
                });
    }

    @Test
    void shouldActivateAuthChainWhenKmsProviderAndRepositoriesAreAvailable() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        GeihouKmsAutoConfiguration.class,
                        GeihouAuthTokenAutoConfiguration.class))
                .withBean(GeihouKmsClientKeyPasswordResolver.class,
                        () -> new GeihouKmsClientKeyPasswordResolver(name -> "runtime-password"))
                .withBean(AliyunDkmsSdkClientFactory.class,
                        () -> new AliyunDkmsSdkClientFactory(config -> mock(Client.class)))
                .withBean(AuthTokenRevokedRepository.class, () -> mock(AuthTokenRevokedRepository.class))
                .withBean(AuthUserRepository.class, () -> mock(AuthUserRepository.class))
                .withPropertyValues(validEnabledProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouKmsProperties.class);
                    assertThat(context).hasSingleBean(Client.class);
                    assertThat(context).hasSingleBean(GeihouKmsSecretClient.class);
                    assertThat(context).doesNotHaveBean(Config.class);
                    assertThat(context).hasSingleBean(GeihouSigningSecretProvider.class);
                    assertThat(context).hasSingleBean(GeihouKmsSigningSecretProvider.class);
                    assertThat(context).hasSingleBean(GeihouActiveKidProvider.class);
                    assertThat(context.getBean(GeihouActiveKidProvider.class))
                            .isInstanceOf(GeihouKmsActiveKidProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouActiveSigningSecretProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouResolvingActiveSigningSecretProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouAccessTokenIssuer.class);
                    assertThat(context).hasSingleBean(AuthApi.class);
                    assertThat(context).hasSingleBean(TokenValidator.class);
                    assertThat(context).hasSingleBean(GeihouAuthTokenVerifier.class);
                });
    }

    @Test
    void autoConfigurationImportsShouldContainKmsAndAuthAutoConfigurations() throws Exception {
        String path = "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream(path), StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));

            assertThat(content)
                    .contains("com.geihou.module.system.framework.jwt.kms.GeihouKmsAutoConfiguration")
                    .contains("com.geihou.module.system.framework.security.config.GeihouAuthTokenAutoConfiguration");
        }
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

    private static String[] validEnabledPropertiesWith(String override) {
        String[] properties = validEnabledProperties();
        String overrideKey = override.substring(0, override.indexOf('=') + 1);
        for (int i = 0; i < properties.length; i++) {
            if (properties[i].startsWith(overrideKey)) {
                properties[i] = override;
                return properties;
            }
        }
        return properties;
    }
}
