package com.geihou.framework.tenant.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.framework.tenant.core.aop.TenantIgnoreAspect;
import com.geihou.framework.tenant.core.interceptor.TenantInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TenantMyBatisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    GeihouMyBatisAutoConfiguration.class, TenantMyBatisAutoConfiguration.class));

    private final ApplicationContextRunner tenantOnlyContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantMyBatisAutoConfiguration.class));

    @Test
    void shouldRegisterTenantInterceptorBean() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(TenantInterceptor.class));
    }

    @Test
    void shouldRegisterMybatisPlusInterceptorBean() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(MybatisPlusInterceptor.class));
    }

    @Test
    void shouldRegisterTenantLineInnerInterceptorBean() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(TenantLineInnerInterceptor.class));
    }

    @Test
    void shouldRegisterTenantIgnoreAspectBean() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(TenantIgnoreAspect.class));
    }

    @Test
    void shouldPlaceTenantLineInnerInterceptorAtFirstPosition() {
        contextRunner.run(context -> {
            MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);

            assertThat(interceptor.getInterceptors()).isNotEmpty();
            assertThat(interceptor.getInterceptors().get(0)).isInstanceOf(TenantLineInnerInterceptor.class);
        });
    }

    @Test
    void shouldKeepPaginationAfterTenantLineWhenBothStartersAreLoaded() {
        contextRunner.run(context -> {
            MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);

            assertThat(interceptor.getInterceptors()).hasSize(2);
            assertThat(interceptor.getInterceptors().get(0)).isInstanceOf(TenantLineInnerInterceptor.class);
            assertThat(interceptor.getInterceptors().get(1)).isInstanceOf(PaginationInnerInterceptor.class);
        });
    }

    @Test
    void shouldNotCreateEmptyMybatisPlusInterceptorWithoutGenericMybatisStarter() {
        tenantOnlyContextRunner.run(context -> {
            assertThat(context).hasSingleBean(TenantInterceptor.class);
            assertThat(context).hasSingleBean(TenantIgnoreAspect.class);
            assertThat(context).doesNotHaveBean(MybatisPlusInterceptor.class);
            assertThat(context).doesNotHaveBean(TenantLineInnerInterceptor.class);
        });
    }
}
