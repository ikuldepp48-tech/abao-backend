package com.geihou.module.system.framework.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.geihou.module.system.dal.mysql.auth.AuthPermissionRepository;
import com.geihou.module.system.dal.mysql.auth.AuthCaptchaChallengeRepository;
import com.geihou.module.system.dal.mysql.auth.AuthLoginAttemptRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRefreshTokenRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRolePermissionRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRoleRepository;
import com.geihou.module.system.dal.mysql.auth.AuthTwoFactorTempTokenRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRoleRepository;
import com.geihou.module.system.dal.mysql.tenant.TenantRepository;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenIssuer;
import com.geihou.module.system.framework.jwt.GeihouJwtTokenParser;
import com.geihou.module.system.service.auth.AesGcmTwoFactorSecretCryptoProvider;
import com.geihou.module.system.service.auth.GeihouAccessTokenIssueService;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService;
import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService;
import com.geihou.module.system.service.auth.GeihouAuthLoginAttemptRecorderAdapter;
import com.geihou.module.system.service.auth.GeihouAuthLoginTokenRefreshService;
import com.geihou.module.system.service.auth.GeihouAuthLoginTokenWiringService;
import com.geihou.module.system.service.auth.GeihouAuthRolePermissionReadService;
import com.geihou.module.system.service.auth.GeihouAuthUserLoginLookupAdapter;
import com.geihou.module.system.service.auth.GeihouAuthUserLoginStateAdapter;
import com.geihou.module.system.service.auth.GeihouBcryptPasswordVerifier;
import com.geihou.module.system.service.auth.GeihouCaptchaChallengeService;
import com.geihou.module.system.service.auth.GeihouRefreshTokenIssueService;
import com.geihou.module.system.service.auth.GeihouRefreshTokenPort;
import com.geihou.module.system.service.auth.GeihouTenantCodeResolverAdapter;
import com.geihou.module.system.service.auth.GeihouTotpVerifier;
import com.geihou.module.system.service.auth.GeihouTwoFactorAttemptRecorderAdapter;
import com.geihou.module.system.service.auth.GeihouTwoFactorTempTokenService;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.TwoFactorAttemptRecorderPort;
import com.geihou.module.system.service.auth.TwoFactorSecretCryptoProvider;
import com.geihou.module.system.service.auth.TwoFactorSecretKeyProvider;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GeihouAuthLoginTokenServiceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GeihouAuthLoginTokenServiceAutoConfiguration.class));

    @Test
    void shouldNotRegisterLoginTokenServicesWhenIssuerIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(GeihouAccessTokenIssueService.class);
            assertThat(context).doesNotHaveBean(GeihouAuthRolePermissionReadService.class);
            assertThat(context).doesNotHaveBean(GeihouAuthLoginTokenWiringService.class);
            assertThat(context).doesNotHaveBean(GeihouAuthLoginTokenRefreshService.class);
        });
    }

    @Test
    void shouldRegisterLoginTokenServiceGraphWhenDependenciesExist() {
        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(GeihouJwtTokenParser.class, GeihouJwtTokenParser::new)
                .withBean(AuthUserRepository.class, () -> mock(AuthUserRepository.class))
                .withBean(TenantRepository.class, () -> mock(TenantRepository.class))
                .withBean(AuthLoginAttemptRepository.class, () -> mock(AuthLoginAttemptRepository.class))
                .withBean(AuthUserRoleRepository.class, () -> mock(AuthUserRoleRepository.class))
                .withBean(AuthRoleRepository.class, () -> mock(AuthRoleRepository.class))
                .withBean(AuthRolePermissionRepository.class, () -> mock(AuthRolePermissionRepository.class))
                .withBean(AuthPermissionRepository.class, () -> mock(AuthPermissionRepository.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouAccessTokenIssueService.class);
                    assertThat(context).hasSingleBean(GeihouAuthRolePermissionReadService.class);
                    assertThat(context).hasSingleBean(GeihouAuthLoginTokenWiringService.class);
                    assertThat(context).hasSingleBean(GeihouAuthLoginTokenRefreshService.class);
                    assertThat(context).hasSingleBean(GeihouAdminLoginAuthenticationService.UserLookupPort.class);
                    assertThat(context.getBean(GeihouAdminLoginAuthenticationService.UserLookupPort.class))
                            .isInstanceOf(GeihouAuthUserLoginLookupAdapter.class);
                    assertThat(context).hasSingleBean(GeihouAdminLoginAuthenticationService.PasswordVerifierPort.class);
                    assertThat(context.getBean(GeihouAdminLoginAuthenticationService.PasswordVerifierPort.class))
                            .isInstanceOf(GeihouBcryptPasswordVerifier.class);
                    assertThat(context).hasSingleBean(GeihouAdminLoginAuthenticationService.TenantCodeResolverPort.class);
                    assertThat(context.getBean(GeihouAdminLoginAuthenticationService.TenantCodeResolverPort.class))
                            .isInstanceOf(GeihouTenantCodeResolverAdapter.class);
                    assertThat(context).hasSingleBean(GeihouAdminLoginAuthenticationService.LoginAttemptRecorderPort.class);
                    assertThat(context.getBean(GeihouAdminLoginAuthenticationService.LoginAttemptRecorderPort.class))
                            .isInstanceOf(GeihouAuthLoginAttemptRecorderAdapter.class);
                    assertThat(context).hasSingleBean(TwoFactorAttemptRecorderPort.class);
                    assertThat(context.getBean(TwoFactorAttemptRecorderPort.class))
                            .isInstanceOf(GeihouTwoFactorAttemptRecorderAdapter.class);
                    assertThat(context).hasSingleBean(GeihouAdminLoginAuthenticationService.LoginStatePort.class);
                    assertThat(context.getBean(GeihouAdminLoginAuthenticationService.LoginStatePort.class))
                            .isInstanceOf(GeihouAuthUserLoginStateAdapter.class);
                    assertThat(context).doesNotHaveBean(GeihouAdminLoginAuthenticationService.CaptchaPort.class);
                    assertThat(context).doesNotHaveBean(
                            GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class);
                    assertThat(context).doesNotHaveBean(GeihouRefreshTokenPort.class);
                    assertThat(context).doesNotHaveBean(GeihouAdminLoginOrchestratorService.class);
                    assertThat(context).doesNotHaveBean(GeihouAdminLoginAuthenticationService.class);
                });
    }

    @Test
    void shouldRegisterAdminLoginOrchestratorOnlyWhenAllDependenciesExist() {
        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(GeihouAdminLoginAuthenticationService.class,
                        () -> mock(GeihouAdminLoginAuthenticationService.class))
                .withBean(GeihouAuthLoginTokenWiringService.class,
                        () -> mock(GeihouAuthLoginTokenWiringService.class))
                .withBean(GeihouRefreshTokenPort.class, () -> mock(GeihouRefreshTokenPort.class))
                .withBean(GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class,
                        () -> mock(GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class))
                .run(context -> assertThat(context).hasSingleBean(GeihouAdminLoginOrchestratorService.class));
    }

    @Test
    void shouldNotRegisterAdminLoginOrchestratorWhenTwoFactorTempTokenPortIsMissing() {
        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(GeihouAdminLoginAuthenticationService.class,
                        () -> mock(GeihouAdminLoginAuthenticationService.class))
                .withBean(GeihouAuthLoginTokenWiringService.class,
                        () -> mock(GeihouAuthLoginTokenWiringService.class))
                .withBean(GeihouRefreshTokenPort.class, () -> mock(GeihouRefreshTokenPort.class))
                .run(context -> assertThat(context).doesNotHaveBean(GeihouAdminLoginOrchestratorService.class));
    }

    @Test
    void shouldBackOffForUserProvidedAdminLoginOrchestrator() {
        GeihouAdminLoginOrchestratorService orchestrator = mock(GeihouAdminLoginOrchestratorService.class);

        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(GeihouAdminLoginAuthenticationService.class,
                        () -> mock(GeihouAdminLoginAuthenticationService.class))
                .withBean(GeihouAuthLoginTokenWiringService.class,
                        () -> mock(GeihouAuthLoginTokenWiringService.class))
                .withBean(GeihouRefreshTokenPort.class, () -> mock(GeihouRefreshTokenPort.class))
                .withBean(GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class,
                        () -> mock(GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class))
                .withBean(GeihouAdminLoginOrchestratorService.class, () -> orchestrator)
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouAdminLoginOrchestratorService.class);
                    assertThat(context.getBean(GeihouAdminLoginOrchestratorService.class)).isSameAs(orchestrator);
                });
    }

    @Test
    void shouldRegisterRefreshTokenPortWhenRepositoryExists() {
        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(AuthRefreshTokenRepository.class, () -> mock(AuthRefreshTokenRepository.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouRefreshTokenPort.class);
                    assertThat(context.getBean(GeihouRefreshTokenPort.class))
                            .isInstanceOf(GeihouRefreshTokenIssueService.class);
                });
    }

    @Test
    void shouldRegisterTwoFactorTempTokenPortWhenRepositoryExists() {
        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(AuthTwoFactorTempTokenRepository.class,
                        () -> mock(AuthTwoFactorTempTokenRepository.class))
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class);
                    assertThat(context.getBean(GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class))
                            .isInstanceOf(GeihouTwoFactorTempTokenService.class);
                });
    }

    @Test
    void shouldRegisterTwoFactorVerifyServiceGraphWhenDependenciesExist() {
        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(AuthUserRepository.class, () -> mock(AuthUserRepository.class))
                .withBean(AuthLoginAttemptRepository.class, () -> mock(AuthLoginAttemptRepository.class))
                .withBean(GeihouAuthLoginTokenWiringService.class,
                        () -> mock(GeihouAuthLoginTokenWiringService.class))
                .withBean(GeihouRefreshTokenPort.class, () -> mock(GeihouRefreshTokenPort.class))
                .withBean(GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class,
                        () -> mock(GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class))
                .withBean(TwoFactorSecretKeyProvider.class,
                        () -> () -> "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8))
                .run(context -> {
                    assertThat(context).hasSingleBean(TwoFactorSecretCryptoProvider.class);
                    assertThat(context.getBean(TwoFactorSecretCryptoProvider.class))
                            .isInstanceOf(AesGcmTwoFactorSecretCryptoProvider.class);
                    assertThat(context).hasSingleBean(GeihouTotpVerifier.class);
                    assertThat(context).hasSingleBean(TwoFactorAttemptRecorderPort.class);
                    assertThat(context).hasSingleBean(GeihouTwoFactorVerifyOrchestrationService.class);
                });
    }

    @Test
    void shouldRegisterTwoFactorAttemptRecorderPortWhenLoginAttemptRepositoryExists() {
        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(AuthLoginAttemptRepository.class, () -> mock(AuthLoginAttemptRepository.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(TwoFactorAttemptRecorderPort.class);
                    assertThat(context.getBean(TwoFactorAttemptRecorderPort.class))
                            .isInstanceOf(GeihouTwoFactorAttemptRecorderAdapter.class);
                });
    }

    @Test
    void shouldNotRegisterTwoFactorVerifyServiceGraphWhenSecretKeyProviderIsMissing() {
        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(AuthUserRepository.class, () -> mock(AuthUserRepository.class))
                .withBean(AuthLoginAttemptRepository.class, () -> mock(AuthLoginAttemptRepository.class))
                .withBean(GeihouAuthLoginTokenWiringService.class,
                        () -> mock(GeihouAuthLoginTokenWiringService.class))
                .withBean(GeihouRefreshTokenPort.class, () -> mock(GeihouRefreshTokenPort.class))
                .withBean(GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class,
                        () -> mock(GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TwoFactorSecretCryptoProvider.class);
                    assertThat(context).doesNotHaveBean(GeihouTotpVerifier.class);
                    assertThat(context).doesNotHaveBean(GeihouTwoFactorVerifyOrchestrationService.class);
                });
    }

    @Test
    void shouldBackOffForUserProvidedTwoFactorSecretCryptoProvider() {
        TwoFactorSecretCryptoProvider cryptoProvider = mock(TwoFactorSecretCryptoProvider.class);

        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(TwoFactorSecretKeyProvider.class,
                        () -> () -> "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8))
                .withBean(TwoFactorSecretCryptoProvider.class, () -> cryptoProvider)
                .run(context -> {
                    assertThat(context).hasSingleBean(TwoFactorSecretCryptoProvider.class);
                    assertThat(context.getBean(TwoFactorSecretCryptoProvider.class)).isSameAs(cryptoProvider);
                    assertThat(context).hasSingleBean(GeihouTotpVerifier.class);
                });
    }

    @Test
    void shouldBackOffForUserProvidedTotpVerifier() {
        GeihouTotpVerifier totpVerifier = mock(GeihouTotpVerifier.class);

        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(TwoFactorSecretKeyProvider.class,
                        () -> () -> "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8))
                .withBean(GeihouTotpVerifier.class, () -> totpVerifier)
                .run(context -> {
                    assertThat(context).hasSingleBean(TwoFactorSecretCryptoProvider.class);
                    assertThat(context).hasSingleBean(GeihouTotpVerifier.class);
                    assertThat(context.getBean(GeihouTotpVerifier.class)).isSameAs(totpVerifier);
                });
    }

    @Test
    void shouldBackOffForUserProvidedTwoFactorVerifyService() {
        GeihouTwoFactorVerifyOrchestrationService verifyService =
                mock(GeihouTwoFactorVerifyOrchestrationService.class);

        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(AuthUserRepository.class, () -> mock(AuthUserRepository.class))
                .withBean(GeihouAuthLoginTokenWiringService.class,
                        () -> mock(GeihouAuthLoginTokenWiringService.class))
                .withBean(GeihouRefreshTokenPort.class, () -> mock(GeihouRefreshTokenPort.class))
                .withBean(TwoFactorAttemptRecorderPort.class, () -> mock(TwoFactorAttemptRecorderPort.class))
                .withBean(GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class,
                        () -> mock(GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class))
                .withBean(TwoFactorSecretKeyProvider.class,
                        () -> () -> "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8))
                .withBean(GeihouTwoFactorVerifyOrchestrationService.class, () -> verifyService)
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouTwoFactorVerifyOrchestrationService.class);
                    assertThat(context.getBean(GeihouTwoFactorVerifyOrchestrationService.class))
                            .isSameAs(verifyService);
                });
    }

    @Test
    void shouldRegisterCaptchaPortAndAuthenticationServiceWhenRealCaptchaRepositoryExists() {
        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(AuthUserRepository.class, () -> mock(AuthUserRepository.class))
                .withBean(TenantRepository.class, () -> mock(TenantRepository.class))
                .withBean(AuthLoginAttemptRepository.class, () -> mock(AuthLoginAttemptRepository.class))
                .withBean(AuthCaptchaChallengeRepository.class, () -> mock(AuthCaptchaChallengeRepository.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouAdminLoginAuthenticationService.CaptchaPort.class);
                    assertThat(context.getBean(GeihouAdminLoginAuthenticationService.CaptchaPort.class))
                            .isInstanceOf(GeihouCaptchaChallengeService.class);
                    assertThat(context).hasSingleBean(GeihouAdminLoginAuthenticationService.class);
                });
    }

    @Test
    void shouldRegisterAdminLoginAuthenticationServiceOnlyWhenAllPortsExist() {
        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(AuthUserRepository.class, () -> mock(AuthUserRepository.class))
                .withBean(GeihouAdminLoginAuthenticationService.CaptchaPort.class,
                        () -> mock(GeihouAdminLoginAuthenticationService.CaptchaPort.class))
                .withBean(GeihouAdminLoginAuthenticationService.TenantCodeResolverPort.class,
                        () -> mock(GeihouAdminLoginAuthenticationService.TenantCodeResolverPort.class))
                .withBean(GeihouAdminLoginAuthenticationService.LoginAttemptRecorderPort.class,
                        () -> mock(GeihouAdminLoginAuthenticationService.LoginAttemptRecorderPort.class))
                .run(context -> assertThat(context).hasSingleBean(GeihouAdminLoginAuthenticationService.class));
    }

    @Test
    void shouldBackOffForUserProvidedAdminLoginAuthenticationService() {
        GeihouAdminLoginAuthenticationService authenticationService =
                mock(GeihouAdminLoginAuthenticationService.class);

        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(AuthUserRepository.class, () -> mock(AuthUserRepository.class))
                .withBean(GeihouAdminLoginAuthenticationService.CaptchaPort.class,
                        () -> mock(GeihouAdminLoginAuthenticationService.CaptchaPort.class))
                .withBean(GeihouAdminLoginAuthenticationService.TenantCodeResolverPort.class,
                        () -> mock(GeihouAdminLoginAuthenticationService.TenantCodeResolverPort.class))
                .withBean(GeihouAdminLoginAuthenticationService.LoginAttemptRecorderPort.class,
                        () -> mock(GeihouAdminLoginAuthenticationService.LoginAttemptRecorderPort.class))
                .withBean(GeihouAdminLoginAuthenticationService.class, () -> authenticationService)
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouAdminLoginAuthenticationService.class);
                    assertThat(context.getBean(GeihouAdminLoginAuthenticationService.class))
                            .isSameAs(authenticationService);
                });
    }

    @Test
    void shouldBackOffForUserProvidedServices() {
        GeihouAccessTokenIssueService issueService = mock(GeihouAccessTokenIssueService.class);
        GeihouAuthRolePermissionReadService readService = mock(GeihouAuthRolePermissionReadService.class);
        GeihouAuthLoginTokenWiringService wiringService = mock(GeihouAuthLoginTokenWiringService.class);
        GeihouAuthLoginTokenRefreshService refreshService = mock(GeihouAuthLoginTokenRefreshService.class);

        contextRunner
                .withBean(GeihouAccessTokenIssuer.class, () -> mock(GeihouAccessTokenIssuer.class))
                .withBean(GeihouJwtTokenParser.class, GeihouJwtTokenParser::new)
                .withBean(GeihouAccessTokenIssueService.class, () -> issueService)
                .withBean(GeihouAuthRolePermissionReadService.class, () -> readService)
                .withBean(GeihouAuthLoginTokenWiringService.class, () -> wiringService)
                .withBean(GeihouAuthLoginTokenRefreshService.class, () -> refreshService)
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouAccessTokenIssueService.class);
                    assertThat(context.getBean(GeihouAccessTokenIssueService.class)).isSameAs(issueService);
                    assertThat(context).hasSingleBean(GeihouAuthRolePermissionReadService.class);
                    assertThat(context.getBean(GeihouAuthRolePermissionReadService.class)).isSameAs(readService);
                    assertThat(context).hasSingleBean(GeihouAuthLoginTokenWiringService.class);
                    assertThat(context.getBean(GeihouAuthLoginTokenWiringService.class)).isSameAs(wiringService);
                    assertThat(context).hasSingleBean(GeihouAuthLoginTokenRefreshService.class);
                    assertThat(context.getBean(GeihouAuthLoginTokenRefreshService.class)).isSameAs(refreshService);
                });
    }

    @Test
    void autoConfigurationImportsShouldContainLoginTokenServiceAutoConfigurationExactlyOnce() throws Exception {
        String path = "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream(path), StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            String importLine = "com.geihou.module.system.framework.security.config."
                    + "GeihouAuthLoginTokenServiceAutoConfiguration";

            assertThat(content).contains(importLine);
            assertThat(content.lines().filter(importLine::equals).count()).isEqualTo(1L);
        }
    }
}
