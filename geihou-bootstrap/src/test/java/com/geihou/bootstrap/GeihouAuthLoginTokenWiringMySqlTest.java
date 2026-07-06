package com.geihou.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.module.system.dal.mysql.auth.AuthRbacProvisioningAuditRepository;
import com.geihou.module.system.dal.mysql.auth.AuthPermissionRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRolePermissionRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRoleRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRoleRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRoleWriteRepository;
import com.geihou.module.system.dal.mysql.auth.GeihouTenantRbacProvisioningRepository;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenIssuer;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import com.geihou.module.system.framework.jwt.GeihouActiveSigningSecretProvider;
import com.geihou.module.system.framework.jwt.GeihouSigningSecret;
import com.geihou.module.system.framework.jwt.GeihouJwtTokenParser;
import com.geihou.module.system.service.auth.GeihouAccessTokenIssueResult;
import com.geihou.module.system.service.auth.GeihouAccessTokenIssueService;
import com.geihou.module.system.service.auth.GeihouAuthLoginTokenWiringService;
import com.geihou.module.system.service.auth.GeihouAuthRolePermissionReadService;
import com.geihou.module.system.service.auth.GeihouTenantRbacProvisioningService;
import com.geihou.module.system.service.auth.GeihouUserRoleAssignmentService;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real MySQL8 Testcontainers integration test for login token wiring.
 *
 * <p>Uses H148 provisioning + H150 assignment + H146 read model + real JWT issuer
 * to prove: assigned role can sign, unassigned role denies, auth_permission not
 * written, permissions never in JWT.
 */
@Testcontainers
@SpringBootTest(
        classes = GeihouAuthLoginTokenWiringMySqlTest.TestConfig.class,
        properties = {
                "spring.flyway.locations=classpath:db/migration",
                "spring.flyway.clean-disabled=true",
                "spring.flyway.baseline-on-migrate=false",
                "spring.main.web-application-type=none"
        }
)
class GeihouAuthLoginTokenWiringMySqlTest {

    private static final Instant NOW = Instant.parse("2026-06-19T02:00:00Z");
    private static final byte[] SECRET = secretBytes();
    private static final GeihouSigningSecret SIGNING_SECRET =
            new GeihouSigningSecret("kid-h152-mysql", "HS512", SECRET);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("geihou_h152")
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
            AuthUserRoleRepository.class,
            AuthPermissionRepository.class,
            AuthRolePermissionRepository.class,
            AuthUserRoleWriteRepository.class,
            GeihouUserRoleAssignmentService.class,
            GeihouAuthRolePermissionReadService.class
    })
    static class TestConfig {
        @Bean
        GeihouActiveSigningSecretProvider signingSecretProvider() {
            return () -> Optional.of(SIGNING_SECRET);
        }

        @Bean
        GeihouAccessTokenIssuer accessTokenIssuer(GeihouActiveSigningSecretProvider provider) {
            return new GeihouAccessTokenIssuer(provider, Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Bean
        GeihouAccessTokenIssueService accessTokenIssueService(GeihouAccessTokenIssuer issuer) {
            return new GeihouAccessTokenIssueService(issuer);
        }

        @Bean
        GeihouAuthLoginTokenWiringService loginTokenWiringService(
                GeihouAuthRolePermissionReadService readService,
                GeihouAccessTokenIssueService issueService) {
            return new GeihouAuthLoginTokenWiringService(readService, issueService);
        }
    }

    @Autowired
    private GeihouTenantRbacProvisioningService provisioningService;

    @Autowired
    private GeihouUserRoleAssignmentService assignmentService;

    @Autowired
    private GeihouAuthLoginTokenWiringService wiringService;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        cleanupTestData();
    }

    // ---- T-01: Assigned role can sign ----

    @Test
    void shouldIssueTokenForUserWithAssignedRole() throws Exception {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h152-test");

        long ownerRoleId = getTenantRoleId(2L, "OWNER");
        assignmentService.assignRole(2L, 2001L, ownerRoleId,
                GeihouAccessTokenUserRole.OWNER, "h152-test");

        Optional<GeihouAccessTokenIssueResult> result = wiringService.issueLoginToken(
                2001L, 2L, GeihouAccessTokenUserRole.OWNER, false, "h152-test");

        assertThat(result).isPresent();
        GeihouAccessTokenIssueResult token = result.get();
        assertThat(token.userId()).isEqualTo(2001L);
        assertThat(token.tenantId()).isEqualTo(2L);
        assertThat(token.userRole()).isEqualTo("OWNER");
        assertThat(token.roles()).containsExactly("OWNER");

        // Verify token is valid and has no permissions claim
        SignedJWT signedJwt = SignedJWT.parse(token.accessToken());
        JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
        assertThat(claims.getClaim("permissions")).isNull();
        assertThat(claims.getClaim("permission")).isNull();

        var verified = new GeihouJwtTokenParser("geihou-platform",
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC))
                .verifyToken(token.accessToken(), SECRET);
        assertThat(verified.getValid()).isTrue();
    }

    // ---- T-02: Unassigned role denies ----

    @Test
    void shouldDenyTokenForUserWithoutAssignedRole() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h152-test");

        // User 2002 has no auth_user_role rows
        Optional<GeihouAccessTokenIssueResult> result = wiringService.issueLoginToken(
                2002L, 2L, GeihouAccessTokenUserRole.OWNER, false, "h152-test");

        assertThat(result).isEmpty();
    }

    // ---- T-03: User with wrong tenant role denies ----

    @Test
    void shouldDenyTokenForUserWithRoleInWrongTenant() {
        insertTestTenant(2L, "test-a", "A");
        insertTestTenant(3L, "test-b", "A");
        provisioningService.ensureTenantRbac(2L, "h152-test");
        provisioningService.ensureTenantRbac(3L, "h152-test");

        // Assign role in tenant 3
        long tenant3OwnerRoleId = getTenantRoleId(3L, "OWNER");
        assignmentService.assignRole(3L, 2003L, tenant3OwnerRoleId,
                GeihouAccessTokenUserRole.OWNER, "h152-test");

        // Try to get token for tenant 2 (no assignment there)
        Optional<GeihouAccessTokenIssueResult> result = wiringService.issueLoginToken(
                2003L, 2L, GeihouAccessTokenUserRole.OWNER, false, "h152-test");

        assertThat(result).isEmpty();
    }

    // ---- T-04: auth_permission not written ----

    @Test
    void authPermissionCountMustStay21AfterTokenIssuance() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h152-test");

        assertCount("SELECT COUNT(*) FROM auth_permission WHERE deleted = 0", 21);

        long ownerRoleId = getTenantRoleId(2L, "OWNER");
        assignmentService.assignRole(2L, 2001L, ownerRoleId,
                GeihouAccessTokenUserRole.OWNER, "h152-test");

        wiringService.issueLoginToken(2001L, 2L, GeihouAccessTokenUserRole.OWNER, false, "h152-test");

        assertCount("SELECT COUNT(*) FROM auth_permission WHERE deleted = 0", 21);
    }

    // ---- T-05: Permissions never in JWT (MySQL integration) ----

    @Test
    void issuedJwtMustNotContainPermissionsClaim() throws Exception {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h152-test");

        long ownerRoleId = getTenantRoleId(2L, "OWNER");
        assignmentService.assignRole(2L, 2001L, ownerRoleId,
                GeihouAccessTokenUserRole.OWNER, "h152-test");

        Optional<GeihouAccessTokenIssueResult> result = wiringService.issueLoginToken(
                2001L, 2L, GeihouAccessTokenUserRole.OWNER, false, "h152-test");

        assertThat(result).isPresent();
        SignedJWT signedJwt = SignedJWT.parse(result.get().accessToken());
        JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
        assertThat(claims.getClaim("permissions")).isNull();
        assertThat(claims.getClaim("permission")).isNull();
        assertThat(claims.getStringListClaim("permissions")).isNull();
    }

    // ---- T-06: B-type tenant (Abao tenant1) assigned role can sign ----

    @Test
    void shouldIssueTokenForBTypeTenantAssignedRole() {
        // Abao tenant (tenant_id=1, B) already exists from migration
        provisioningService.ensureTenantRbac(1L, "h152-test");

        long ownerRoleId = getTenantRoleId(1L, "OWNER");
        assignmentService.assignRole(1L, 1001L, ownerRoleId,
                GeihouAccessTokenUserRole.OWNER, "h152-test");

        Optional<GeihouAccessTokenIssueResult> result = wiringService.issueLoginToken(
                1001L, 1L, GeihouAccessTokenUserRole.OWNER, false, "h152-test");

        assertThat(result).isPresent();
        assertThat(result.get().roles()).containsExactly("OWNER");
        assertThat(result.get().tenantId()).isEqualTo(1L);
    }

    // ---- T-07: Store staff with multiple roles signs with all roles ----

    @Test
    void shouldIssueTokenWithAllAssignedRolesForStoreStaff() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h152-test");

        long cashierRoleId = getTenantRoleId(2L, "CASHIER");
        long waiterRoleId = getTenantRoleId(2L, "WAITER");
        long cookRoleId = getTenantRoleId(2L, "KITCHEN_COOK");

        assignmentService.assignRole(2L, 2004L, cashierRoleId,
                GeihouAccessTokenUserRole.STORE_STAFF, "h152-test");
        assignmentService.assignRole(2L, 2004L, waiterRoleId,
                GeihouAccessTokenUserRole.STORE_STAFF, "h152-test");
        assignmentService.assignRole(2L, 2004L, cookRoleId,
                GeihouAccessTokenUserRole.STORE_STAFF, "h152-test");

        Optional<GeihouAccessTokenIssueResult> result = wiringService.issueLoginToken(
                2004L, 2L, GeihouAccessTokenUserRole.STORE_STAFF, false, "h152-test");

        assertThat(result).isPresent();
        assertThat(result.get().roles()).containsExactly("CASHIER", "KITCHEN_COOK", "WAITER");
    }

    // ---- T-08: Tenant0 data unchanged ----

    @Test
    void tenant0DataMustNotChangeAfterTokenIssuance() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h152-test");

        long ownerRoleId = getTenantRoleId(2L, "OWNER");
        assignmentService.assignRole(2L, 2001L, ownerRoleId,
                GeihouAccessTokenUserRole.OWNER, "h152-test");

        wiringService.issueLoginToken(2001L, 2L, GeihouAccessTokenUserRole.OWNER, false, "h152-test");

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
                    'h152', NOW(), 'h152', NOW(), 0)
                """, id, code, "Test " + merchantType + " Tenant", merchantType);
    }

    private void assertCount(String sql, int expected) {
        Integer count = jdbc.queryForObject(sql, Integer.class);
        assertThat(count).as(sql).isEqualTo(expected);
    }

    private static byte[] secretBytes() {
        byte[] secret = new byte[64];
        for (int i = 0; i < secret.length; i++) {
            secret[i] = (byte) (i + 1);
        }
        return secret;
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
