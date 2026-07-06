package com.geihou.module.infra.dal.mysql.microservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.geihou.common.pojo.PageResult;
import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.infra.dal.dataobject.microservice.MicroserviceRegistryDO;
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
        classes = MicroserviceRegistryRepositorySmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_infra_microservice_registry_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class MicroserviceRegistryRepositorySmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({GeihouMyBatisAutoConfiguration.class, MicroserviceRegistryRepository.class})
    @MapperScan("com.geihou.module.infra.dal.mysql.microservice")
    static class TestConfig {
    }

    @Autowired
    private MicroserviceRegistryRepository repository;

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
        insertRegistry("geihou-module-gateway", 48080, null, "GATEWAY", 1, true, false);
        insertRegistry("geihou-module-system", 48092, null, "COMMON", 10, true, false);
        insertRegistry("geihou-module-infra", 48093, null, "COMMON", 10, true, false);
        insertRegistry("geihou-module-finance", 48081, 1, "BUSINESS", 20, true, false);
        insertRegistry("geihou-module-supplychain", 48082, 2, "BUSINESS", 20, true, false);
        insertRegistry("geihou-module-obsolete", 48999, 9, "BUSINESS", 50, false, true);
    }

    @Test
    void shouldRegisterPaginationInterceptor() {
        assertThat(mybatisPlusInterceptor.getInterceptors())
                .singleElement()
                .isInstanceOfSatisfying(PaginationInnerInterceptor.class, pagination ->
                        assertThat(pagination.getDbType()).isEqualTo(DbType.MYSQL));
    }

    @Test
    void shouldSelectById() {
        MicroserviceRegistryDO gateway = repository.selectByServiceName("geihou-module-gateway");

        assertThat(repository.selectById(gateway.getId()).getServicePort()).isEqualTo(48080);
        assertThat(repository.selectById(9999L)).isNull();
    }

    @Test
    void shouldSelectByServiceName() {
        MicroserviceRegistryDO system = repository.selectByServiceName("geihou-module-system");

        assertThat(system).isNotNull();
        assertThat(system.getServiceType()).isEqualTo("COMMON");
        assertThat(repository.selectByServiceName("geihou-module-missing")).isNull();
    }

    @Test
    void shouldSelectListOrderedAndHideLogicDeletedRows() {
        assertThat(repository.selectList())
                .extracting(MicroserviceRegistryDO::getServiceName)
                .containsExactly(
                        "geihou-module-gateway",
                        "geihou-module-system",
                        "geihou-module-infra",
                        "geihou-module-finance",
                        "geihou-module-supplychain");
    }

    @Test
    void shouldSelectPagesWithFiltersAndDefaultOrder() {
        PageResult<MicroserviceRegistryDO> commonPage = repository.selectPage(1, 10,
                new LambdaQueryWrapper<MicroserviceRegistryDO>()
                        .eq(MicroserviceRegistryDO::getServiceType, "COMMON"));
        assertThat(commonPage.getTotal()).isEqualTo(2L);
        assertThat(commonPage.getList())
                .extracting(MicroserviceRegistryDO::getServiceName)
                .containsExactly("geihou-module-system", "geihou-module-infra");

        PageResult<MicroserviceRegistryDO> subsystemPage = repository.selectPage(1, 10,
                new LambdaQueryWrapper<MicroserviceRegistryDO>()
                        .eq(MicroserviceRegistryDO::getSubsystemId, 1)
                        .eq(MicroserviceRegistryDO::getIsRequired, true));
        assertThat(subsystemPage.getTotal()).isEqualTo(1L);
        assertThat(subsystemPage.getList())
                .extracting(MicroserviceRegistryDO::getServiceName)
                .containsExactly("geihou-module-finance");
    }

    private void insertRegistry(String serviceName, Integer servicePort, Integer subsystemId,
                                String serviceType, Integer startupPriority, Boolean isRequired,
                                Boolean deleted) {
        new JdbcTemplate(dataSource).update("""
                INSERT INTO microservice_registry (service_name, service_port, subsystem_id, service_type,
                    health_check_url, startup_priority, is_required, creator, updater, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, serviceName, servicePort, subsystemId, serviceType, "/actuator/health",
                startupPriority, isRequired, "system", "system", deleted);
    }
}
