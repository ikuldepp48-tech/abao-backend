package com.geihou.framework.tenant.core.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.framework.tenant.config.TenantAsyncAutoConfiguration;
import com.geihou.framework.tenant.config.TenantMyBatisAutoConfiguration;
import com.geihou.framework.tenant.core.annotation.TenantIgnore;
import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.framework.tenant.core.context.TenantTaskDecorator;
import com.geihou.framework.tenant.test.TestEntity;
import com.geihou.framework.tenant.test.mapper.TestEntityMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = TenantInterceptorIntegrationTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:tenant_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.aop.proxy-target-class=true"
        }
)
class TenantInterceptorIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    @EnableAsync(proxyTargetClass = true)
    @Import({GeihouMyBatisAutoConfiguration.class, TenantMyBatisAutoConfiguration.class, TenantAsyncAutoConfiguration.class})
    @MapperScan("com.geihou.framework.tenant.test.mapper")
    static class TestConfig {

        @Bean
        IgnoreQueryService ignoreQueryService(TestEntityMapper mapper) {
            return new IgnoreQueryService(mapper);
        }

        @Bean
        AsyncQueryService asyncQueryService(TestEntityMapper mapper) {
            return new AsyncQueryService(mapper);
        }

        @Bean
        IgnoreAsyncQueryLauncher ignoreAsyncQueryLauncher(AsyncQueryService asyncQueryService) {
            return new IgnoreAsyncQueryLauncher(asyncQueryService);
        }

        @Bean(name = "taskExecutor", destroyMethod = "shutdown")
        ThreadPoolTaskExecutor taskExecutor(TenantTaskDecorator decorator) {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(8);
            executor.setThreadNamePrefix("tenant-async-test-");
            executor.setTaskDecorator(decorator);
            executor.initialize();
            return executor;
        }
    }

    @Autowired
    private TestEntityMapper mapper;

    @Autowired
    private IgnoreQueryService ignoreQueryService;

    @Autowired
    private AsyncQueryService asyncQueryService;

    @Autowired
    private IgnoreAsyncQueryLauncher ignoreAsyncQueryLauncher;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS test_entity (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        name VARCHAR(255) NOT NULL
                    )
                    """);
            statement.execute("DELETE FROM test_entity");
        }
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldQueryOnlyTenantOneRowsWhenContextIsTenantOne() {
        insertRow(1L, "tenant-one");
        insertRow(2L, "tenant-two");

        TenantContextHolder.setTenantId(1L);

        List<TestEntity> results = mapper.selectList(new LambdaQueryWrapper<>());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTenantId()).isEqualTo(1L);
        assertThat(results.get(0).getName()).isEqualTo("tenant-one");
    }

    @Test
    void shouldQueryOnlyTenantTwoRowsWhenContextIsTenantTwo() {
        insertRow(1L, "tenant-one");
        insertRow(2L, "tenant-two");

        TenantContextHolder.setTenantId(2L);

        List<TestEntity> results = mapper.selectList(new LambdaQueryWrapper<>());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTenantId()).isEqualTo(2L);
        assertThat(results.get(0).getName()).isEqualTo("tenant-two");
    }

    @Test
    void shouldIsolateRowsAfterTenantSwitch() {
        insertRow(1L, "tenant-one-a");
        insertRow(1L, "tenant-one-b");
        insertRow(2L, "tenant-two");

        TenantContextHolder.setTenantId(1L);
        assertThat(mapper.selectList(new LambdaQueryWrapper<>())).hasSize(2);

        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(2L);
        assertThat(mapper.selectList(new LambdaQueryWrapper<>())).hasSize(1);
    }

    @Test
    void shouldFailQueryWhenTenantContextMissing() {
        insertRow(1L, "tenant-one");

        assertThatThrownBy(() -> mapper.selectList(new LambdaQueryWrapper<>()))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context is required");
    }

    @Test
    void shouldBypassTenantFilteringInsideTenantIgnoreMethod() {
        insertRow(1L, "tenant-one");
        insertRow(2L, "tenant-two");
        TenantContextHolder.setTenantId(1L);

        List<TestEntity> results = ignoreQueryService.findAll();

        assertThat(results).hasSize(2);
        assertThat(results).extracting(TestEntity::getTenantId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    @Test
    void shouldRestoreTenantFilteringAfterTenantIgnoreMethod() {
        insertRow(1L, "tenant-one");
        insertRow(2L, "tenant-two");
        TenantContextHolder.setTenantId(1L);

        ignoreQueryService.findAll();
        List<TestEntity> results = mapper.selectList(new LambdaQueryWrapper<>());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTenantId()).isEqualTo(1L);
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    @Test
    void shouldStartEachTestWithoutIgnoreFlag() {
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    @Test
    void shouldQueryOnlyTenantOneRowsInAsyncDecoratedTask() throws Exception {
        insertRow(1L, "tenant-one");
        insertRow(2L, "tenant-two");
        TenantContextHolder.setTenantId(1L);

        List<TestEntity> results = asyncQueryService.findAll().get(3, TimeUnit.SECONDS);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTenantId()).isEqualTo(1L);
    }

    @Test
    void shouldRunAsyncQueryOnConfiguredExecutorThread() throws Exception {
        String callerThreadName = Thread.currentThread().getName();

        String asyncThreadName = asyncQueryService.currentThreadName().get(3, TimeUnit.SECONDS);

        assertThat(asyncThreadName).startsWith("tenant-async-test-");
        assertThat(asyncThreadName).isNotEqualTo(callerThreadName);
    }

    @Test
    void shouldQueryOnlyTenantTwoRowsInAsyncDecoratedTask() throws Exception {
        insertRow(1L, "tenant-one");
        insertRow(2L, "tenant-two");
        TenantContextHolder.setTenantId(2L);

        List<TestEntity> results = asyncQueryService.findAll().get(3, TimeUnit.SECONDS);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTenantId()).isEqualTo(2L);
    }

    @Test
    void shouldNotLeakTenantIdBetweenSequentialAsyncTasks() throws Exception {
        insertRow(1L, "tenant-one");
        insertRow(2L, "tenant-two");

        TenantContextHolder.setTenantId(1L);
        List<TestEntity> tenantOneResults = asyncQueryService.findAll().get(3, TimeUnit.SECONDS);

        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(2L);
        List<TestEntity> tenantTwoResults = asyncQueryService.findAll().get(3, TimeUnit.SECONDS);

        assertThat(tenantOneResults).extracting(TestEntity::getTenantId).containsExactly(1L);
        assertThat(tenantTwoResults).extracting(TestEntity::getTenantId).containsExactly(2L);
    }

    @Test
    void shouldFailAsyncTaskWhenTenantContextMissing() {
        insertRow(1L, "tenant-one");

        CompletableFuture<List<TestEntity>> future = asyncQueryService.findAll();

        assertThatThrownBy(() -> future.get(3, TimeUnit.SECONDS))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context is required");
    }

    @Test
    void shouldBypassTenantFilteringInAsyncTaskWhenIgnoreIsTrue() throws Exception {
        insertRow(1L, "tenant-one");
        insertRow(2L, "tenant-two");
        TenantContextHolder.setTenantId(1L);

        List<TestEntity> ignoredResults = ignoreAsyncQueryLauncher.findAllIgnoringTenant()
                .get(3, TimeUnit.SECONDS);
        List<TestEntity> normalResults = mapper.selectList(new LambdaQueryWrapper<>());

        assertThat(ignoredResults).hasSize(2);
        assertThat(ignoredResults).extracting(TestEntity::getTenantId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(normalResults).hasSize(1);
        assertThat(normalResults.get(0).getTenantId()).isEqualTo(1L);
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    private void insertRow(Long tenantId, String name) {
        new JdbcTemplate(dataSource).update(
                "INSERT INTO test_entity (tenant_id, name) VALUES (?, ?)",
                tenantId,
                name);
    }

    static class IgnoreQueryService {

        private final TestEntityMapper mapper;

        IgnoreQueryService(TestEntityMapper mapper) {
            this.mapper = mapper;
        }

        @TenantIgnore
        public List<TestEntity> findAll() {
            return mapper.selectList(new LambdaQueryWrapper<>());
        }
    }

    static class AsyncQueryService {

        private final TestEntityMapper mapper;

        AsyncQueryService(TestEntityMapper mapper) {
            this.mapper = mapper;
        }

        @Async
        public CompletableFuture<List<TestEntity>> findAll() {
            return CompletableFuture.completedFuture(mapper.selectList(new LambdaQueryWrapper<>()));
        }

        @Async
        public CompletableFuture<String> currentThreadName() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }

    static class IgnoreAsyncQueryLauncher {

        private final AsyncQueryService asyncQueryService;

        IgnoreAsyncQueryLauncher(AsyncQueryService asyncQueryService) {
            this.asyncQueryService = asyncQueryService;
        }

        @TenantIgnore
        public CompletableFuture<List<TestEntity>> findAllIgnoringTenant() {
            return asyncQueryService.findAll();
        }
    }
}
