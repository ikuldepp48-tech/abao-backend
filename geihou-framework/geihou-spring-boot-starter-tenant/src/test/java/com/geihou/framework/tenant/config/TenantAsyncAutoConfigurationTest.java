package com.geihou.framework.tenant.config;

import com.geihou.framework.tenant.core.context.TenantTaskDecorator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskDecorator;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAsyncAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantAsyncAutoConfiguration.class));

    @Test
    void shouldRegisterTenantTaskDecoratorBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TenantTaskDecorator.class);
            assertThat(context).hasSingleBean(TaskDecorator.class);
        });
    }

    @Test
    void shouldNotReplaceUserProvidedTaskDecorator() {
        TaskDecorator customDecorator = runnable -> runnable;

        contextRunner
                .withBean(TaskDecorator.class, () -> customDecorator)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TenantTaskDecorator.class);
                    assertThat(context).hasSingleBean(TaskDecorator.class);
                    assertThat(context.getBean(TaskDecorator.class)).isSameAs(customDecorator);
                });
    }
}
