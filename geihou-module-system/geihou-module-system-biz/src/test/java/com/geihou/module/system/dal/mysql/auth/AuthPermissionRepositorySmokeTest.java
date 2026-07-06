package com.geihou.module.system.dal.mysql.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.system.dal.dataobject.auth.AuthPermissionDO;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
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

/**
 * H2 in-memory smoke test for {@link AuthPermissionRepository}.
 *
 * <p>Note: the simplified DDL below omits MySQL generated columns
 * (e.g. {@code active_permission_code}) and MySQL type exactness. The authoritative
 * integration test is {@code GeihouAuthRolePermissionReadModelMySqlTest} in
 * {@code geihou-bootstrap}, which uses MySQL8 Testcontainers + Flyway V01_001–V01_007.
 */
@SpringBootTest(
        classes = AuthPermissionRepositorySmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_system_auth_permission_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class AuthPermissionRepositorySmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({GeihouMyBatisAutoConfiguration.class, AuthPermissionRepository.class})
    @MapperScan("com.geihou.module.system.dal.mysql.auth")
    static class TestConfig {
    }

    @Autowired
    private AuthPermissionMapper mapper;

    @Autowired
    private AuthPermissionRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_permission");
        createAuthPermissionTable(jdbcTemplate);
        mapper.insert(newPermission("cart:staff-assisted", "代客下单", 1, "cart", "cart",
                "staff-assisted", "MEDIUM", false));
        mapper.insert(newPermission("ck:dashboard:read", "CK仪表盘", 2, "ck-dashboard", "dashboard",
                "read", "MEDIUM", false));
        mapper.insert(newPermission("deleted:perm:read", "已删除权限", 1, "test", "test",
                "read", "LOW", true));
    }

    @Test
    void shouldSelectActiveByIds() {
        AuthPermissionDO cart = mapper.selectOne(AuthPermissionDO::getPermissionCode, "cart:staff-assisted");
        AuthPermissionDO ck = mapper.selectOne(AuthPermissionDO::getPermissionCode, "ck:dashboard:read");

        List<AuthPermissionDO> result = repository.selectActiveByIds(List.of(cart.getId(), ck.getId()));

        assertThat(result).extracting(AuthPermissionDO::getPermissionCode)
                .containsExactlyInAnyOrder("cart:staff-assisted", "ck:dashboard:read");
    }

    @Test
    void shouldExcludeDeletedPermissions() {
        AuthPermissionDO deleted = mapper.selectOne(AuthPermissionDO::getPermissionCode, "deleted:perm:read");

        List<AuthPermissionDO> result = repository.selectActiveByIds(List.of(deleted.getId()));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForNullOrEmptyIds() {
        assertThat(repository.selectActiveByIds(null)).isEmpty();
        assertThat(repository.selectActiveByIds(Collections.emptyList())).isEmpty();
    }

    private static void createAuthPermissionTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE auth_permission (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    permission_code VARCHAR(128) NOT NULL,
                    permission_name VARCHAR(128) NOT NULL,
                    subsystem_id SMALLINT,
                    module_name VARCHAR(64),
                    resource VARCHAR(64),
                    action VARCHAR(32),
                    risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW',
                    description VARCHAR(255),
                    creator VARCHAR(64) NOT NULL DEFAULT '',
                    create_time TIMESTAMP NOT NULL,
                    updater VARCHAR(64) NOT NULL DEFAULT '',
                    update_time TIMESTAMP NOT NULL,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
    }

    private static AuthPermissionDO newPermission(String code, String name, Integer subsystemId,
                                                   String module, String resource, String action,
                                                   String risk, boolean deleted) {
        AuthPermissionDO permission = new AuthPermissionDO();
        permission.setPermissionCode(code);
        permission.setPermissionName(name);
        permission.setSubsystemId(subsystemId);
        permission.setModuleName(module);
        permission.setResource(resource);
        permission.setAction(action);
        permission.setRiskLevel(risk);
        permission.setDescription("smoke test permission");
        permission.setCreator("h146");
        permission.setCreateTime(LocalDateTime.parse("2026-06-19T08:00:00"));
        permission.setUpdater("h146");
        permission.setUpdateTime(LocalDateTime.parse("2026-06-19T08:00:00"));
        permission.setDeleted(deleted);
        return permission;
    }
}
