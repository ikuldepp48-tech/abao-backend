package com.geihou.module.system.dal.mysql.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.module.system.dal.dataobject.auth.AuthRoleDO;
import java.time.LocalDateTime;
import java.util.Collection;
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
 * H2 in-memory smoke test for {@link AuthRoleRepository}.
 *
 * <p>Note: the simplified DDL below omits MySQL generated columns
 * (e.g. {@code active_role_code}) and MySQL type exactness. The authoritative
 * integration test is {@code GeihouAuthRolePermissionReadModelMySqlTest} in
 * {@code geihou-bootstrap}, which uses MySQL8 Testcontainers + Flyway V01_001–V01_007.
 */
@SpringBootTest(
        classes = AuthRoleRepositorySmokeTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:geihou_system_auth_role_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class AuthRoleRepositorySmokeTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({GeihouMyBatisAutoConfiguration.class, AuthRoleRepository.class})
    @MapperScan("com.geihou.module.system.dal.mysql.auth")
    static class TestConfig {
    }

    @Autowired
    private AuthRoleMapper mapper;

    @Autowired
    private AuthRoleRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_role");
        createAuthRoleTable(jdbcTemplate);
        mapper.insert(newRole(1L, "OWNER", "老板", true, true, "ACTIVE", false));
        mapper.insert(newRole(1L, "SHOP_MANAGER", "店长", true, false, "ACTIVE", false));
        mapper.insert(newRole(1L, "CASHIER", "收银员", true, false, "ACTIVE", false));
        mapper.insert(newRole(1L, "DISABLED_ROLE", "已禁用", true, false, "DISABLED", false));
        mapper.insert(newRole(1L, "DELETED_ROLE", "已删除", true, false, "ACTIVE", true));
        mapper.insert(newRole(2L, "OWNER", "另一租户老板", true, true, "ACTIVE", false));
    }

    @Test
    void shouldSelectActiveByTenantIdAndIds() {
        AuthRoleDO owner = mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuthRoleDO>()
                .eq(AuthRoleDO::getTenantId, 1L)
                .eq(AuthRoleDO::getRoleCode, "OWNER")
                .eq(AuthRoleDO::getDeleted, false));
        AuthRoleDO cashier = mapper.selectOne(AuthRoleDO::getRoleCode, "CASHIER");

        List<AuthRoleDO> result = repository.selectActiveByTenantIdAndIds(1L,
                List.of(owner.getId(), cashier.getId()));

        assertThat(result).extracting(AuthRoleDO::getRoleCode)
                .containsExactlyInAnyOrder("OWNER", "CASHIER");
    }

    @Test
    void shouldExcludeDeletedRoles() {
        AuthRoleDO deleted = mapper.selectOne(AuthRoleDO::getRoleCode, "DELETED_ROLE");

        List<AuthRoleDO> result = repository.selectActiveByTenantIdAndIds(1L, List.of(deleted.getId()));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldExcludeDisabledRoles() {
        AuthRoleDO disabled = mapper.selectOne(AuthRoleDO::getRoleCode, "DISABLED_ROLE");

        List<AuthRoleDO> result = repository.selectActiveByTenantIdAndIds(1L, List.of(disabled.getId()));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldExcludeWrongTenantRoles() {
        AuthRoleDO otherTenantOwner = mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuthRoleDO>()
                .eq(AuthRoleDO::getTenantId, 2L)
                .eq(AuthRoleDO::getRoleCode, "OWNER")
                .eq(AuthRoleDO::getDeleted, false));

        List<AuthRoleDO> result = repository.selectActiveByTenantIdAndIds(1L, List.of(otherTenantOwner.getId()));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForNullOrEmptyIds() {
        assertThat(repository.selectActiveByTenantIdAndIds(1L, null)).isEmpty();
        assertThat(repository.selectActiveByTenantIdAndIds(1L, Collections.emptyList())).isEmpty();
    }

    private static void createAuthRoleTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE auth_role (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL DEFAULT 0,
                    role_code VARCHAR(64) NOT NULL,
                    role_name VARCHAR(64) NOT NULL,
                    description VARCHAR(255),
                    is_builtin BOOLEAN NOT NULL DEFAULT FALSE,
                    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
                    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                    creator VARCHAR(64) NOT NULL DEFAULT '',
                    create_time TIMESTAMP NOT NULL,
                    updater VARCHAR(64) NOT NULL DEFAULT '',
                    update_time TIMESTAMP NOT NULL,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
    }

    private static AuthRoleDO newRole(Long tenantId, String roleCode, String roleName,
                                       boolean isBuiltin, boolean isAdmin, String status, boolean deleted) {
        AuthRoleDO role = new AuthRoleDO();
        role.setTenantId(tenantId);
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setDescription("smoke test role");
        role.setIsBuiltin(isBuiltin);
        role.setIsAdmin(isAdmin);
        role.setStatus(status);
        role.setCreator("h146");
        role.setCreateTime(LocalDateTime.parse("2026-06-19T08:00:00"));
        role.setUpdater("h146");
        role.setUpdateTime(LocalDateTime.parse("2026-06-19T08:00:00"));
        role.setDeleted(deleted);
        return role;
    }
}
