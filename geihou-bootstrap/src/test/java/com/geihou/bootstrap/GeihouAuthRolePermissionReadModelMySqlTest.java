package com.geihou.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geihou.module.system.dal.dataobject.auth.AuthPermissionDO;
import com.geihou.module.system.dal.dataobject.auth.AuthRoleDO;
import com.geihou.module.system.dal.dataobject.auth.AuthRolePermissionDO;
import com.geihou.module.system.dal.dataobject.auth.AuthUserRoleDO;
import com.geihou.module.system.dal.mysql.auth.AuthPermissionRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRolePermissionRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRoleRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRoleRepository;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import com.geihou.module.system.service.auth.GeihouAuthRolePermissionReadService;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        classes = GeihouAuthRolePermissionReadModelMySqlTest.TestConfig.class,
        properties = {
                "spring.flyway.locations=classpath:db/migration",
                "spring.flyway.clean-disabled=true",
                "spring.flyway.baseline-on-migrate=false",
                "spring.main.web-application-type=none"
        }
)
class GeihouAuthRolePermissionReadModelMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("geihou_h146")
            .withUsername("geihou")
            .withPassword("geihou");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Configuration
    @EnableAutoConfiguration
    @MapperScan("com.geihou.module.system.dal.mysql.auth")
    @Import({
            AuthRoleRepository.class,
            AuthUserRoleRepository.class,
            AuthPermissionRepository.class,
            AuthRolePermissionRepository.class,
            GeihouAuthRolePermissionReadService.class
    })
    static class TestConfig {
    }

    @Autowired
    private GeihouAuthRolePermissionReadService readService;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        // Clean up any previous test fixture data (tenant1 + platform test users)
        cleanupTestFixtures();
        // Insert tenant1 cloned fixture rows after migration (no SQL modification)
        insertTenant1Fixtures();
        insertPlatformTestFixtures();
        insertEdgeCaseFixtures();
    }

    // ---- T-01: All identity mappings ----

    @Test
    void shouldResolveRoleCodesForAllIdentityFamilies() {
        // CUSTOMER
        assertThat(readService.resolveActiveRoleCodes(1L, 1006L, GeihouAccessTokenUserRole.CUSTOMER))
                .containsExactly("CUSTOMER");
        // STORE_STAFF (CASHIER + WAITER + KITCHEN_COOK)
        assertThat(readService.resolveActiveRoleCodes(1L, 1002L, GeihouAccessTokenUserRole.STORE_STAFF))
                .containsExactly("CASHIER", "KITCHEN_COOK", "WAITER");
        // CK_WORKER
        assertThat(readService.resolveActiveRoleCodes(1L, 1003L, GeihouAccessTokenUserRole.CK_WORKER))
                .containsExactly("CK_WORKER");
        // STORE_MANAGER
        assertThat(readService.resolveActiveRoleCodes(1L, 1004L, GeihouAccessTokenUserRole.STORE_MANAGER))
                .containsExactly("SHOP_MANAGER");
        // CK_MANAGER
        assertThat(readService.resolveActiveRoleCodes(1L, 1005L, GeihouAccessTokenUserRole.CK_MANAGER))
                .containsExactly("CK_MANAGER");
        // OWNER
        assertThat(readService.resolveActiveRoleCodes(1L, 1001L, GeihouAccessTokenUserRole.OWNER))
                .containsExactly("OWNER");
        // CONSULTANT (platform scope, tenantId=0)
        assertThat(readService.resolveActiveRoleCodes(0L, 2001L, GeihouAccessTokenUserRole.CONSULTANT))
                .containsExactly("CONSULTANT");
        // PLATFORM_ADMIN (platform scope, tenantId=0)
        assertThat(readService.resolveActiveRoleCodes(0L, 2002L, GeihouAccessTokenUserRole.PLATFORM_ADMIN))
                .containsExactly("PLATFORM_DEVELOPER", "PLATFORM_OPERATOR");
    }

    // ---- T-02: tenant0 template confusion ----

    @Test
    void tenant1UserMustNotReturnTenant0TemplateRoles() {
        // User 1010 has a user_role pointing to a tenant0 role_id (template)
        // The role repository filters by tenant_id=1, so tenant0 role should not be returned
        List<String> codes = readService.resolveActiveRoleCodes(1L, 1010L, GeihouAccessTokenUserRole.OWNER);
        assertThat(codes).isEmpty();
    }

    // ---- T-03: tenant mismatch at each chain step ----

    @Test
    void tenantMismatchInUserRoleShouldReturnEmpty() {
        // User 1001 exists in tenant1, but querying with tenant2
        assertThat(readService.resolveActiveRoleCodes(2L, 1001L, GeihouAccessTokenUserRole.OWNER))
                .isEmpty();
    }

    @Test
    void tenantMismatchInRoleShouldReturnEmpty() {
        // User 1011 has a user_role in tenant1 pointing to a role that exists only in tenant2
        assertThat(readService.resolveActiveRoleCodes(1L, 1011L, GeihouAccessTokenUserRole.OWNER))
                .isEmpty();
    }

    // ---- T-04: disabled/deleted role ----

    @Test
    void disabledRoleMustNotBeReturned() {
        // User 1012 has a user_role pointing to a DISABLED role in tenant1
        assertThat(readService.resolveActiveRoleCodes(1L, 1012L, GeihouAccessTokenUserRole.OWNER))
                .isEmpty();
    }

    @Test
    void deletedRoleMustNotBeReturned() {
        // User 1013 has a user_role pointing to a deleted role in tenant1
        assertThat(readService.resolveActiveRoleCodes(1L, 1013L, GeihouAccessTokenUserRole.OWNER))
                .isEmpty();
    }

    // ---- T-05: deleted links ----

    @Test
    void deletedUserRoleMustBeExcluded() {
        // User 1014 has a deleted user_role in tenant1
        assertThat(readService.resolveActiveRoleCodes(1L, 1014L, GeihouAccessTokenUserRole.OWNER))
                .isEmpty();
    }

    @Test
    void deletedRolePermissionMustBeExcluded() {
        // User 1015 has a role with a deleted role_permission link
        List<String> perms = readService.resolveAssignedPermissionCodes(1L, 1015L,
                GeihouAccessTokenUserRole.OWNER);
        assertThat(perms).isEmpty();
    }

    // ---- T-06: deleted permissions ----

    @Test
    void deletedPermissionMustBeExcluded() {
        // User 1016 has a role linked to a deleted permission
        List<String> perms = readService.resolveAssignedPermissionCodes(1L, 1016L,
                GeihouAccessTokenUserRole.OWNER);
        assertThat(perms).isEmpty();
    }

    // ---- T-07: no tenant0 fallback ----

    @Test
    void tenant1UserMustNeverReturnTenant0RolesOrPermissions() {
        // User 1001 is a tenant1 OWNER; tenant0 has OWNER template role with permissions
        // But tenant1 OWNER should only get tenant1 role_permission, not tenant0
        List<String> roleCodes = readService.resolveActiveRoleCodes(1L, 1001L,
                GeihouAccessTokenUserRole.OWNER);
        assertThat(roleCodes).containsExactly("OWNER");

        // Permissions should come from tenant1 role_permission, not tenant0
        List<String> perms = readService.resolveAssignedPermissionCodes(1L, 1001L,
                GeihouAccessTokenUserRole.OWNER);
        // tenant1 OWNER has cloned permissions - verify they are present
        assertThat(perms).isNotEmpty();
        // Verify no tenant0-only artifact: tenant0 seeds are at tenant_id=0, tenant1 at tenant_id=1
    }

    // ---- T-08: wrong tenant role_permission ----

    @Test
    void wrongTenantRolePermissionMustNotReturn() {
        // User 1017 has a tenant1 role, but the role_permission is at tenant0
        // The role_permission repository filters by tenant_id=1, so tenant0 links are excluded
        List<String> perms = readService.resolveAssignedPermissionCodes(1L, 1017L,
                GeihouAccessTokenUserRole.OWNER);
        assertThat(perms).isEmpty();
    }

    // ---- T-09: stable/dedup/immutable ----

    @Test
    void resultsMustBeSortedDedupedAndImmutable() {
        // User 1001 has a single OWNER role
        List<String> roleCodes = readService.resolveActiveRoleCodes(1L, 1001L,
                GeihouAccessTokenUserRole.OWNER);
        assertThat(roleCodes).isSorted();
        assertThatThrownBy(() -> roleCodes.add("HACK")).isInstanceOf(UnsupportedOperationException.class);

        List<String> perms = readService.resolveAssignedPermissionCodes(1L, 1001L,
                GeihouAccessTokenUserRole.OWNER);
        assertThat(perms).isSorted();
        assertThat(perms).doesNotHaveDuplicates();
        assertThatThrownBy(() -> perms.add("HACK")).isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- T-10: empty ----

    @Test
    void userWithNoRolesMustReturnEmpty() {
        assertThat(readService.resolveActiveRoleCodes(1L, 9999L, GeihouAccessTokenUserRole.OWNER))
                .isEmpty();
        assertThat(readService.resolveAssignedPermissionCodes(1L, 9999L, GeihouAccessTokenUserRole.OWNER))
                .isEmpty();
    }

    @Test
    void userWithRolesButNoPermissionsMustReturnEmptyPermissions() {
        // User 1006 is CUSTOMER, which has 0 permission grants
        List<String> perms = readService.resolveAssignedPermissionCodes(1L, 1006L,
                GeihouAccessTokenUserRole.CUSTOMER);
        assertThat(perms).isEmpty();
    }

    // ---- T-11: consultant static finance present (exact, no superset-only check) ----

    @Test
    void consultantAssignedPermissionsMustBeExactlyTheEightSeedCodes() {
        List<String> perms = readService.resolveAssignedPermissionCodes(0L, 2001L,
                GeihouAccessTokenUserRole.CONSULTANT);
        // Exactly the 8 CONSULTANT seed codes from V01_007, sorted and deduped.
        assertThat(perms).containsExactly(
                "consultant:benchmark:read",
                "consultant:case:read",
                "consultant:client:read",
                "consultant:dashboard:read",
                "consultant:diagnosis:read",
                "consultant:methodology:read",
                "consultant:strategy:write",
                "finance:core-profit:read"
        );
    }

    // ---- I-05: OWNER tenant1 permissions exactly the 14 seed codes ----

    @Test
    void ownerTenant1AssignedPermissionsMustBeExactlyTheFourteenSeedCodes() {
        List<String> perms = readService.resolveAssignedPermissionCodes(1L, 1001L,
                GeihouAccessTokenUserRole.OWNER);
        // Exactly the 14 OWNER seed codes from V01_007 (cloned to tenant1), sorted and deduped.
        assertThat(perms).containsExactly(
                "bom:read",
                "cart:staff-assisted",
                "ck:dashboard:read",
                "ck:material:read",
                "ck:procurement:read",
                "ck:settings:write",
                "cost:read",
                "decision:read",
                "delivery:write",
                "employee:read",
                "finance:core-profit:read",
                "iot:read",
                "production:write",
                "supplier:read"
        );
    }

    // ---- Invalid inputs ----

    @Test
    void invalidInputsMustReturnEmpty() {
        assertThat(readService.resolveActiveRoleCodes(null, 1001L, GeihouAccessTokenUserRole.OWNER))
                .isEmpty();
        assertThat(readService.resolveActiveRoleCodes(1L, null, GeihouAccessTokenUserRole.OWNER))
                .isEmpty();
        assertThat(readService.resolveActiveRoleCodes(1L, 0L, GeihouAccessTokenUserRole.OWNER))
                .isEmpty();
        assertThat(readService.resolveActiveRoleCodes(1L, -1L, GeihouAccessTokenUserRole.OWNER))
                .isEmpty();
        assertThat(readService.resolveActiveRoleCodes(0L, 1001L, GeihouAccessTokenUserRole.OWNER))
                .isEmpty(); // tenant scope mismatch
        assertThat(readService.resolveAssignedPermissionCodes(null, 1001L, GeihouAccessTokenUserRole.OWNER))
                .isEmpty();
    }

    // ---- Fixture insertion helpers ----

    private void insertTenant1Fixtures() {
        // Clone 8 tenant-template roles to tenant_id=1
        jdbc.update("""
                INSERT INTO auth_role (tenant_id, role_code, role_name, description, is_builtin, is_admin, status,
                    creator, create_time, updater, update_time, deleted)
                SELECT 1, role_code, role_name, description, is_builtin, is_admin, status,
                    'h146', NOW(), 'h146', NOW(), 0
                FROM auth_role
                WHERE tenant_id = 0 AND deleted = 0
                    AND role_code IN ('OWNER','SHOP_MANAGER','CK_MANAGER','CASHIER','WAITER',
                        'KITCHEN_COOK','CK_WORKER','CUSTOMER')
                """);

        // Clone role_permissions from tenant0 to tenant1 with new role_ids
        jdbc.update("""
                INSERT INTO auth_role_permission (tenant_id, role_id, permission_id,
                    creator, create_time, updater, update_time, deleted)
                SELECT 1, t1.id, arp.permission_id, 'h146', NOW(), 'h146', NOW(), 0
                FROM auth_role_permission arp
                JOIN auth_role t0 ON t0.id = arp.role_id AND t0.tenant_id = 0 AND t0.deleted = 0
                JOIN auth_role t1 ON t1.role_code = t0.role_code AND t1.tenant_id = 1 AND t1.deleted = 0
                WHERE arp.tenant_id = 0 AND arp.deleted = 0
                """);

        // Insert user_role rows for tenant1 users
        // User 1001 -> OWNER
        linkUserRole(1L, 1001L, resolveTenantRoleId(1L, "OWNER"));
        // User 1002 -> CASHIER, WAITER, KITCHEN_COOK (STORE_STAFF)
        linkUserRole(1L, 1002L, resolveTenantRoleId(1L, "CASHIER"));
        linkUserRole(1L, 1002L, resolveTenantRoleId(1L, "WAITER"));
        linkUserRole(1L, 1002L, resolveTenantRoleId(1L, "KITCHEN_COOK"));
        // User 1003 -> CK_WORKER
        linkUserRole(1L, 1003L, resolveTenantRoleId(1L, "CK_WORKER"));
        // User 1004 -> SHOP_MANAGER
        linkUserRole(1L, 1004L, resolveTenantRoleId(1L, "SHOP_MANAGER"));
        // User 1005 -> CK_MANAGER
        linkUserRole(1L, 1005L, resolveTenantRoleId(1L, "CK_MANAGER"));
        // User 1006 -> CUSTOMER
        linkUserRole(1L, 1006L, resolveTenantRoleId(1L, "CUSTOMER"));
    }

    private void insertPlatformTestFixtures() {
        // Platform users (tenant0) for CONSULTANT and PLATFORM_ADMIN
        long consultantRoleId = resolveTenantRoleId(0L, "CONSULTANT");
        long platformOperatorRoleId = resolveTenantRoleId(0L, "PLATFORM_OPERATOR");
        long platformDeveloperRoleId = resolveTenantRoleId(0L, "PLATFORM_DEVELOPER");

        linkUserRole(0L, 2001L, consultantRoleId);
        linkUserRole(0L, 2002L, platformOperatorRoleId);
        linkUserRole(0L, 2002L, platformDeveloperRoleId);
    }

    private void insertEdgeCaseFixtures() {
        // T-02: User 1010 has user_role pointing to tenant0 OWNER template role_id
        long tenant0OwnerId = resolveTenantRoleId(0L, "OWNER");
        linkUserRole(1L, 1010L, tenant0OwnerId);

        // T-03: User 1011 has user_role in tenant1 pointing to a role that only exists in tenant2
        // Insert a tenant2 role
        jdbc.update("""
                INSERT INTO auth_role (tenant_id, role_code, role_name, description, is_builtin, is_admin, status,
                    creator, create_time, updater, update_time, deleted)
                VALUES (2, 'OWNER', '租户2老板', 'test', 1, 1, 'ACTIVE', 'h146', NOW(), 'h146', NOW(), 0)
                """);
        long tenant2OwnerId = resolveTenantRoleId(2L, "OWNER");
        linkUserRole(1L, 1011L, tenant2OwnerId);

        // T-04: User 1012 has user_role pointing to a DISABLED role in tenant1
        jdbc.update("""
                INSERT INTO auth_role (tenant_id, role_code, role_name, description, is_builtin, is_admin, status,
                    creator, create_time, updater, update_time, deleted)
                VALUES (1, 'DISABLED_OWNER', '禁用老板', 'test', 0, 1, 'DISABLED', 'h146', NOW(), 'h146', NOW(), 0)
                """);
        long disabledRoleId = resolveRoleIdByCode(1L, "DISABLED_OWNER");
        linkUserRole(1L, 1012L, disabledRoleId);

        // T-04: User 1013 has user_role pointing to a deleted role in tenant1
        jdbc.update("""
                INSERT INTO auth_role (tenant_id, role_code, role_name, description, is_builtin, is_admin, status,
                    creator, create_time, updater, update_time, deleted)
                VALUES (1, 'DELETED_OWNER', '删除老板', 'test', 0, 1, 'ACTIVE', 'h146', NOW(), 'h146', NOW(), 1)
                """);
        // Can't use linkUserRole for deleted role because role_code exists but deleted
        // Need to insert user_role directly with the role_id
        long deletedRoleId = findDeletedRoleId(1L, "DELETED_OWNER");
        jdbc.update("INSERT INTO auth_user_role (tenant_id, user_id, role_id, creator, create_time, updater, update_time, deleted) "
                + "VALUES (1, 1013, " + deletedRoleId + ", 'h146', NOW(), 'h146', NOW(), 0)");

        // T-05: User 1014 has a deleted user_role
        long tenant1OwnerId = resolveTenantRoleId(1L, "OWNER");
        jdbc.update("INSERT INTO auth_user_role (tenant_id, user_id, role_id, creator, create_time, updater, update_time, deleted) "
                + "VALUES (1, 1014, " + tenant1OwnerId + ", 'h146', NOW(), 'h146', NOW(), 1)");

        // T-05: User 1015 has a role with a deleted role_permission link
        // Create a special role for this user
        jdbc.update("""
                INSERT INTO auth_role (tenant_id, role_code, role_name, description, is_builtin, is_admin, status,
                    creator, create_time, updater, update_time, deleted)
                VALUES (1, 'OWNER_DELETED_PERM', '删除权限老板', 'test', 0, 1, 'ACTIVE', 'h146', NOW(), 'h146', NOW(), 0)
                """);
        long ownerDeletedPermRoleId = resolveRoleIdByCode(1L, "OWNER_DELETED_PERM");
        linkUserRole(1L, 1015L, ownerDeletedPermRoleId);
        // Insert a deleted role_permission for this role
        long financePermissionId = resolvePermissionId("finance:core-profit:read");
        jdbc.update("INSERT INTO auth_role_permission (tenant_id, role_id, permission_id, creator, create_time, updater, update_time, deleted) "
                + "VALUES (1, " + ownerDeletedPermRoleId + ", " + financePermissionId + ", 'h146', NOW(), 'h146', NOW(), 1)");

        // T-06: User 1016 has a role linked to a deleted permission
        jdbc.update("""
                INSERT INTO auth_role (tenant_id, role_code, role_name, description, is_builtin, is_admin, status,
                    creator, create_time, updater, update_time, deleted)
                VALUES (1, 'OWNER_DELETED_PERM_ROW', '删除权限行老板', 'test', 0, 1, 'ACTIVE', 'h146', NOW(), 'h146', NOW(), 0)
                """);
        long ownerDeletedPermRowRoleId = resolveRoleIdByCode(1L, "OWNER_DELETED_PERM_ROW");
        linkUserRole(1L, 1016L, ownerDeletedPermRowRoleId);
        // Insert a deleted permission and link it
        jdbc.update("""
                INSERT INTO auth_permission (permission_code, permission_name, subsystem_id, module_name,
                    resource, action, risk_level, description, creator, create_time, updater, update_time, deleted)
                VALUES ('test:deleted:read', 'Test Deleted', 1, 'test', 'test', 'read', 'LOW',
                    'test', 'h146', NOW(), 'h146', NOW(), 1)
                """);
        long deletedPermId = findDeletedPermissionId("test:deleted:read");
        jdbc.update("INSERT INTO auth_role_permission (tenant_id, role_id, permission_id, creator, create_time, updater, update_time, deleted) "
                + "VALUES (1, " + ownerDeletedPermRowRoleId + ", " + deletedPermId + ", 'h146', NOW(), 'h146', NOW(), 0)");

        // T-08: User 1017 has a tenant1 role, but the role_permission is at tenant0 (wrong tenant)
        // Create a tenant1-only role (no role_permission at tenant1)
        jdbc.update("""
                INSERT INTO auth_role (tenant_id, role_code, role_name, description, is_builtin, is_admin, status,
                    creator, create_time, updater, update_time, deleted)
                VALUES (1, 'OWNER_NO_PERM', '无权限老板', 'test', 0, 1, 'ACTIVE', 'h146', NOW(), 'h146', NOW(), 0)
                """);
        long ownerNoPermRoleId = resolveRoleIdByCode(1L, "OWNER_NO_PERM");
        linkUserRole(1L, 1017L, ownerNoPermRoleId);
        // The tenant0 role_permission for OWNER exists but tenant1 role_permission does not
        // (because we only cloned for the 8 standard roles, not this custom one)
    }

    private void linkUserRole(Long tenantId, Long userId, Long roleId) {
        jdbc.update("INSERT INTO auth_user_role (tenant_id, user_id, role_id, creator, create_time, updater, update_time, deleted) "
                + "VALUES (?, ?, ?, 'h146', NOW(), 'h146', NOW(), 0)", tenantId, userId, roleId);
    }

    private long resolveTenantRoleId(Long tenantId, String roleCode) {
        return jdbc.queryForObject(
                "SELECT id FROM auth_role WHERE tenant_id = ? AND role_code = ? AND deleted = 0",
                Long.class, tenantId, roleCode);
    }

    private long resolveRoleIdByCode(Long tenantId, String roleCode) {
        return jdbc.queryForObject(
                "SELECT id FROM auth_role WHERE tenant_id = ? AND role_code = ?",
                Long.class, tenantId, roleCode);
    }

    private long findDeletedRoleId(Long tenantId, String roleCode) {
        return jdbc.queryForObject(
                "SELECT id FROM auth_role WHERE tenant_id = ? AND role_code = ? AND deleted = 1",
                Long.class, tenantId, roleCode);
    }

    private long resolvePermissionId(String permissionCode) {
        return jdbc.queryForObject(
                "SELECT id FROM auth_permission WHERE permission_code = ? AND deleted = 0",
                Long.class, permissionCode);
    }

    private long findDeletedPermissionId(String permissionCode) {
        return jdbc.queryForObject(
                "SELECT id FROM auth_permission WHERE permission_code = ? AND deleted = 1",
                Long.class, permissionCode);
    }

    private void cleanupTestFixtures() {
        // Clean up test-specific data (don't touch migration seed data)
        jdbc.update("DELETE FROM auth_user_role WHERE tenant_id IN (1, 2) OR user_id >= 1000");
        jdbc.update("DELETE FROM auth_role_permission WHERE tenant_id = 1 AND creator = 'h146'");
        jdbc.update("DELETE FROM auth_role WHERE tenant_id IN (1, 2) AND creator = 'h146'");
        jdbc.update("DELETE FROM auth_permission WHERE creator = 'h146'");
    }
}
