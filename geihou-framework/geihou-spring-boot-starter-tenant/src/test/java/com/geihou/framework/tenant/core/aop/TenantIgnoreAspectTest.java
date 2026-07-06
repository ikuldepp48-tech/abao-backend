package com.geihou.framework.tenant.core.aop;

import com.geihou.framework.tenant.core.annotation.TenantIgnore;
import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.framework.tenant.core.context.TenantTaskDecorator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(TenantIgnoreAspectTest.TestConfig.class)
class TenantIgnoreAspectTest {

    @Autowired
    private TestService testService;

    @Autowired
    private TypeIgnoredService typeIgnoredService;

    @Autowired
    private OuterService outerService;

    @Autowired
    private DecoratedTaskService decoratedTaskService;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldSetIgnoreFlagDuringAnnotatedMethod() {
        assertThat(testService.methodWithTenantIgnore()).isTrue();
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    @Test
    void shouldNotSetIgnoreFlagForNonAnnotatedMethod() {
        assertThat(testService.methodWithoutTenantIgnore()).isFalse();
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    @Test
    void shouldRestorePreviousIgnoreFlag() {
        TenantContextHolder.setIgnore(true);

        assertThat(testService.methodWithTenantIgnore()).isTrue();

        assertThat(TenantContextHolder.isIgnore()).isTrue();
    }

    @Test
    void shouldSupportNestedAnnotatedCalls() {
        assertThat(outerService.outerAnnotatedMethod()).isTrue();
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    @Test
    void shouldSupportTypeLevelTenantIgnore() {
        assertThat(typeIgnoredService.methodFromIgnoredType()).isTrue();
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    @Test
    void shouldPropagateIgnoreFlagToDecoratedTaskSubmittedInsideTenantIgnore() throws Exception {
        assertThat(decoratedTaskService.decoratedTaskSubmittedInsideTenantIgnore()).isTrue();
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    @Test
    void shouldNotLeakIgnoreFlagToLaterDecoratedTask() throws Exception {
        decoratedTaskService.decoratedTaskSubmittedInsideTenantIgnore();

        assertThat(decoratedTaskService.decoratedTaskSubmittedOutsideTenantIgnore()).isFalse();
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        TenantIgnoreAspect tenantIgnoreAspect() {
            return new TenantIgnoreAspect();
        }

        @Bean
        TestService testService() {
            return new TestService();
        }

        @Bean
        TypeIgnoredService typeIgnoredService() {
            return new TypeIgnoredService();
        }

        @Bean
        InnerService innerService() {
            return new InnerService();
        }

        @Bean
        OuterService outerService(InnerService innerService) {
            return new OuterService(innerService);
        }

        @Bean(destroyMethod = "shutdownNow")
        ExecutorService executorService() {
            return Executors.newSingleThreadExecutor();
        }

        @Bean
        TenantTaskDecorator tenantTaskDecorator() {
            return new TenantTaskDecorator();
        }

        @Bean
        DecoratedTaskService decoratedTaskService(ExecutorService executorService, TenantTaskDecorator decorator) {
            return new DecoratedTaskService(executorService, decorator);
        }
    }

    static class TestService {

        @TenantIgnore
        public boolean methodWithTenantIgnore() {
            return TenantContextHolder.isIgnore();
        }

        public boolean methodWithoutTenantIgnore() {
            return TenantContextHolder.isIgnore();
        }
    }

    @TenantIgnore
    static class TypeIgnoredService {

        public boolean methodFromIgnoredType() {
            return TenantContextHolder.isIgnore();
        }
    }

    static class InnerService {

        @TenantIgnore
        public boolean innerAnnotatedMethod() {
            return TenantContextHolder.isIgnore();
        }
    }

    static class OuterService {

        private final InnerService innerService;

        OuterService(InnerService innerService) {
            this.innerService = innerService;
        }

        @TenantIgnore
        public boolean outerAnnotatedMethod() {
            boolean beforeInner = TenantContextHolder.isIgnore();
            boolean innerResult = innerService.innerAnnotatedMethod();
            boolean afterInner = TenantContextHolder.isIgnore();
            return beforeInner && innerResult && afterInner;
        }
    }

    static class DecoratedTaskService {

        private final ExecutorService executorService;
        private final TenantTaskDecorator decorator;

        DecoratedTaskService(ExecutorService executorService, TenantTaskDecorator decorator) {
            this.executorService = executorService;
            this.decorator = decorator;
        }

        @TenantIgnore
        public boolean decoratedTaskSubmittedInsideTenantIgnore() throws Exception {
            return submitIgnoreSnapshot();
        }

        public boolean decoratedTaskSubmittedOutsideTenantIgnore() throws Exception {
            return submitIgnoreSnapshot();
        }

        private boolean submitIgnoreSnapshot() throws Exception {
            AtomicReference<Boolean> ignoreSnapshot = new AtomicReference<>();
            Future<?> future = executorService.submit(decorator.decorate(
                    () -> ignoreSnapshot.set(TenantContextHolder.isIgnore())));
            future.get(3, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ignoreSnapshot.get());
        }
    }
}
