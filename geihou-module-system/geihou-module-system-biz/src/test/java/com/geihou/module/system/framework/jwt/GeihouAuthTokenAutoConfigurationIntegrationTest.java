package com.geihou.module.system.framework.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.framework.security.core.spi.TokenValidationResult;
import com.geihou.framework.security.core.spi.TokenValidator;
import com.geihou.module.system.api.auth.AuthApi;
import com.geihou.module.system.api.auth.dto.AuthTokenVerifyRespDTO;
import com.geihou.module.system.dal.dataobject.auth.AuthUserDO;
import com.geihou.module.system.dal.mysql.auth.AuthTokenRevokedRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import com.geihou.module.system.framework.security.config.GeihouAuthTokenAutoConfiguration;
import com.geihou.module.system.service.auth.GeihouAuthTokenVerifier;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GeihouAuthTokenAutoConfigurationIntegrationTest {

    private static final Long USER_ID = 100L;
    private static final Long TENANT_ID = 200L;
    private static final String TOKEN_ID = "jwt-id-1";
    private static final String USER_ROLE = "OWNER";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GeihouAuthTokenAutoConfiguration.class));

    @Test
    void shouldVerifyValidTestJwtThroughAuthApiAndTokenValidatorWhenTestProviderExists() {
        AuthTokenRevokedRepository revokedRepository = mock(AuthTokenRevokedRepository.class);
        AuthUserRepository userRepository = mock(AuthUserRepository.class);
        when(revokedRepository.existsByJti(TOKEN_ID)).thenReturn(false);
        when(userRepository.selectById(USER_ID)).thenReturn(activeUser());

        contextRunner
                .withBean(GeihouSigningSecretProvider.class, GeihouTestSigningSecretProvider::new)
                .withBean(AuthTokenRevokedRepository.class, () -> revokedRepository)
                .withBean(AuthUserRepository.class, () -> userRepository)
                .run(context -> {
                    String token = GeihouJwtTestTokenFactory.validToken(
                            Instant.now(), GeihouJwtTestTokenFactory.testSecret());

                    AuthTokenVerifyRespDTO response = context.getBean(AuthApi.class).verifyToken(token);
                    assertThat(response.getValid()).isTrue();
                    assertThat(response.getUserId()).isEqualTo(USER_ID);
                    assertThat(response.getTenantId()).isEqualTo(TENANT_ID);
                    assertThat(response.getTokenId()).isEqualTo(TOKEN_ID);
                    assertThat(response.getUserRole()).isEqualTo(USER_ROLE);

                    TokenValidationResult validation = context.getBean(TokenValidator.class).validate(token);
                    assertThat(validation.valid()).isTrue();
                    assertThat(validation.principal().userId()).isEqualTo(USER_ID);
                    assertThat(validation.principal().tenantId()).isEqualTo(TENANT_ID);
                    assertThat(validation.principal().tokenId()).isEqualTo(TOKEN_ID);
                    assertThat(validation.principal().tokenScopes()).containsExactlyInAnyOrder("OWNER", "SHOP_MANAGER");
                });
    }

    @Test
    void shouldFailClosedForUnknownKidThroughAuthApi() {
        AuthTokenRevokedRepository revokedRepository = mock(AuthTokenRevokedRepository.class);
        AuthUserRepository userRepository = mock(AuthUserRepository.class);

        contextRunner
                .withBean(GeihouSigningSecretProvider.class, GeihouTestSigningSecretProvider::new)
                .withBean(AuthTokenRevokedRepository.class, () -> revokedRepository)
                .withBean(AuthUserRepository.class, () -> userRepository)
                .run(context -> {
                    String token = GeihouJwtTestTokenFactory.sign(
                            GeihouJwtTestTokenFactory.header(JWSAlgorithm.HS512, JOSEObjectType.JWT, "unknown-key"),
                            GeihouJwtTestTokenFactory.validClaimsBuilder(Instant.now()).build(),
                            GeihouJwtTestTokenFactory.testSecret());

                    AuthTokenVerifyRespDTO response = context.getBean(AuthApi.class).verifyToken(token);

                    assertThat(response.getValid()).isFalse();
                    assertThat(response.getErrorCode()).isEqualTo(GeihouAuthErrorCodes.INVALID_TOKEN);
                    verify(revokedRepository, never()).existsByJti(TOKEN_ID);
                    verify(userRepository, never()).selectById(USER_ID);
                });
    }

    @Test
    void shouldNotCreateAuthChainWhenTestProviderIsMissing() {
        contextRunner
                .withBean(AuthTokenRevokedRepository.class, () -> mock(AuthTokenRevokedRepository.class))
                .withBean(AuthUserRepository.class, () -> mock(AuthUserRepository.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AuthApi.class);
                    assertThat(context).doesNotHaveBean(TokenValidator.class);
                    assertThat(context).doesNotHaveBean(GeihouAuthTokenVerifier.class);
                });
    }

    private static AuthUserDO activeUser() {
        AuthUserDO user = new AuthUserDO();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        user.setUserRole(USER_ROLE);
        user.setStatus("ACTIVE");
        user.setDeleted(false);
        return user;
    }
}
