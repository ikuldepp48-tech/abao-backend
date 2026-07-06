package com.geihou.module.system.framework.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.geihou.framework.security.core.spi.TokenValidator;
import com.geihou.module.system.api.auth.AuthApi;
import com.geihou.module.system.dal.mysql.auth.AuthTokenRevokedRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import com.geihou.module.system.framework.jwt.GeihouJwtHeaderParser;
import com.geihou.module.system.framework.jwt.GeihouJwtTokenParser;
import com.geihou.module.system.framework.jwt.GeihouSigningSecretProvider;
import com.geihou.module.system.service.auth.AuthApiImpl;
import com.geihou.module.system.service.auth.AuthApiTokenValidator;
import com.geihou.module.system.service.auth.GeihouAuthTokenVerifier;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GeihouAuthTokenAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GeihouAuthTokenAutoConfiguration.class));

    @Test
    void shouldNotRegisterAuthChainWhenSigningSecretProviderIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(TokenValidator.class);
            assertThat(context).doesNotHaveBean(AuthApi.class);
            assertThat(context).doesNotHaveBean(AuthApiTokenValidator.class);
            assertThat(context).doesNotHaveBean(GeihouAuthTokenVerifier.class);
        });
    }

    @Test
    void shouldRegisterAuthTokenValidatorWhenSigningSecretProviderExists() {
        contextRunner
                .withBean(GeihouSigningSecretProvider.class, () -> kid -> Optional.empty())
                .withBean(AuthTokenRevokedRepository.class, () -> mock(AuthTokenRevokedRepository.class))
                .withBean(AuthUserRepository.class, () -> mock(AuthUserRepository.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(TokenValidator.class);
                    assertThat(context.getBean(TokenValidator.class)).isInstanceOf(AuthApiTokenValidator.class);
                    assertThat(context).hasSingleBean(AuthApi.class);
                    assertThat(context.getBean(AuthApi.class)).isInstanceOf(AuthApiImpl.class);
                    assertThat(context).hasSingleBean(GeihouJwtHeaderParser.class);
                    assertThat(context).hasSingleBean(GeihouJwtTokenParser.class);
                    assertThat(context).hasSingleBean(GeihouAuthTokenVerifier.class);
                });
    }

    @Test
    void autoConfigurationImportsShouldContainAuthTokenAutoConfiguration() throws Exception {
        String path = "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream(path), StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));

            assertThat(content)
                    .contains("com.geihou.module.system.framework.security.config.GeihouAuthTokenAutoConfiguration");
        }
    }
}
