package com.geihou.module.system.dal.mysql.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.system.dal.dataobject.auth.AuthRolePermissionDO;
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
 * H2 in-memory smoke test for {@link AuthRolePermissionRepository}.
 *
 * <p>Note: the simplified DDL below omits MySQL generated columns
 * (e.g. {@code active_role_id}/{@code active_permission_id}) and MySQL type exactness.
 * The authoritative integration test is {@code GeihouAuthRolePermissionReadModelMySqlTest}
 * in {@code geihou-bootstrap}, which uses MySQL8 Testcontainers + Flyway V01_001–V01_007.
 */
@SpringBootTest(
        classes = AuthRolePermissionRepositorySmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_system_auth_role_permission_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class AuthRolePermissionRepositorySmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({GeihouMyBatisAutoConfiguration.class, AuthRolePermissionRepository.class})
    @MapperScan("com.geihou.module.system.dal.mysql.auth")
    static class TestConfig {
    }

    @Autowired
    private AuthRolePermissionMapper mapper;

    @Autowired
    private AuthRolePermissionRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_role_permission");
        createAuthRolePermissionTable(jdbcTemplate);
        mapper.insert(newRolePermission(1L, 10L, 100L, false));
        mapper.insert(newRolePermission(1L, 10L, 101L, false));
        mapper.insert(newRolePermission(1L, 11L, 100L, false));
        mapper.insert(newRolePermission(1L, 12L, 100L, true));
        mapper.insert(newRolePermission(2L, 10L, 100L, false));
    }

    @Test
    void shouldSelectActiveByTenantIdAndRoleIds() {
        List<AuthRolePermissionDO> result = repository.selectActiveByTenantIdAndRoleIds(1L, List.of(10L, 11L));

        assertThat(result).extracting(AuthRolePermissionDO::getPermissionId)
                .containsExactlyInAnyOrder(100L, 101L, 100L);
        assertThat(result).hasSize(3);
    }

    @Test
    void shouldExcludeDeletedLinks() {
        List<AuthRolePermissionDO> result = repository.selectActiveByTenantIdAndRoleIds(1L, List.of(12L));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldExcludeWrongTenant() {
        List<AuthRolePermissionDO> result = repository.selectActiveByTenantIdAndRoleIds(1L, List.of(10L));

        assertThat(result).allSatisfy(rp -> assertThat(rp.getTenantId()).isEqualTo(1L));
        assertThat(repository.selectActiveByTenantIdAndRoleIds(2L, List.of(10L)))
                .hasSize(1);
    }

    @Test
    void shouldReturnEmptyForNullOrEmptyIds() {
        assertThat(repository.selectActiveByTenantIdAndRoleIds(1L, null)).isEmpty();
        assertThat(repository.selectActiveByTenantIdAndRoleIds(1L, Collections.emptyList())).isEmpty();
    }

    private static void createAuthRolePermissionTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE auth_role_permission (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL DEFAULT 0,
                    role_id BIGINT NOT NULL,
                    permission_id BIGINT NOT NULL,
                    creator VARCHAR(64) NOT NULL DEFAULT '',
                    create_time TIMESTAMP NOT NULL,
                    updater VARCHAR(64) NOT NULL DEFAULT '',
                    update_time TIMESTAMP NOT NULL,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
    }

    private static AuthRolePermissionDO newRolePermission(Long tenantId, Long roleId, Long permissionId,
                                                           boolean deleted) {
        AuthRolePermissionDO rolePermission = new AuthRolePermissionDO();
        rolePermission.setTenantId(tenantId);
        rolePermission.setRoleId(roleId);
        rolePermission.setPermissionId(permissionId);
        rolePermission.setCreator("h146");
        rolePermission.setCreateTime(LocalDateTime.parse("2026-06-19T08:00:00"));
        rolePermission.setUpdater("h146");
        rolePermission.setUpdateTime(LocalDateTime.parse("2026-06-19T08:00:00"));
        rolePermission.setDeleted(deleted);
        return rolePermission;
    }
}
