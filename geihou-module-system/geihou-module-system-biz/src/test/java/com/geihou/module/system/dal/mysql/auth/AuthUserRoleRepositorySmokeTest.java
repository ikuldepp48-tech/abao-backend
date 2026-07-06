package com.geihou.module.system.dal.mysql.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.system.dal.dataobject.auth.AuthUserRoleDO;
import java.time.LocalDateTime;
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
 * H2 in-memory smoke test for {@link AuthUserRoleRepository}.
 *
 * <p>Note: the simplified DDL below omits MySQL generated columns
 * (e.g. {@code active_user_id}/{@code active_role_id}) and MySQL type exactness.
 * The authoritative integration test is {@code GeihouAuthRolePermissionReadModelMySqlTest}
 * in {@code geihou-bootstrap}, which uses MySQL8 Testcontainers + Flyway V01_001–V01_007.
 */
@SpringBootTest(
        classes = AuthUserRoleRepositorySmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_system_auth_user_role_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class AuthUserRoleRepositorySmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({GeihouMyBatisAutoConfiguration.class, AuthUserRoleRepository.class})
    @MapperScan("com.geihou.module.system.dal.mysql.auth")
    static class TestConfig {
    }

    @Autowired
    private AuthUserRoleMapper mapper;

    @Autowired
    private AuthUserRoleRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_user_role");
        createAuthUserRoleTable(jdbcTemplate);
        mapper.insert(newUserRole(1L, 1001L, 10L, false));
        mapper.insert(newUserRole(1L, 1001L, 11L, false));
        mapper.insert(newUserRole(1L, 1002L, 10L, false));
        mapper.insert(newUserRole(1L, 1003L, 12L, true));
        mapper.insert(newUserRole(2L, 1001L, 10L, false));
    }

    @Test
    void shouldSelectActiveByTenantIdAndUserId() {
        List<AuthUserRoleDO> result = repository.selectActiveByTenantIdAndUserId(1L, 1001L);

        assertThat(result).extracting(AuthUserRoleDO::getRoleId)
                .containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void shouldExcludeDeletedLinks() {
        List<AuthUserRoleDO> result = repository.selectActiveByTenantIdAndUserId(1L, 1003L);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldExcludeWrongTenant() {
        List<AuthUserRoleDO> result = repository.selectActiveByTenantIdAndUserId(2L, 1001L);

        assertThat(result).extracting(AuthUserRoleDO::getRoleId)
                .containsExactly(10L);
    }

    @Test
    void shouldReturnEmptyForNoMatch() {
        assertThat(repository.selectActiveByTenantIdAndUserId(1L, 9999L)).isEmpty();
    }

    private static void createAuthUserRoleTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE auth_user_role (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    role_id BIGINT NOT NULL,
                    creator VARCHAR(64) NOT NULL DEFAULT '',
                    create_time TIMESTAMP NOT NULL,
                    updater VARCHAR(64) NOT NULL DEFAULT '',
                    update_time TIMESTAMP NOT NULL,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
    }

    private static AuthUserRoleDO newUserRole(Long tenantId, Long userId, Long roleId, boolean deleted) {
        AuthUserRoleDO userRole = new AuthUserRoleDO();
        userRole.setTenantId(tenantId);
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRole.setCreator("h146");
        userRole.setCreateTime(LocalDateTime.parse("2026-06-19T08:00:00"));
        userRole.setUpdater("h146");
        userRole.setUpdateTime(LocalDateTime.parse("2026-06-19T08:00:00"));
        userRole.setDeleted(deleted);
        return userRole;
    }
}
