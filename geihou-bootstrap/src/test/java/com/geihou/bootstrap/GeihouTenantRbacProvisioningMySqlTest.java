package com.geihou.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geihou.module.system.dal.mysql.auth.AuthRbacProvisioningAuditRepository;
import com.geihou.module.system.dal.mysql.auth.GeihouTenantRbacProvisioningRepository;
import com.geihou.module.system.service.auth.GeihouTenantRbacProvisioningService;
import com.geihou.module.system.service.auth.GeihouTenantRbacProvisioningService.ProvisioningResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real MySQL8 Testcontainers integration test for tenant RBAC clone provisioning.
 * Wires real provisioning repository/service against a fresh Flyway-migrated MySQL8 instance.
 */
@Testcontainers
@SpringBootTest(
        classes = GeihouTenantRbacProvisioningMySqlTest.TestConfig.class,
        properties = {
                "spring.flyway.locations=classpath:db/migration",
                "spring.flyway.clean-disabled=true",
                "spring.flyway.baseline-on-migrate=false",
                "spring.main.web-application-type=none"
        }
)
class GeihouTenantRbacProvisioningMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("geihou_h148")
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
            GeihouTenantRbacProvisioningRepository.class,
            AuthRbacProvisioningAuditRepository.class,
            GeihouTenantRbacProvisioningService.class
    })
    static class TestConfig {
    }

    @Autowired
    private GeihouTenantRbacProvisioningService service;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    private static final String[] CK_PERMISSION_CODES = {
            "ck:dashboard:read", "ck:procurement:read", "ck:material:read", "supplier:read",
            "bom:read", "production:write", "cost:read", "employee:read", "delivery:write",
            "iot:read", "decision:read", "ck:settings:write"
    };

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        // Clean up any previous test data (don't touch migration seeds)
        cleanupTestData();
    }

    // ---- T-03: A type 8 roles / 32 grants ----

    @Test
    void shouldProvisionTypeATenantWith8RolesAnd32Grants() {
        // Insert A-type test tenant
        insertTestTenant(2L, "test-a", "A");

        ProvisioningResult result = service.ensureTenantRbac(2L, "h148-test");

        assertThat(result.outcome()).isEqualTo("SUCCEEDED");
        assertThat(result.merchantType()).isEqualTo("A");
        assertThat(result.rolesExpected()).isEqualTo(8);
        assertThat(result.rolesInserted()).isEqualTo(8);
        assertThat(result.rolesSkipped()).isEqualTo(0);
        assertThat(result.grantsExpected()).isEqualTo(32);
        assertThat(result.grantsInserted()).isEqualTo(32);
        assertThat(result.grantsSkipped()).isEqualTo(0);

        // Verify DB counts
        assertCount("SELECT COUNT(*) FROM auth_role WHERE tenant_id = 2 AND deleted = 0", 8);
        assertCount("SELECT COUNT(*) FROM auth_role_permission WHERE tenant_id = 2 AND deleted = 0", 32);

        // Verify role codes
        List<String> roleCodes = jdbc.queryForList(
                "SELECT DISTINCT role_code FROM auth_role WHERE tenant_id = 2 AND deleted = 0 ORDER BY role_code",
                String.class);
        assertThat(roleCodes).containsExactly(
                "CASHIER", "CK_MANAGER", "CK_WORKER", "CUSTOMER",
                "KITCHEN_COOK", "OWNER", "SHOP_MANAGER", "WAITER");
    }

    // ---- T-04/T-05: B type 6 roles / 4 grants, OWNER=2, no CK permissions ----

    @Test
    void shouldProvisionTypeBTenantWith6RolesAnd4GrantsOwnerHas2() {
        // Abao tenant (tenant_id=1, B) already exists from migration
        ProvisioningResult result = service.ensureTenantRbac(1L, "h148-test");

        assertThat(result.outcome()).isEqualTo("SUCCEEDED");
        assertThat(result.merchantType()).isEqualTo("B");
        assertThat(result.rolesExpected()).isEqualTo(6);
        assertThat(result.rolesInserted()).isEqualTo(6);
        assertThat(result.rolesSkipped()).isEqualTo(0);
        assertThat(result.grantsExpected()).isEqualTo(4);
        assertThat(result.grantsInserted()).isEqualTo(4);
        assertThat(result.grantsSkipped()).isEqualTo(0);

        // Verify DB counts
        assertCount("SELECT COUNT(*) FROM auth_role WHERE tenant_id = 1 AND deleted = 0", 6);
        assertCount("SELECT COUNT(*) FROM auth_role_permission WHERE tenant_id = 1 AND deleted = 0", 4);

        // Verify no CK roles
        List<String> roleCodes = jdbc.queryForList(
                "SELECT DISTINCT role_code FROM auth_role WHERE tenant_id = 1 AND deleted = 0 ORDER BY role_code",
                String.class);
        assertThat(roleCodes).containsExactly(
                "CASHIER", "CUSTOMER", "KITCHEN_COOK", "OWNER", "SHOP_MANAGER", "WAITER");

        // Verify OWNER has exactly 2 grants
        assertCount("""
                SELECT COUNT(*) FROM auth_role_permission arp
                JOIN auth_role ar ON ar.id = arp.role_id
                WHERE arp.tenant_id = 1 AND arp.deleted = 0
                AND ar.tenant_id = 1 AND ar.deleted = 0 AND ar.role_code = 'OWNER'
                """, 2);

        // Verify no CK permission codes in any B-type tenant grant
        for (String ckCode : CK_PERMISSION_CODES) {
            assertCount("""
                    SELECT COUNT(*) FROM auth_role_permission arp
                    JOIN auth_permission ap ON ap.id = arp.permission_id
                    WHERE arp.tenant_id = 1 AND arp.deleted = 0
                    AND ap.deleted = 0 AND ap.permission_code = '%s'
                    """.formatted(ckCode), 0);
        }

        // Verify OWNER's 2 grants are exactly finance:core-profit:read + cart:staff-assisted
        List<String> ownerPerms = jdbc.queryForList("""
                SELECT ap.permission_code FROM auth_role_permission arp
                JOIN auth_role ar ON ar.id = arp.role_id
                JOIN auth_permission ap ON ap.id = arp.permission_id
                WHERE arp.tenant_id = 1 AND arp.deleted = 0
                AND ar.role_code = 'OWNER' AND ap.deleted = 0
                ORDER BY ap.permission_code
                """, String.class);
        assertThat(ownerPerms).containsExactly("cart:staff-assisted", "finance:core-profit:read");
    }

    // ---- T-06: role IDs differ/remap ----

    @Test
    void tenantRoleIdsMustDifferFromTemplateRoleIds() {
        insertTestTenant(2L, "test-a", "A");
        service.ensureTenantRbac(2L, "h148-test");

        // For each role_code, tenant role id != template role id
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT t0.id AS template_id, t0.role_code, t2.id AS tenant_id
                FROM auth_role t0
                JOIN auth_role t2 ON t2.role_code = t0.role_code AND t2.tenant_id = 2
                WHERE t0.tenant_id = 0 AND t0.deleted = 0 AND t2.deleted = 0
                """);
        assertThat(rows).isNotEmpty();
        for (Map<String, Object> row : rows) {
            long templateId = ((Number) row.get("template_id")).longValue();
            long tenantId = ((Number) row.get("tenant_id")).longValue();
            assertThat(tenantId).isNotEqualTo(templateId);
        }
    }

    // ---- T-07: global permission count stays 21 ----

    @Test
    void globalPermissionCountMustStay21() {
        insertTestTenant(2L, "test-a", "A");
        service.ensureTenantRbac(2L, "h148-test");
        service.ensureTenantRbac(1L, "h148-test"); // B type

        assertCount("SELECT COUNT(*) FROM auth_permission WHERE deleted = 0", 21);
    }

    // ---- T-08: tenant0 stays 11/40 ----

    @Test
    void tenant0MustStay11RolesAnd40Grants() {
        insertTestTenant(2L, "test-a", "A");
        service.ensureTenantRbac(2L, "h148-test");
        service.ensureTenantRbac(1L, "h148-test");

        assertCount("SELECT COUNT(*) FROM auth_role WHERE tenant_id = 0 AND deleted = 0", 11);
        assertCount("SELECT COUNT(*) FROM auth_role_permission WHERE tenant_id = 0 AND deleted = 0", 40);
    }

    // ---- T-09: auth_user_role stays 0 ----

    @Test
    void authUserRoleMustStayZero() {
        insertTestTenant(2L, "test-a", "A");
        service.ensureTenantRbac(2L, "h148-test");

        assertCount("SELECT COUNT(*) FROM auth_user_role", 0);
    }

    // ---- T-10: idempotent repeat ----

    @Test
    void idempotentRepeatShouldBeNoOp() {
        insertTestTenant(2L, "test-a", "A");

        ProvisioningResult first = service.ensureTenantRbac(2L, "h148-test");
        assertThat(first.rolesInserted()).isEqualTo(8);
        assertThat(first.grantsInserted()).isEqualTo(32);

        ProvisioningResult second = service.ensureTenantRbac(2L, "h148-test");
        assertThat(second.rolesInserted()).isEqualTo(0);
        assertThat(second.rolesSkipped()).isEqualTo(8);
        assertThat(second.grantsInserted()).isEqualTo(0);
        assertThat(second.grantsSkipped()).isEqualTo(32);

        // No duplicate rows
        assertCount("SELECT COUNT(*) FROM auth_role WHERE tenant_id = 2 AND deleted = 0", 8);
        assertCount("SELECT COUNT(*) FROM auth_role_permission WHERE tenant_id = 2 AND deleted = 0", 32);
    }

    // ---- T-11: two-thread same tenant lock ----

    @Test
    void twoThreadSameTenantShouldSerializeWithoutDuplicates() throws Exception {
        insertTestTenant(2L, "test-a", "A");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        try {
            Future<ProvisioningResult> f1 = pool.submit(() -> {
                ready.countDown();
                ready.await();
                return service.ensureTenantRbac(2L, "thread-1");
            });
            Future<ProvisioningResult> f2 = pool.submit(() -> {
                ready.countDown();
                ready.await();
                return service.ensureTenantRbac(2L, "thread-2");
            });

            ProvisioningResult r1 = f1.get(30, TimeUnit.SECONDS);
            ProvisioningResult r2 = f2.get(30, TimeUnit.SECONDS);

            // Both should succeed
            assertThat(r1.outcome()).isEqualTo("SUCCEEDED");
            assertThat(r2.outcome()).isEqualTo("SUCCEEDED");

            // Combined inserts should be exactly 8 roles / 32 grants (no duplicates)
            int totalInserted = r1.rolesInserted() + r2.rolesInserted();
            int totalGrantInserted = r1.grantsInserted() + r2.grantsInserted();
            assertThat(totalInserted).isEqualTo(8);
            assertThat(totalGrantInserted).isEqualTo(32);

            assertCount("SELECT COUNT(*) FROM auth_role WHERE tenant_id = 2 AND deleted = 0", 8);
            assertCount("SELECT COUNT(*) FROM auth_role_permission WHERE tenant_id = 2 AND deleted = 0", 32);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- T-12/T-14: rollback on security-field conflict ----

    @Test
    void isAdminMismatchShouldRollbackAndNoBusinessRowsSurvive() {
        insertTestTenant(2L, "test-a", "A");
        // Pre-insert a tenant OWNER role with wrong is_admin (0 instead of 1)
        jdbc.update("""
                INSERT INTO auth_role (tenant_id, role_code, role_name, description, is_builtin, is_admin, status,
                    creator, create_time, updater, update_time, deleted)
                VALUES (2, 'OWNER', 'Bad Owner', 'test', 1, 0, 'ACTIVE', 'h148', NOW(), 'h148', NOW(), 0)
                """);

        assertThatThrownBy(() -> service.ensureTenantRbac(2L, "h148-test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is_admin");

        // No new roles should have been inserted (only the pre-existing bad one)
        assertCount("SELECT COUNT(*) FROM auth_role WHERE tenant_id = 2 AND deleted = 0", 1);
        // No grants at all
        assertCount("SELECT COUNT(*) FROM auth_role_permission WHERE tenant_id = 2 AND deleted = 0", 0);

        // T-13: STARTED + FAILED audit survive via REQUIRES_NEW
        assertCount("SELECT COUNT(*) FROM auth_rbac_provisioning_audit WHERE outcome = 'STARTED'", 1);
        assertCount("SELECT COUNT(*) FROM auth_rbac_provisioning_audit WHERE outcome = 'FAILED'", 1);
        assertCount("SELECT COUNT(*) FROM auth_rbac_provisioning_audit WHERE outcome = 'SUCCEEDED'", 0);
    }

    // ---- T-15: SUCCEEDED only after commit ----

    @Test
    void succeededAuditOnlyAfterCommit() {
        insertTestTenant(2L, "test-a", "A");
        ProvisioningResult result = service.ensureTenantRbac(2L, "h148-test");

        assertThat(result.outcome()).isEqualTo("SUCCEEDED");
        assertCount("SELECT COUNT(*) FROM auth_rbac_provisioning_audit WHERE outcome = 'STARTED'", 1);
        assertCount("SELECT COUNT(*) FROM auth_rbac_provisioning_audit WHERE outcome = 'SUCCEEDED'", 1);
        assertCount("SELECT COUNT(*) FROM auth_rbac_provisioning_audit WHERE outcome = 'FAILED'", 0);
    }

    // ---- T-16: custom role/name/description preserved ----

    @Test
    void customRolesAndDivergedNameDescriptionPreserved() {
        insertTestTenant(2L, "test-a", "A");
        // Pre-insert a custom role not in expected set
        jdbc.update("""
                INSERT INTO auth_role (tenant_id, role_code, role_name, description, is_builtin, is_admin, status,
                    creator, create_time, updater, update_time, deleted)
                VALUES (2, 'CUSTOM_ROLE', 'Custom', 'test custom', 0, 0, 'ACTIVE', 'h148', NOW(), 'h148', NOW(), 0)
                """);
        // Pre-insert OWNER with diverged name/description
        jdbc.update("""
                INSERT INTO auth_role (tenant_id, role_code, role_name, description, is_builtin, is_admin, status,
                    creator, create_time, updater, update_time, deleted)
                VALUES (2, 'OWNER', 'My Owner', 'My Description', 1, 1, 'ACTIVE', 'h148', NOW(), 'h148', NOW(), 0)
                """);

        ProvisioningResult result = service.ensureTenantRbac(2L, "h148-test");

        assertThat(result.outcome()).isEqualTo("SUCCEEDED");
        assertThat(result.rolesSkipped()).isGreaterThanOrEqualTo(1); // OWNER skipped

        // Custom role still exists
        assertCount("SELECT COUNT(*) FROM auth_role WHERE tenant_id = 2 AND role_code = 'CUSTOM_ROLE' AND deleted = 0", 1);
        // OWNER name/description preserved
        String ownerName = jdbc.queryForObject(
                "SELECT role_name FROM auth_role WHERE tenant_id = 2 AND role_code = 'OWNER' AND deleted = 0",
                String.class);
        assertThat(ownerName).isEqualTo("My Owner");
    }

    // ---- T-17: extra grants preserved ----

    @Test
    void extraGrantsOnExpectedRolesPreserved() {
        insertTestTenant(2L, "test-a", "A");
        // First provision
        service.ensureTenantRbac(2L, "h148-test");

        // Add an extra grant to OWNER (use an existing permission not in OWNER's template)
        // OWNER already has all 14, so use a consultant permission
        long ownerRoleId = jdbc.queryForObject(
                "SELECT id FROM auth_role WHERE tenant_id = 2 AND role_code = 'OWNER' AND deleted = 0",
                Long.class);
        long consultantDashboardPermId = jdbc.queryForObject(
                "SELECT id FROM auth_permission WHERE permission_code = 'consultant:dashboard:read' AND deleted = 0",
                Long.class);
        jdbc.update("INSERT INTO auth_role_permission (tenant_id, role_id, permission_id, creator, create_time, updater, update_time, deleted) "
                + "VALUES (2, ?, ?, 'extra', NOW(), 'extra', NOW(), 0)", ownerRoleId, consultantDashboardPermId);

        // Re-provision — should preserve the extra grant
        ProvisioningResult result = service.ensureTenantRbac(2L, "h148-test");

        assertThat(result.grantsPreserved()).isEqualTo(1);
        // Total grants = 32 (template) + 1 (extra) = 33
        assertCount("SELECT COUNT(*) FROM auth_role_permission WHERE tenant_id = 2 AND deleted = 0", 33);
    }

    // ---- T-18: role_name/description divergence succeeds ----

    @Test
    void roleNameDescriptionDivergenceSucceeds() {
        insertTestTenant(2L, "test-a", "A");
        // Pre-insert OWNER with different name/description but matching security fields
        jdbc.update("""
                INSERT INTO auth_role (tenant_id, role_code, role_name, description, is_builtin, is_admin, status,
                    creator, create_time, updater, update_time, deleted)
                VALUES (2, 'OWNER', 'Different Name', 'Different Description', 1, 1, 'ACTIVE', 'h148', NOW(), 'h148', NOW(), 0)
                """);

        ProvisioningResult result = service.ensureTenantRbac(2L, "h148-test");

        assertThat(result.outcome()).isEqualTo("SUCCEEDED");
        // OWNER was skipped (not re-inserted)
        assertCount("SELECT COUNT(*) FROM auth_role WHERE tenant_id = 2 AND role_code = 'OWNER' AND deleted = 0", 1);
        String ownerName = jdbc.queryForObject(
                "SELECT role_name FROM auth_role WHERE tenant_id = 2 AND role_code = 'OWNER' AND deleted = 0",
                String.class);
        assertThat(ownerName).isEqualTo("Different Name");
    }

    // ---- T-24: no tenant/status/life-stage/auth_permission/auth_user_role/tenant0 writes ----

    @Test
    void provisioningMustNotModifyTenantOrPermissionOrUserOrTenant0Data() {
        insertTestTenant(2L, "test-a", "A");

        // Capture before
        String tenantBefore = jdbc.queryForObject(
                "SELECT CONCAT(merchant_type, '|', status, '|', life_stage) FROM tenants WHERE id = 2", String.class);
        assertCount("SELECT COUNT(*) FROM auth_permission", 21);
        assertCount("SELECT COUNT(*) FROM auth_user_role", 0);
        assertCount("SELECT COUNT(*) FROM auth_role WHERE tenant_id = 0", 11);
        assertCount("SELECT COUNT(*) FROM auth_role_permission WHERE tenant_id = 0", 40);

        service.ensureTenantRbac(2L, "h148-test");

        // After: tenant unchanged
        String tenantAfter = jdbc.queryForObject(
                "SELECT CONCAT(merchant_type, '|', status, '|', life_stage) FROM tenants WHERE id = 2", String.class);
        assertThat(tenantAfter).isEqualTo(tenantBefore);
        assertCount("SELECT COUNT(*) FROM auth_permission", 21);
        assertCount("SELECT COUNT(*) FROM auth_user_role", 0);
        assertCount("SELECT COUNT(*) FROM auth_role WHERE tenant_id = 0", 11);
        assertCount("SELECT COUNT(*) FROM auth_role_permission WHERE tenant_id = 0", 40);
    }

    // ---- T-23: deleted permission malformed reference fail closed ----

    @Test
    void deletedPermissionInTemplateShouldFailClosed() {
        // This test verifies the validation path: if a template grant references
        // a deleted permission, provisioning should fail.
        // We simulate this by soft-deleting a permission that a template grant references,
        // then attempting provisioning.
        insertTestTenant(2L, "test-a", "A");

        // Soft-delete the cart:staff-assisted permission (used by SHOP_MANAGER and CASHIER)
        jdbc.update("UPDATE auth_permission SET deleted = 1 WHERE permission_code = 'cart:staff-assisted'");

        assertThatThrownBy(() -> service.ensureTenantRbac(2L, "h148-test"))
                .isInstanceOf(IllegalStateException.class);

        // Restore
        jdbc.update("UPDATE auth_permission SET deleted = 0 WHERE permission_code = 'cart:staff-assisted'");
    }

    // ---- Helpers ----

    private void insertTestTenant(long id, String code, String merchantType) {
        jdbc.update("""
                INSERT INTO tenants (id, tenant_code, tenant_name, merchant_type, status, life_stage,
                    business_day_cutoff_hour, timezone, creator, create_time, updater, update_time, deleted)
                VALUES (?, ?, ?, ?, 'ACTIVE', 'EXPLORATION', 3, 'Asia/Shanghai',
                    'h148', NOW(), 'h148', NOW(), 0)
                """, id, code, "Test " + merchantType + " Tenant", merchantType);
    }

    private void assertCount(String sql, int expected) {
        Integer count = jdbc.queryForObject(sql, Integer.class);
        assertThat(count).as(sql).isEqualTo(expected);
    }

    private void cleanupTestData() {
        // Test-only cleanup — DELETE is explicitly test-only, not production evidence.
        jdbc.update("DELETE FROM auth_rbac_provisioning_audit");
        jdbc.update("DELETE FROM auth_role_permission WHERE tenant_id > 0");
        jdbc.update("DELETE FROM auth_role WHERE tenant_id > 0");
        jdbc.update("DELETE FROM tenants WHERE id > 1");
        // Restore any soft-deleted permissions
        jdbc.update("UPDATE auth_permission SET deleted = 0 WHERE deleted = 1 AND permission_code = 'cart:staff-assisted'");
    }
}
