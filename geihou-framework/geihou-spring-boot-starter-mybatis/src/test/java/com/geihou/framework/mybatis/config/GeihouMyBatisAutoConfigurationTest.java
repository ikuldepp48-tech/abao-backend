package com.geihou.framework.mybatis.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class GeihouMyBatisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GeihouMyBatisAutoConfiguration.class));

    @Test
    void shouldRegisterMybatisPlusInterceptor() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(MybatisPlusInterceptor.class));
    }

    @Test
    void shouldRegisterPaginationInnerInterceptor() {
        contextRunner.run(context -> {
            MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);

            assertThat(interceptor.getInterceptors())
                    .singleElement()
                    .isInstanceOfSatisfying(PaginationInnerInterceptor.class, pagination ->
                            assertThat(pagination.getDbType()).isEqualTo(DbType.MYSQL));
        });
    }

    @Test
    void shouldBackOffWhenUserProvidesMybatisPlusInterceptor() {
        contextRunner
                .withUserConfiguration(CustomMybatisPlusInterceptorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
                    assertThat(context.getBean(MybatisPlusInterceptor.class))
                            .isSameAs(context.getBean("customMybatisPlusInterceptor"));
                });
    }

    @Test
    void shouldLoadWithOnlyGenericMybatisConfiguration() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(MybatisPlusInterceptor.class));
    }

    @Test
    void shouldBeListedInAutoConfigurationImports() throws Exception {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(inputStream).isNotNull();
            String imports = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            assertThat(imports).contains(GeihouMyBatisAutoConfiguration.class.getName());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomMybatisPlusInterceptorConfiguration {

        @Bean
        MybatisPlusInterceptor customMybatisPlusInterceptor() {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
            return interceptor;
        }
    }
}
