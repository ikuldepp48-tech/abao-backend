package com.geihou.module.infra.dal.mysql.microservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.geihou.common.pojo.PageResult;
import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.infra.dal.dataobject.microservice.MicroserviceRegistryDO;
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
        classes = MicroserviceRegistryMapperDbSmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_infra_microservice_registry_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class MicroserviceRegistryMapperDbSmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import(GeihouMyBatisAutoConfiguration.class)
    @MapperScan("com.geihou.module.infra.dal.mysql.microservice")
    static class TestConfig {
    }

    @Autowired
    private MicroserviceRegistryMapper mapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS microservice_registry");
        jdbcTemplate.execute("""
                CREATE TABLE microservice_registry (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    service_name VARCHAR(64) NOT NULL,
                    service_port INT NOT NULL,
                    subsystem_id TINYINT,
                    service_type VARCHAR(20) NOT NULL,
                    health_check_url VARCHAR(255) NOT NULL,
                    startup_priority TINYINT NOT NULL DEFAULT 50,
                    is_required BOOLEAN NOT NULL DEFAULT TRUE,
                    creator VARCHAR(64) NOT NULL DEFAULT '',
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updater VARCHAR(64) NOT NULL DEFAULT '',
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO microservice_registry (service_name, service_port, subsystem_id, service_type,
                    health_check_url, startup_priority, is_required, creator, updater)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "geihou-module-gateway", 48092, null, "GATEWAY", "/actuator/health", 1, true, "h8", "h8");
        jdbcTemplate.update("""
                INSERT INTO microservice_registry (service_name, service_port, subsystem_id, service_type,
                    health_check_url, startup_priority, is_required, creator, updater)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "geihou-module-system", 48080, 1, "BUSINESS", "/actuator/health", 10, true, "h8", "h8");
        jdbcTemplate.update("""
                INSERT INTO microservice_registry (service_name, service_port, subsystem_id, service_type,
                    health_check_url, startup_priority, is_required, creator, updater)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "geihou-module-report", 48091, 8, "BUSINESS", "/actuator/health", 50, false, "h8", "h8");
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
        MicroserviceRegistryDO registry = newRegistry("geihou-module-finance", 48083, 3, "BUSINESS", true);

        assertThat(mapper.insert(registry)).isEqualTo(1);
        assertThat(registry.getId()).isNotNull();

        MicroserviceRegistryDO selected = mapper.selectById(registry.getId());
        assertThat(selected.getServiceName()).isEqualTo("geihou-module-finance");
        assertThat(selected.getServicePort()).isEqualTo(48083);
    }

    @Test
    void shouldSelectOneBySingleField() {
        MicroserviceRegistryDO registry = mapper.selectOne(
                MicroserviceRegistryDO::getServiceName, "geihou-module-system");

        assertThat(registry).isNotNull();
        assertThat(registry.getServiceType()).isEqualTo("BUSINESS");
        assertThat(registry.getServicePort()).isEqualTo(48080);
    }

    @Test
    void shouldSelectOneByTwoFields() {
        MicroserviceRegistryDO registry = mapper.selectOne(
                MicroserviceRegistryDO::getServiceName, "geihou-module-gateway",
                MicroserviceRegistryDO::getServiceType, "GATEWAY");

        assertThat(registry).isNotNull();
        assertThat(registry.getStartupPriority()).isEqualTo(1);
    }

    @Test
    void shouldSelectListsAndCounts() {
        assertThat(mapper.selectList()).hasSize(3);
        assertThat(mapper.selectList(MicroserviceRegistryDO::getServiceType, "BUSINESS")).hasSize(2);
        assertThat(mapper.selectList(MicroserviceRegistryDO::getServiceType, "BUSINESS",
                MicroserviceRegistryDO::getIsRequired, true)).hasSize(1);
        assertThat(mapper.selectCount(MicroserviceRegistryDO::getIsRequired, false)).isEqualTo(1L);
    }

    @Test
    void shouldSelectPages() {
        PageResult<MicroserviceRegistryDO> firstPage = mapper.selectPage(1, 2);
        assertThat(firstPage.getList()).hasSize(2);
        assertThat(firstPage.getTotal()).isEqualTo(3L);
        assertThat(firstPage.getPageNo()).isEqualTo(1);
        assertThat(firstPage.getPageSize()).isEqualTo(2);

        PageResult<MicroserviceRegistryDO> orderedPage = mapper.selectPage(1, 2,
                new LambdaQueryWrapper<MicroserviceRegistryDO>()
                        .orderByAsc(MicroserviceRegistryDO::getServiceName));
        assertThat(orderedPage.getList())
                .extracting(MicroserviceRegistryDO::getServiceName)
                .containsExactly("geihou-module-gateway", "geihou-module-report");

        PageResult<MicroserviceRegistryDO> businessPage = mapper.selectPage(
                MicroserviceRegistryDO::getServiceType, "BUSINESS", 1, 10);
        assertThat(businessPage.getList()).hasSize(2);
        assertThat(businessPage.getTotal()).isEqualTo(2L);

        PageResult<MicroserviceRegistryDO> requiredBusinessPage = mapper.selectPage(
                MicroserviceRegistryDO::getServiceType, "BUSINESS",
                MicroserviceRegistryDO::getIsRequired, true, 1, 10);
        assertThat(requiredBusinessPage.getList()).hasSize(1);
        assertThat(requiredBusinessPage.getTotal()).isEqualTo(1L);
    }

    @Test
    void shouldUpdateById() {
        MicroserviceRegistryDO registry = mapper.selectOne(
                MicroserviceRegistryDO::getServiceName, "geihou-module-system");
        registry.setHealthCheckUrl("/actuator/health/readiness");
        registry.setUpdateTime(LocalDateTime.now());

        assertThat(mapper.updateById(registry)).isEqualTo(1);
        assertThat(mapper.selectById(registry.getId()).getHealthCheckUrl())
                .isEqualTo("/actuator/health/readiness");
    }

    @Test
    void shouldDeleteByFieldWithTableLogic() {
        assertThat(mapper.delete(MicroserviceRegistryDO::getServiceName, "geihou-module-report"))
                .isEqualTo(1);

        Boolean deleted = new JdbcTemplate(dataSource)
                .queryForObject("SELECT deleted FROM microservice_registry WHERE service_name = ?",
                        Boolean.class, "geihou-module-report");
        assertThat(deleted).isTrue();
        assertThat(mapper.selectOne(MicroserviceRegistryDO::getServiceName, "geihou-module-report"))
                .isNull();
        assertThat(mapper.selectList()).hasSize(2);
    }

    private static MicroserviceRegistryDO newRegistry(String serviceName, Integer servicePort,
                                                       Integer subsystemId, String serviceType,
                                                       Boolean isRequired) {
        MicroserviceRegistryDO registry = new MicroserviceRegistryDO();
        registry.setServiceName(serviceName);
        registry.setServicePort(servicePort);
        registry.setSubsystemId(subsystemId);
        registry.setServiceType(serviceType);
        registry.setHealthCheckUrl("/actuator/health");
        registry.setStartupPriority(20);
        registry.setIsRequired(isRequired);
        registry.setCreator("h8");
        registry.setCreateTime(LocalDateTime.now());
        registry.setUpdater("h8");
        registry.setUpdateTime(LocalDateTime.now());
        registry.setDeleted(false);
        return registry;
    }
}
