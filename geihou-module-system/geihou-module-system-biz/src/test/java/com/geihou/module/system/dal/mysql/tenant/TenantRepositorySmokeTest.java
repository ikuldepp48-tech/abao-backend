package com.geihou.module.system.dal.mysql.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
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
        classes = TenantRepositorySmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_tenant_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class TenantRepositorySmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({GeihouMyBatisAutoConfiguration.class, TenantRepository.class})
    @MapperScan("com.geihou.module.system.dal.mysql.tenant")
    static class TestConfig {
    }

    @Autowired
    private TenantRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS tenants");
        jdbcTemplate.execute("""
                CREATE TABLE tenants (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tenant_code VARCHAR(32) NOT NULL,
                    tenant_name VARCHAR(128) NOT NULL,
                    merchant_type VARCHAR(8) NOT NULL DEFAULT 'B',
                    status VARCHAR(20) NOT NULL DEFAULT 'TRIAL',
                    life_stage VARCHAR(20) NOT NULL DEFAULT 'STARTUP',
                    contract_start_date DATE,
                    contract_end_date DATE,
                    business_day_cutoff_hour TINYINT NOT NULL DEFAULT 3,
                    timezone VARCHAR(32) NOT NULL DEFAULT 'Asia/Shanghai',
                    contact_name VARCHAR(64),
                    contact_phone VARCHAR(32),
                    creator VARCHAR(64) NOT NULL DEFAULT '',
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updater VARCHAR(64) NOT NULL DEFAULT '',
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_code, tenant_name, status, creator, updater, deleted)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "abao", "Abao", "ACTIVE", "test", "test", false);
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_code, tenant_name, status, creator, updater, deleted)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "suspended", "Suspended", "SUSPENDED", "test", "test", false);
        jdbcTemplate.update("""
                INSERT INTO tenants (tenant_code, tenant_name, status, creator, updater, deleted)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "deleted", "Deleted", "ACTIVE", "test", "test", true);
    }

    @Test
    void shouldSelectOnlyActiveNonDeletedTenantByCode() {
        assertThat(repository.selectActiveByTenantCode(" abao ").getTenantName()).isEqualTo("Abao");
        assertThat(repository.selectActiveByTenantCode("suspended")).isNull();
        assertThat(repository.selectActiveByTenantCode("deleted")).isNull();
        assertThat(repository.selectActiveByTenantCode(" ")).isNull();
    }
}
