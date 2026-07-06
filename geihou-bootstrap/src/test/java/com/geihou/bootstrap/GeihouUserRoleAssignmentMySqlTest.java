package com.geihou.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geihou.module.system.dal.mysql.auth.AuthRbacProvisioningAuditRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRoleRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRoleWriteRepository;
import com.geihou.module.system.dal.mysql.auth.GeihouTenantRbacProvisioningRepository;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import com.geihou.module.system.service.auth.GeihouTenantRbacProvisioningService;
import com.geihou.module.system.service.auth.GeihouTenantRbacProvisioningService.ProvisioningResult;
import com.geihou.module.system.service.auth.GeihouUserRoleAssignmentService;
import com.geihou.module.system.service.auth.GeihouUserRoleAssignmentService.AssignmentResult;
import java.util.List;
import java.util.Map;
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

/**
 * Real MySQL8 Testcontainers integration test for user-role assignment.
 *
 * <p>First calls {@code ensureTenantRbac} to create tenant-owned roles,
 * then exercises {@link GeihouUserRoleAssignmentService#assignRole}.
 */
@Testcontainers
@SpringBootTest(
        classes = GeihouUserRoleAssignmentMySqlTest.TestConfig.class,
        properties = {
                "spring.flyway.locations=classpath:db/migration",
                "spring.flyway.clean-disabled=true",
                "spring.flyway.baseline-on-migrate=false",
                "spring.main.web-application-type=none"
        }
)
class GeihouUserRoleAssignmentMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("geihou_h150")
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
            GeihouTenantRbacProvisioningService.class,
            AuthRoleRepository.class,
            AuthUserRoleWriteRepository.class,
            GeihouUserRoleAssignmentService.class
    })
    static class TestConfig {
    }

    @Autowired
    private GeihouTenantRbacProvisioningService provisioningService;

    @Autowired
    private GeihouUserRoleAssignmentService assignmentService;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        cleanupTestData();
    }

    // ---- T-01: Successful insert after provisioning ----

    @Test
    void shouldInsertAssignmentAfterProvisioning() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h150-test");

        long ownerRoleId = getTenantRoleId(2L, "OWNER");

        AssignmentResult result = assignmentService.assignRole(
                2L, 2001L, ownerRoleId, GeihouAccessTokenUserRole.OWNER, "h150-test");

        assertThat(result.outcome()).isEqualTo("inserted");
        assertThat(result.tenantId()).isEqualTo(2L);
        assertThat(result.userId()).isEqualTo(2001L);
        assertThat(result.roleId()).isEqualTo(ownerRoleId);
        assertThat(result.roleCode()).isEqualTo("OWNER");

        assertCount("SELECT COUNT(*) FROM auth_user_role WHERE tenant_id = 2 AND user_id = 2001 AND role_id = " + ownerRoleId + " AND deleted = 0", 1);
    }

    // ---- T-02: role_id != tenant0 template role_id ----

    @Test
    void assignedRoleIdMustDifferFromTenant0TemplateRoleId() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h150-test");

        long tenant0OwnerId = jdbc.queryForObject(
                "SELECT id FROM auth_role WHERE tenant_id = 0 AND role_code = 'OWNER' AND deleted = 0",
                Long.class);
        long tenantOwnerId = getTenantRoleId(2L, "OWNER");

        assertThat(tenantOwnerId).isNotEqualTo(tenant0OwnerId);

        AssignmentResult result = assignmentService.assignRole(
                2L, 2001L, tenantOwnerId, GeihouAccessTokenUserRole.OWNER, "h150-test");
        assertThat(result.roleId()).isNotEqualTo(tenant0OwnerId);
    }

    // ---- T-03: Wrong tenant role id fails closed ----

    @Test
    void wrongTenantRoleIdShouldFailClosed() {
        insertTestTenant(2L, "test-a", "A");
        insertTestTenant(3L, "test-b", "B");
        provisioningService.ensureTenantRbac(2L, "h150-test");
        provisioningService.ensureTenantRbac(3L, "h150-test");

        long tenant3OwnerId = getTenantRoleId(3L, "OWNER");

        // Try to assign tenant3's role to a user in tenant2
        assertThatThrownBy(() -> assignmentService.assignRole(
                2L, 2001L, tenant3OwnerId, GeihouAccessTokenUserRole.OWNER, "h150-test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");

        // No auth_user_role row should exist
        assertCount("SELECT COUNT(*) FROM auth_user_role WHERE tenant_id = 2 AND deleted = 0", 0);
    }

    // ---- T-04: Template (tenant0) role id fails closed ----

    @Test
    void tenant0TemplateRoleIdShouldFailClosed() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h150-test");

        long templateOwnerId = jdbc.queryForObject(
                "SELECT id FROM auth_role WHERE tenant_id = 0 AND role_code = 'OWNER' AND deleted = 0",
                Long.class);

        assertThatThrownBy(() -> assignmentService.assignRole(
                2L, 2001L, templateOwnerId, GeihouAccessTokenUserRole.OWNER, "h150-test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");

        assertCount("SELECT COUNT(*) FROM auth_user_role WHERE tenant_id = 2 AND deleted = 0", 0);
    }

    // ---- T-05: Disallowed role code fails closed ----

    @Test
    void disallowedRoleCodeShouldFailClosed() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h150-test");

        long cashierRoleId = getTenantRoleId(2L, "CASHIER");

        // CASHIER role_code is not in OWNER's allowlist
        assertThatThrownBy(() -> assignmentService.assignRole(
                2L, 2001L, cashierRoleId, GeihouAccessTokenUserRole.OWNER, "h150-test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not allowed");

        assertCount("SELECT COUNT(*) FROM auth_user_role WHERE tenant_id = 2 AND deleted = 0", 0);
    }

    // ---- T-06: Idempotent repeat ----

    @Test
    void idempotentRepeatShouldSkipSecondCall() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h150-test");

        long ownerRoleId = getTenantRoleId(2L, "OWNER");

        AssignmentResult first = assignmentService.assignRole(
                2L, 2001L, ownerRoleId, GeihouAccessTokenUserRole.OWNER, "h150-test");
        assertThat(first.outcome()).isEqualTo("inserted");

        AssignmentResult second = assignmentService.assignRole(
                2L, 2001L, ownerRoleId, GeihouAccessTokenUserRole.OWNER, "h150-test");
        assertThat(second.outcome()).isEqualTo("skipped");

        // Exactly one row
        assertCount("SELECT COUNT(*) FROM auth_user_role WHERE tenant_id = 2 AND user_id = 2001 AND deleted = 0", 1);
    }

    // ---- T-07: auth_permission unchanged ----

    @Test
    void authPermissionCountMustStay21() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h150-test");

        assertCount("SELECT COUNT(*) FROM auth_permission WHERE deleted = 0", 21);

        long ownerRoleId = getTenantRoleId(2L, "OWNER");
        assignmentService.assignRole(
                2L, 2001L, ownerRoleId, GeihouAccessTokenUserRole.OWNER, "h150-test");

        assertCount("SELECT COUNT(*) FROM auth_permission WHERE deleted = 0", 21);
    }

    // ---- T-08: tenant/status/merchant_type unchanged ----

    @Test
    void tenantStatusAndMerchantTypeMustNotChange() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h150-test");

        String before = jdbc.queryForObject(
                "SELECT CONCAT(merchant_type, '|', status, '|', life_stage) FROM tenants WHERE id = 2",
                String.class);

        long ownerRoleId = getTenantRoleId(2L, "OWNER");
        assignmentService.assignRole(
                2L, 2001L, ownerRoleId, GeihouAccessTokenUserRole.OWNER, "h150-test");

        String after = jdbc.queryForObject(
                "SELECT CONCAT(merchant_type, '|', status, '|', life_stage) FROM tenants WHERE id = 2",
                String.class);
        assertThat(after).isEqualTo(before);
    }

    // ---- T-09: Multiple assignments for different users ----

    @Test
    void shouldSupportMultipleAssignmentsForDifferentUsers() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h150-test");

        long ownerRoleId = getTenantRoleId(2L, "OWNER");
        long cashierRoleId = getTenantRoleId(2L, "CASHIER");

        assignmentService.assignRole(2L, 2001L, ownerRoleId,
                GeihouAccessTokenUserRole.OWNER, "h150-test");
        assignmentService.assignRole(2L, 2002L, cashierRoleId,
                GeihouAccessTokenUserRole.STORE_STAFF, "h150-test");

        assertCount("SELECT COUNT(*) FROM auth_user_role WHERE tenant_id = 2 AND deleted = 0", 2);
    }

    // ---- T-10: B-type tenant assignment ----

    @Test
    void shouldAssignRoleInBTypeTenant() {
        // Abao tenant (tenant_id=1, B) already exists from migration
        provisioningService.ensureTenantRbac(1L, "h150-test");

        long ownerRoleId = getTenantRoleId(1L, "OWNER");

        AssignmentResult result = assignmentService.assignRole(
                1L, 1001L, ownerRoleId, GeihouAccessTokenUserRole.OWNER, "h150-test");

        assertThat(result.outcome()).isEqualTo("inserted");
        assertThat(result.roleCode()).isEqualTo("OWNER");

        assertCount("SELECT COUNT(*) FROM auth_user_role WHERE tenant_id = 1 AND user_id = 1001 AND deleted = 0", 1);
    }

    // ---- T-11: tenant0 role_permission unchanged ----

    @Test
    void tenant0DataMustNotChange() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h150-test");

        assertCount("SELECT COUNT(*) FROM auth_role WHERE tenant_id = 0 AND deleted = 0", 11);
        assertCount("SELECT COUNT(*) FROM auth_role_permission WHERE tenant_id = 0 AND deleted = 0", 40);

        long ownerRoleId = getTenantRoleId(2L, "OWNER");
        assignmentService.assignRole(
                2L, 2001L, ownerRoleId, GeihouAccessTokenUserRole.OWNER, "h150-test");

        assertCount("SELECT COUNT(*) FROM auth_role WHERE tenant_id = 0 AND deleted = 0", 11);
        assertCount("SELECT COUNT(*) FROM auth_role_permission WHERE tenant_id = 0 AND deleted = 0", 40);
    }

    // ---- Helpers ----

    private long getTenantRoleId(long tenantId, String roleCode) {
        return jdbc.queryForObject(
                "SELECT id FROM auth_role WHERE tenant_id = ? AND role_code = ? AND deleted = 0",
                Long.class, tenantId, roleCode);
    }

    private void insertTestTenant(long id, String code, String merchantType) {
        jdbc.update("""
                INSERT INTO tenants (id, tenant_code, tenant_name, merchant_type, status, life_stage,
                    business_day_cutoff_hour, timezone, creator, create_time, updater, update_time, deleted)
                VALUES (?, ?, ?, ?, 'ACTIVE', 'EXPLORATION', 3, 'Asia/Shanghai',
                    'h150', NOW(), 'h150', NOW(), 0)
                """, id, code, "Test " + merchantType + " Tenant", merchantType);
    }

    private void assertCount(String sql, int expected) {
        Integer count = jdbc.queryForObject(sql, Integer.class);
        assertThat(count).as(sql).isEqualTo(expected);
    }

    private void cleanupTestData() {
        // Test-only cleanup — DELETE is explicitly test-only, not production evidence.
        jdbc.update("DELETE FROM auth_user_role");
        jdbc.update("DELETE FROM auth_rbac_provisioning_audit");
        jdbc.update("DELETE FROM auth_role_permission WHERE tenant_id > 0");
        jdbc.update("DELETE FROM auth_role WHERE tenant_id > 0");
        jdbc.update("DELETE FROM tenants WHERE id > 1");
    }
}
