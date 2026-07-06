package com.geihou.framework.security.config;

import com.geihou.framework.security.core.filter.GeihouAdminRouteGuardFilter;
import com.geihou.framework.security.core.filter.GeihouSecurityContextFilter;
import com.geihou.framework.security.core.handler.GeihouSecurityErrorHandler;
import com.geihou.framework.security.core.spi.DenyAllPermissionChecker;
import com.geihou.framework.security.core.spi.DenyAllTokenValidator;
import com.geihou.framework.security.core.spi.PermissionChecker;
import com.geihou.framework.security.core.spi.TokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GeihouSecurityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GeihouSecurityAutoConfiguration.class));

    @Test
    void shouldRegisterDefaultFailClosedBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TokenValidator.class);
            assertThat(context).hasSingleBean(PermissionChecker.class);
            assertThat(context).hasSingleBean(GeihouSecurityProperties.class);
            assertThat(context).hasSingleBean(GeihouSecurityErrorHandler.class);
            assertThat(context).hasSingleBean(GeihouSecurityContextFilter.class);
            assertThat(context).hasSingleBean(GeihouAdminRouteGuardFilter.class);
            assertThat(context.getBean(TokenValidator.class)).isInstanceOf(DenyAllTokenValidator.class);
            assertThat(context.getBean(PermissionChecker.class)).isInstanceOf(DenyAllPermissionChecker.class);
            assertThat(context.getBean(GeihouAdminRouteGuardFilter.class).getOrder())
                    .isEqualTo(GeihouSecurityContextFilter.ORDER + 1);
        });
    }

    @Test
    void shouldRespectCustomSpiBeans() {
        TokenValidator customTokenValidator = token -> null;
        PermissionChecker customPermissionChecker = (principal, permission) -> true;

        contextRunner
                .withBean(TokenValidator.class, () -> customTokenValidator)
                .withBean(PermissionChecker.class, () -> customPermissionChecker)
                .run(context -> {
                    assertThat(context.getBean(TokenValidator.class)).isSameAs(customTokenValidator);
                    assertThat(context.getBean(PermissionChecker.class)).isSameAs(customPermissionChecker);
                });
    }

    @Test
    void shouldRespectCustomRouteGuardBean() {
        GeihouAdminRouteGuardFilter customRouteGuard = new GeihouAdminRouteGuardFilter(
                new GeihouSecurityProperties(),
                new GeihouSecurityErrorHandler(new com.fasterxml.jackson.databind.ObjectMapper()));

        contextRunner
                .withBean(GeihouAdminRouteGuardFilter.class, () -> customRouteGuard)
                .run(context -> assertThat(context.getBean(GeihouAdminRouteGuardFilter.class))
                        .isSameAs(customRouteGuard));
    }

    @Test
    void shouldBindCustomPathPatternProperties() {
        contextRunner
                .withPropertyValues(
                        "geihou.security.protected-path-patterns[0]=/internal/**",
                        "geihou.security.protected-path-patterns[1]=/ops/**",
                        "geihou.security.permit-path-patterns[0]=/internal/auth/**")
                .run(context -> {
                    GeihouSecurityProperties properties = context.getBean(GeihouSecurityProperties.class);

                    assertThat(properties.getProtectedPathPatterns()).containsExactly("/internal/**", "/ops/**");
                    assertThat(properties.getPermitPathPatterns()).containsExactly("/internal/auth/**");
                });
    }

    @Test
    void autoConfigurationImportsShouldContainSecurityAutoConfiguration() throws Exception {
        String path = "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream(path), StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));

            assertThat(content.trim()).isEqualTo(
                    "com.geihou.framework.security.config.GeihouSecurityAutoConfiguration");
        }
    }
}
