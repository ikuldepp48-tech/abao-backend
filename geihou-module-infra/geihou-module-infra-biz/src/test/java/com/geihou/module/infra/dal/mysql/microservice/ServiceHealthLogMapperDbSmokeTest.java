package com.geihou.module.infra.dal.mysql.microservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.geihou.common.pojo.PageResult;
import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.infra.dal.dataobject.microservice.ServiceHealthLogDO;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        classes = ServiceHealthLogMapperDbSmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_infra_service_health_log_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ServiceHealthLogMapperDbSmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import(GeihouMyBatisAutoConfiguration.class)
    @MapperScan("com.geihou.module.infra.dal.mysql.microservice")
    static class TestConfig {
    }

    @Autowired
    private ServiceHealthLogMapper mapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS service_health_log");
        jdbcTemplate.execute("""
                CREATE TABLE service_health_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    service_name VARCHAR(64) NOT NULL,
                    check_time TIMESTAMP NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    response_time_ms INT,
                    error_message TEXT,
                    create_time TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO service_health_log (service_name, check_time, status, response_time_ms, error_message, create_time)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "geihou-module-gateway", LocalDateTime.parse("2026-06-12T08:00:00"), "UP", 31, null,
                LocalDateTime.parse("2026-06-12T08:00:01"));
        jdbcTemplate.update("""
                INSERT INTO service_health_log (service_name, check_time, status, response_time_ms, error_message, create_time)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "geihou-module-system", LocalDateTime.parse("2026-06-12T08:01:00"), "UP", 45, null,
                LocalDateTime.parse("2026-06-12T08:01:01"));
        jdbcTemplate.update("""
                INSERT INTO service_health_log (service_name, check_time, status, response_time_ms, error_message, create_time)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "geihou-module-report", LocalDateTime.parse("2026-06-12T08:02:00"), "DOWN", null,
                "Connection refused", LocalDateTime.parse("2026-06-12T08:02:01"));
    }

    @Test
    void shouldRegisterPaginationInterceptor() {
        assertThat(mybatisPlusInterceptor.getInterceptors())
                .singleElement()
                .isInstanceOfSatisfying(PaginationInnerInterceptor.class, pagination ->
                        assertThat(pagination.getDbType()).isEqualTo(DbType.MYSQL));
    }

    @Test
    void shouldInsertAndSelectById() {
        ServiceHealthLogDO log = newHealthLog("geihou-module-finance",
                LocalDateTime.parse("2026-06-12T08:03:00"), "TIMEOUT", null, "Timeout");

        assertThat(mapper.insert(log)).isEqualTo(1);
        assertThat(log.getId()).isNotNull();

        ServiceHealthLogDO selected = mapper.selectById(log.getId());
        assertThat(selected.getServiceName()).isEqualTo("geihou-module-finance");
        assertThat(selected.getStatus()).isEqualTo("TIMEOUT");
        assertThat(selected.getErrorMessage()).isEqualTo("Timeout");
    }

    @Test
    void shouldSelectOneBySingleField() {
        ServiceHealthLogDO log = mapper.selectOne(
                ServiceHealthLogDO::getServiceName, "geihou-module-gateway");

        assertThat(log).isNotNull();
        assertThat(log.getStatus()).isEqualTo("UP");
        assertThat(log.getResponseTimeMs()).isEqualTo(31);
    }

    @Test
    void shouldSelectOneByTwoFields() {
        ServiceHealthLogDO log = mapper.selectOne(
                ServiceHealthLogDO::getServiceName, "geihou-module-system",
                ServiceHealthLogDO::getStatus, "UP");

        assertThat(log).isNotNull();
        assertThat(log.getResponseTimeMs()).isEqualTo(45);
    }

    @Test
    void shouldSelectListsAndCounts() {
        assertThat(mapper.selectList()).hasSize(3);
        assertThat(mapper.selectList(ServiceHealthLogDO::getStatus, "UP")).hasSize(2);
        assertThat(mapper.selectList(ServiceHealthLogDO::getStatus, "DOWN",
                ServiceHealthLogDO::getServiceName, "geihou-module-report")).hasSize(1);
        assertThat(mapper.selectCount(ServiceHealthLogDO::getStatus, "DOWN")).isEqualTo(1L);
    }

    @Test
    void shouldSelectPages() {
        PageResult<ServiceHealthLogDO> firstPage = mapper.selectPage(1, 2);
        assertThat(firstPage.getList()).hasSize(2);
        assertThat(firstPage.getTotal()).isEqualTo(3L);
        assertThat(firstPage.getPageNo()).isEqualTo(1);
        assertThat(firstPage.getPageSize()).isEqualTo(2);

        PageResult<ServiceHealthLogDO> orderedPage = mapper.selectPage(1, 2,
                new LambdaQueryWrapper<ServiceHealthLogDO>()
                        .orderByAsc(ServiceHealthLogDO::getCheckTime));
        assertThat(orderedPage.getList())
                .extracting(ServiceHealthLogDO::getServiceName)
                .containsExactly("geihou-module-gateway", "geihou-module-system");

        PageResult<ServiceHealthLogDO> upPage = mapper.selectPage(
                ServiceHealthLogDO::getStatus, "UP", 1, 10);
        assertThat(upPage.getList()).hasSize(2);
        assertThat(upPage.getTotal()).isEqualTo(2L);

        PageResult<ServiceHealthLogDO> downPage = mapper.selectPage(
                ServiceHealthLogDO::getStatus, "DOWN",
                ServiceHealthLogDO::getServiceName, "geihou-module-report", 1, 10);
        assertThat(downPage.getList()).hasSize(1);
        assertThat(downPage.getTotal()).isEqualTo(1L);
    }

    private static ServiceHealthLogDO newHealthLog(String serviceName, LocalDateTime checkTime,
                                                    String status, Integer responseTimeMs,
                                                    String errorMessage) {
        ServiceHealthLogDO log = new ServiceHealthLogDO();
        log.setServiceName(serviceName);
        log.setCheckTime(checkTime);
        log.setStatus(status);
        log.setResponseTimeMs(responseTimeMs);
        log.setErrorMessage(errorMessage);
        log.setCreateTime(LocalDateTime.parse("2026-06-12T08:03:01"));
        return log;
    }
}
