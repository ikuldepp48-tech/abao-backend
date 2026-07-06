package com.geihou.framework.tenant.config;

import com.geihou.framework.tenant.web.TenantContextFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantAutoConfiguration.class));

    @Test
    void shouldLoadApplicationContext() {
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldRegisterTenantContextFilterBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TenantContextFilter.class);
            assertThat(context.getBean(TenantContextFilter.class)).isNotNull();
        });
    }
}
