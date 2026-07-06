package com.geihou.module.system.framework.security.config;

import com.geihou.framework.security.config.GeihouSecurityAutoConfiguration;
import com.geihou.framework.security.core.spi.PermissionChecker;
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
import com.geihou.module.system.service.auth.GeihouAuthUserLoginLookupAdapter;
import com.geihou.module.system.service.auth.GeihouAuthUserLoginStateAdapter;
import com.geihou.module.system.service.auth.GeihouAuthLoginTokenRefreshService;
import com.geihou.module.system.service.auth.GeihouAuthLoginTokenWiringService;
import com.geihou.module.system.service.auth.GeihouAuthRolePermissionReadService;
import com.geihou.module.system.service.auth.GeihouBcryptPasswordVerifier;
import com.geihou.module.system.service.auth.GeihouCaptchaChallengeService;
import com.geihou.module.system.service.auth.GeihouRbacPermissionChecker;
import com.geihou.module.system.service.auth.GeihouRefreshTokenIssueService;
import com.geihou.module.system.service.auth.GeihouRefreshTokenPort;
import com.geihou.module.system.service.auth.GeihouTenantCodeResolverAdapter;
import com.geihou.module.system.service.auth.GeihouTotpVerifier;
import com.geihou.module.system.service.auth.GeihouTwoFactorAttemptRecorderAdapter;
import com.geihou.module.system.service.auth.GeihouTwoFactorTempTokenService;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService;
import com.geihou.module.system.service.auth.TwoFactorSecretCryptoProvider;
import com.geihou.module.system.service.auth.TwoFactorSecretKeyProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Conditional wiring for the Geihou login/refresh token service-layer bean graph.
 *
 * <p>H156: registers the first Spring bean graph for login/refresh token
 * service-layer consumers without adding public endpoints. Runs after
 * {@link GeihouAuthTokenAutoConfiguration} so the JWT issuer/parser chain is
 * available.
 *
 * <p>Registered beans (all {@code @ConditionalOnMissingBean}):
 * <ul>
 *   <li>{@link GeihouAccessTokenIssueService} — requires
 *       {@link GeihouAccessTokenIssuer}.</li>
 *   <li>{@link GeihouAuthRolePermissionReadService} — requires the four
 *       auth RBAC repositories.</li>
 *   <li>{@link GeihouAuthLoginTokenWiringService} — requires the read
 *       service and issue service.</li>
 *   <li>{@link GeihouAuthLoginTokenRefreshService} — requires the read
 *       service, issue service, and {@link GeihouJwtTokenParser}.</li>
 * </ul>
 *
 * <p>Does not add {@code @Service} to existing H152/H154 plain service
 * classes. Does not register H148 provisioning service or H150 assignment
 * service. Does not implement public login/refresh/logout endpoints,
 * {@code /auth/me}, B-to-A, no-literal, consultant finance conjunction,
 * token revocation, audit, or step-up verification mechanism.
 */
@AutoConfiguration(after = GeihouAuthTokenAutoConfiguration.class, before = GeihouSecurityAutoConfiguration.class)
@ConditionalOnBean(GeihouAccessTokenIssuer.class)
public class GeihouAuthLoginTokenServiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GeihouAccessTokenIssueService.class)
    public GeihouAccessTokenIssueService geihouAccessTokenIssueService(GeihouAccessTokenIssuer accessTokenIssuer) {
        return new GeihouAccessTokenIssueService(accessTokenIssuer);
    }

    @Bean
    @ConditionalOnBean({
            AuthUserRoleRepository.class,
            AuthRoleRepository.class,
            AuthRolePermissionRepository.class,
            AuthPermissionRepository.class
    })
    @ConditionalOnMissingBean(GeihouAuthRolePermissionReadService.class)
    public GeihouAuthRolePermissionReadService geihouAuthRolePermissionReadService(
            AuthUserRoleRepository userRoleRepository,
            AuthRoleRepository roleRepository,
            AuthRolePermissionRepository rolePermissionRepository,
            AuthPermissionRepository permissionRepository) {
        return new GeihouAuthRolePermissionReadService(
                userRoleRepository, roleRepository, rolePermissionRepository, permissionRepository);
    }

    @Bean
    @ConditionalOnBean(GeihouAuthRolePermissionReadService.class)
    @ConditionalOnMissingBean(PermissionChecker.class)
    public PermissionChecker geihouRbacPermissionChecker(
            GeihouAuthRolePermissionReadService rolePermissionReadService) {
        return new GeihouRbacPermissionChecker(rolePermissionReadService);
    }

    @Bean
    @ConditionalOnBean({GeihouAuthRolePermissionReadService.class, GeihouAccessTokenIssueService.class})
    @ConditionalOnMissingBean(GeihouAuthLoginTokenWiringService.class)
    public GeihouAuthLoginTokenWiringService geihouAuthLoginTokenWiringService(
            GeihouAuthRolePermissionReadService rolePermissionReadService,
            GeihouAccessTokenIssueService accessTokenIssueService) {
        return new GeihouAuthLoginTokenWiringService(rolePermissionReadService, accessTokenIssueService);
    }

    @Bean
    @ConditionalOnBean({
            GeihouAuthRolePermissionReadService.class,
            GeihouAccessTokenIssueService.class,
            GeihouJwtTokenParser.class
    })
    @ConditionalOnMissingBean(GeihouAuthLoginTokenRefreshService.class)
    public GeihouAuthLoginTokenRefreshService geihouAuthLoginTokenRefreshService(
            GeihouAuthRolePermissionReadService rolePermissionReadService,
            GeihouAccessTokenIssueService accessTokenIssueService,
            GeihouJwtTokenParser jwtTokenParser) {
        return new GeihouAuthLoginTokenRefreshService(
                rolePermissionReadService, accessTokenIssueService, jwtTokenParser);
    }

    @Bean
    @ConditionalOnBean(AuthUserRepository.class)
    @ConditionalOnMissingBean(GeihouAdminLoginAuthenticationService.UserLookupPort.class)
    public GeihouAdminLoginAuthenticationService.UserLookupPort geihouAdminLoginUserLookupPort(
            AuthUserRepository authUserRepository) {
        return new GeihouAuthUserLoginLookupAdapter(authUserRepository);
    }

    @Bean
    @ConditionalOnBean(AuthUserRepository.class)
    @ConditionalOnMissingBean(GeihouAdminLoginAuthenticationService.LoginStatePort.class)
    public GeihouAdminLoginAuthenticationService.LoginStatePort geihouAdminLoginStatePort(
            AuthUserRepository authUserRepository) {
        return new GeihouAuthUserLoginStateAdapter(authUserRepository);
    }

    @Bean
    @ConditionalOnMissingBean(GeihouAdminLoginAuthenticationService.PasswordVerifierPort.class)
    public GeihouAdminLoginAuthenticationService.PasswordVerifierPort geihouAdminLoginPasswordVerifierPort() {
        return new GeihouBcryptPasswordVerifier();
    }

    @Bean
    @ConditionalOnBean(TenantRepository.class)
    @ConditionalOnMissingBean(GeihouAdminLoginAuthenticationService.TenantCodeResolverPort.class)
    public GeihouAdminLoginAuthenticationService.TenantCodeResolverPort geihouTenantCodeResolverPort(
            TenantRepository tenantRepository) {
        return new GeihouTenantCodeResolverAdapter(tenantRepository);
    }

    @Bean
    @ConditionalOnBean(AuthLoginAttemptRepository.class)
    @ConditionalOnMissingBean(GeihouAdminLoginAuthenticationService.LoginAttemptRecorderPort.class)
    public GeihouAdminLoginAuthenticationService.LoginAttemptRecorderPort geihouLoginAttemptRecorderPort(
            AuthLoginAttemptRepository authLoginAttemptRepository) {
        return new GeihouAuthLoginAttemptRecorderAdapter(authLoginAttemptRepository);
    }

    @Bean
    @ConditionalOnBean(AuthLoginAttemptRepository.class)
    @ConditionalOnMissingBean(GeihouTwoFactorVerifyOrchestrationService.TwoFactorAttemptRecorderPort.class)
    public GeihouTwoFactorVerifyOrchestrationService.TwoFactorAttemptRecorderPort geihouTwoFactorAttemptRecorderPort(
            AuthLoginAttemptRepository authLoginAttemptRepository) {
        return new GeihouTwoFactorAttemptRecorderAdapter(authLoginAttemptRepository);
    }

    @Bean
    @ConditionalOnBean(AuthCaptchaChallengeRepository.class)
    @ConditionalOnMissingBean(GeihouAdminLoginAuthenticationService.CaptchaPort.class)
    public GeihouAdminLoginAuthenticationService.CaptchaPort geihouCaptchaPort(
            AuthCaptchaChallengeRepository authCaptchaChallengeRepository) {
        return new GeihouCaptchaChallengeService(authCaptchaChallengeRepository);
    }

    @Bean
    @ConditionalOnBean(AuthTwoFactorTempTokenRepository.class)
    @ConditionalOnMissingBean(GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class)
    public GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort geihouTwoFactorTempTokenPort(
            AuthTwoFactorTempTokenRepository authTwoFactorTempTokenRepository) {
        return new GeihouTwoFactorTempTokenService(authTwoFactorTempTokenRepository);
    }

    @Bean
    @ConditionalOnBean(AuthRefreshTokenRepository.class)
    @ConditionalOnMissingBean(GeihouRefreshTokenPort.class)
    public GeihouRefreshTokenPort geihouRefreshTokenPort(AuthRefreshTokenRepository authRefreshTokenRepository) {
        return new GeihouRefreshTokenIssueService(authRefreshTokenRepository);
    }

    @Bean
    @ConditionalOnBean(TwoFactorSecretKeyProvider.class)
    @ConditionalOnMissingBean(TwoFactorSecretCryptoProvider.class)
    public TwoFactorSecretCryptoProvider geihouTwoFactorSecretCryptoProvider(
            TwoFactorSecretKeyProvider twoFactorSecretKeyProvider) {
        return new AesGcmTwoFactorSecretCryptoProvider(twoFactorSecretKeyProvider);
    }

    @Bean
    @ConditionalOnBean(TwoFactorSecretCryptoProvider.class)
    @ConditionalOnMissingBean(GeihouTotpVerifier.class)
    public GeihouTotpVerifier geihouTotpVerifier(TwoFactorSecretCryptoProvider twoFactorSecretCryptoProvider) {
        return new GeihouTotpVerifier(twoFactorSecretCryptoProvider);
    }

    @Bean
    @ConditionalOnBean({
            GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class,
            AuthUserRepository.class,
            GeihouTotpVerifier.class,
            GeihouAuthLoginTokenWiringService.class,
            GeihouRefreshTokenPort.class,
            GeihouTwoFactorVerifyOrchestrationService.TwoFactorAttemptRecorderPort.class
    })
    @ConditionalOnMissingBean(GeihouTwoFactorVerifyOrchestrationService.class)
    public GeihouTwoFactorVerifyOrchestrationService geihouTwoFactorVerifyOrchestrationService(
            GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort twoFactorTempTokenPort,
            AuthUserRepository authUserRepository,
            GeihouTotpVerifier totpVerifier,
            GeihouAuthLoginTokenWiringService tokenWiringService,
            GeihouRefreshTokenPort refreshTokenPort,
            GeihouTwoFactorVerifyOrchestrationService.TwoFactorAttemptRecorderPort twoFactorAttemptRecorderPort) {
        return new GeihouTwoFactorVerifyOrchestrationService(
                twoFactorTempTokenPort,
                authUserRepository,
                totpVerifier,
                tokenWiringService,
                refreshTokenPort,
                twoFactorAttemptRecorderPort);
    }

    @Bean
    @ConditionalOnBean({
            GeihouAdminLoginAuthenticationService.UserLookupPort.class,
            GeihouAdminLoginAuthenticationService.PasswordVerifierPort.class,
            GeihouAdminLoginAuthenticationService.CaptchaPort.class,
            GeihouAdminLoginAuthenticationService.TenantCodeResolverPort.class,
            GeihouAdminLoginAuthenticationService.LoginAttemptRecorderPort.class,
            GeihouAdminLoginAuthenticationService.LoginStatePort.class
    })
    @ConditionalOnMissingBean(GeihouAdminLoginAuthenticationService.class)
    public GeihouAdminLoginAuthenticationService geihouAdminLoginAuthenticationService(
            GeihouAdminLoginAuthenticationService.UserLookupPort userLookupPort,
            GeihouAdminLoginAuthenticationService.PasswordVerifierPort passwordVerifierPort,
            GeihouAdminLoginAuthenticationService.CaptchaPort captchaPort,
            GeihouAdminLoginAuthenticationService.TenantCodeResolverPort tenantCodeResolverPort,
            GeihouAdminLoginAuthenticationService.LoginAttemptRecorderPort loginAttemptRecorderPort,
            GeihouAdminLoginAuthenticationService.LoginStatePort loginStatePort) {
        return new GeihouAdminLoginAuthenticationService(
                userLookupPort,
                passwordVerifierPort,
                captchaPort,
                tenantCodeResolverPort,
                loginAttemptRecorderPort,
                loginStatePort);
    }

    @Bean
    @ConditionalOnBean({
            GeihouAdminLoginAuthenticationService.class,
            GeihouAuthLoginTokenWiringService.class,
            GeihouRefreshTokenPort.class,
            GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class
    })
    @ConditionalOnMissingBean(GeihouAdminLoginOrchestratorService.class)
    public GeihouAdminLoginOrchestratorService geihouAdminLoginOrchestratorService(
            GeihouAdminLoginAuthenticationService authenticationService,
            GeihouAuthLoginTokenWiringService tokenWiringService,
            GeihouRefreshTokenPort refreshTokenPort,
            GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort twoFactorTempTokenPort) {
        return new GeihouAdminLoginOrchestratorService(
                authenticationService, tokenWiringService, refreshTokenPort, twoFactorTempTokenPort);
    }
}
