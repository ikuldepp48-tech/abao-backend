package com.geihou.module.system.controller.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies the {@link ConditionalOnProperty} mutual exclusion between
 * {@link GeihouAdminAuthController} and {@link GeihouAdminAuthUnavailableController}.
 *
 * <p>Source: G0-04H185-SYS-503-CONTRACT.
 */
class GeihouAuthControllerConditionMutualExclusionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GeihouAdminAuthController.class,
                    GeihouAdminAuthUnavailableController.class)
            .withBean(GeihouAdminLoginOrchestratorService.class,
                    () -> mock(GeihouAdminLoginOrchestratorService.class))
            .withBean(GeihouTwoFactorVerifyOrchestrationService.class,
                    () -> mock(GeihouTwoFactorVerifyOrchestrationService.class));

    @Test
    void defaultModeShouldRegisterRealControllerOnly() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GeihouAdminAuthController.class);
            assertThat(context).doesNotHaveBean(GeihouAdminAuthUnavailableController.class);
        });
    }

    @Test
    void authDisabledFalseShouldRegisterRealControllerOnly() {
        contextRunner
                .withPropertyValues("geihou.security.auth-disabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(GeihouAdminAuthController.class);
                    assertThat(context).doesNotHaveBean(GeihouAdminAuthUnavailableController.class);
                });
    }

    @Test
    void authDisabledTrueShouldRegisterUnavailableControllerOnly() {
        contextRunner
                .withPropertyValues("geihou.security.auth-disabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(GeihouAdminAuthController.class);
                    assertThat(context).hasSingleBean(GeihouAdminAuthUnavailableController.class);
                });
    }
}
