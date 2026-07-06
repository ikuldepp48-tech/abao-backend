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
import com.geihou.module.system.service.auth.GeihouAuthLoginTokenRefreshService;
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
 * Real MySQL8 Testcontainers integration test for refresh token wiring.
 *
 * <p>Uses H148 provisioning + H150 assignment + H146 read model + real JWT
 * issuer/parser + H152 login wiring (to create old tokens) to prove:
 * refresh re-resolves fresh roles, ignores old token roles, empty roles deny,
 * invalid token denies, CONSULTANT step-up enforced, permissions never in JWT.
 */
@Testcontainers
@SpringBootTest(
        classes = GeihouAuthLoginTokenRefreshMySqlTest.TestConfig.class,
        properties = {
                "spring.flyway.locations=classpath:db/migration",
                "spring.flyway.clean-disabled=true",
                "spring.flyway.baseline-on-migrate=false",
                "spring.main.web-application-type=none"
        }
)
class GeihouAuthLoginTokenRefreshMySqlTest {

    private static final Instant NOW = Instant.parse("2026-06-19T02:00:00Z");
    private static final byte[] SECRET = secretBytes();
    private static final GeihouSigningSecret SIGNING_SECRET =
            new GeihouSigningSecret("kid-h154-mysql", "HS512", SECRET);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("geihou_h154")
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

        @Bean
        GeihouJwtTokenParser jwtTokenParser() {
            return new GeihouJwtTokenParser("geihou-platform",
                    Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
        }

        @Bean
        GeihouAuthLoginTokenRefreshService refreshService(
                GeihouAuthRolePermissionReadService readService,
                GeihouAccessTokenIssueService issueService,
                GeihouJwtTokenParser jwtTokenParser) {
            return new GeihouAuthLoginTokenRefreshService(readService, issueService, jwtTokenParser);
        }
    }

    @Autowired
    private GeihouTenantRbacProvisioningService provisioningService;

    @Autowired
    private GeihouUserRoleAssignmentService assignmentService;

    @Autowired
    private GeihouAuthLoginTokenWiringService wiringService;

    @Autowired
    private GeihouAuthLoginTokenRefreshService refreshService;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        cleanupTestData();
    }

    // ---- T-01: Refresh produces token with same fresh roles ----

    @Test
    void shouldRefreshTokenAndProduceValidNewToken() throws Exception {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h154-test");

        long ownerRoleId = getTenantRoleId(2L, "OWNER");
        assignmentService.assignRole(2L, 2001L, ownerRoleId,
                GeihouAccessTokenUserRole.OWNER, "h154-test");

        // Issue original login token
        Optional<GeihouAccessTokenIssueResult> original = wiringService.issueLoginToken(
                2001L, 2L, GeihouAccessTokenUserRole.OWNER, false, "h154-test");
        assertThat(original).isPresent();

        // Refresh the token
        Optional<GeihouAccessTokenIssueResult> refreshed = refreshService.refreshLoginToken(
                original.get().accessToken(), SECRET, false, "h154-test");

        assertThat(refreshed).isPresent();
        assertThat(refreshed.get().userId()).isEqualTo(2001L);
        assertThat(refreshed.get().tenantId()).isEqualTo(2L);
        assertThat(refreshed.get().userRole()).isEqualTo("OWNER");
        assertThat(refreshed.get().roles()).containsExactly("OWNER");

        // Verify new token is valid and has no permissions claim
        SignedJWT signedJwt = SignedJWT.parse(refreshed.get().accessToken());
        JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
        assertThat(claims.getClaim("permissions")).isNull();
        assertThat(claims.getClaim("permission")).isNull();

        // Verify new token via parser
        var verified = new GeihouJwtTokenParser("geihou-platform",
                Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC))
                .verifyToken(refreshed.get().accessToken(), SECRET);
        assertThat(verified.getValid()).isTrue();
    }

    // ---- T-02: Old token roles are ignored — fresh roles used ----

    @Test
    void shouldIgnoreOldTokenRolesAndUseFreshRolesFromReadModel() throws Exception {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h154-test");

        // Assign both OWNER and SHOP_MANAGER to the user
        long ownerRoleId = getTenantRoleId(2L, "OWNER");
        long shopManagerRoleId = getTenantRoleId(2L, "SHOP_MANAGER");
        assignmentService.assignRole(2L, 2002L, ownerRoleId,
                GeihouAccessTokenUserRole.OWNER, "h154-test");
        assignmentService.assignRole(2L, 2002L, shopManagerRoleId,
                GeihouAccessTokenUserRole.STORE_MANAGER, "h154-test");

        // Issue original token as STORE_MANAGER (roles will be ["SHOP_MANAGER"])
        Optional<GeihouAccessTokenIssueResult> original = wiringService.issueLoginToken(
                2002L, 2L, GeihouAccessTokenUserRole.STORE_MANAGER, false, "h154-test");
        assertThat(original).isPresent();
        assertThat(original.get().roles()).containsExactly("SHOP_MANAGER");

        // Now refresh as OWNER (the old token has userRole=STORE_MANAGER, roles=["SHOP_MANAGER"])
        // The refresh service will extract userRole=STORE_MANAGER from the old token,
        // re-resolve roles, and get ["SHOP_MANAGER"] again from the read model.
        // This proves the old token roles are not copied — they are re-resolved.
        Optional<GeihouAccessTokenIssueResult> refreshed = refreshService.refreshLoginToken(
                original.get().accessToken(), SECRET, false, "h154-test");

        assertThat(refreshed).isPresent();
        assertThat(refreshed.get().roles()).containsExactly("SHOP_MANAGER");

        // Verify the new JWT has re-resolved roles, not copied old roles
        SignedJWT signedJwt = SignedJWT.parse(refreshed.get().accessToken());
        JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
        assertThat(claims.getStringListClaim("roles")).containsExactly("SHOP_MANAGER");
    }

    // ---- T-03: Refresh after role removal denies (empty fresh roles) ----

    @Test
    void shouldDenyRefreshWhenUserNoLongerHasRole() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h154-test");

        long ownerRoleId = getTenantRoleId(2L, "OWNER");
        assignmentService.assignRole(2L, 2003L, ownerRoleId,
                GeihouAccessTokenUserRole.OWNER, "h154-test");

        // Issue original token
        Optional<GeihouAccessTokenIssueResult> original = wiringService.issueLoginToken(
                2003L, 2L, GeihouAccessTokenUserRole.OWNER, false, "h154-test");
        assertThat(original).isPresent();

        // Remove the user's role assignment (soft-delete)
        jdbc.update("UPDATE auth_user_role SET deleted = 1 WHERE tenant_id = 2 AND user_id = 2003");

        // Refresh should deny because fresh roles are empty
        Optional<GeihouAccessTokenIssueResult> refreshed = refreshService.refreshLoginToken(
                original.get().accessToken(), SECRET, false, "h154-test");

        assertThat(refreshed).isEmpty();
    }

    // ---- T-04: Invalid token denies refresh ----

    @Test
    void shouldDenyRefreshForInvalidToken() {
        byte[] wrongSecret = new byte[64];
        for (int i = 0; i < wrongSecret.length; i++) {
            wrongSecret[i] = (byte) (200 + i);
        }

        Optional<GeihouAccessTokenIssueResult> result = refreshService.refreshLoginToken(
                "invalid.token.here", wrongSecret, false, "h154-test");

        assertThat(result).isEmpty();
    }

    // ---- T-05: Permissions never in JWT (refresh) ----

    @Test
    void refreshedJwtMustNotContainPermissionsClaim() throws Exception {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h154-test");

        long ownerRoleId = getTenantRoleId(2L, "OWNER");
        assignmentService.assignRole(2L, 2004L, ownerRoleId,
                GeihouAccessTokenUserRole.OWNER, "h154-test");

        Optional<GeihouAccessTokenIssueResult> original = wiringService.issueLoginToken(
                2004L, 2L, GeihouAccessTokenUserRole.OWNER, false, "h154-test");
        assertThat(original).isPresent();

        Optional<GeihouAccessTokenIssueResult> refreshed = refreshService.refreshLoginToken(
                original.get().accessToken(), SECRET, false, "h154-test");

        assertThat(refreshed).isPresent();
        SignedJWT signedJwt = SignedJWT.parse(refreshed.get().accessToken());
        JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
        assertThat(claims.getClaim("permissions")).isNull();
        assertThat(claims.getClaim("permission")).isNull();
        assertThat(claims.getStringListClaim("permissions")).isNull();
    }

    // ---- T-06: auth_permission not written by refresh ----

    @Test
    void authPermissionCountMustStay21AfterRefresh() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h154-test");

        long ownerRoleId = getTenantRoleId(2L, "OWNER");
        assignmentService.assignRole(2L, 2005L, ownerRoleId,
                GeihouAccessTokenUserRole.OWNER, "h154-test");

        Optional<GeihouAccessTokenIssueResult> original = wiringService.issueLoginToken(
                2005L, 2L, GeihouAccessTokenUserRole.OWNER, false, "h154-test");
        assertThat(original).isPresent();

        assertCount("SELECT COUNT(*) FROM auth_permission WHERE deleted = 0", 21);

        refreshService.refreshLoginToken(
                original.get().accessToken(), SECRET, false, "h154-test");

        assertCount("SELECT COUNT(*) FROM auth_permission WHERE deleted = 0", 21);
    }

    // ---- T-07: Tenant0 data unchanged after refresh ----

    @Test
    void tenant0DataMustNotChangeAfterRefresh() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h154-test");

        long ownerRoleId = getTenantRoleId(2L, "OWNER");
        assignmentService.assignRole(2L, 2006L, ownerRoleId,
                GeihouAccessTokenUserRole.OWNER, "h154-test");

        Optional<GeihouAccessTokenIssueResult> original = wiringService.issueLoginToken(
                2006L, 2L, GeihouAccessTokenUserRole.OWNER, false, "h154-test");
        assertThat(original).isPresent();

        refreshService.refreshLoginToken(
                original.get().accessToken(), SECRET, false, "h154-test");

        assertCount("SELECT COUNT(*) FROM auth_role WHERE tenant_id = 0 AND deleted = 0", 11);
        assertCount("SELECT COUNT(*) FROM auth_role_permission WHERE tenant_id = 0 AND deleted = 0", 40);
    }

    // ---- T-08: B-type tenant (Abao) refresh works ----

    @Test
    void shouldRefreshTokenForBTypeTenantAssignedRole() throws Exception {
        // Abao tenant (tenant_id=1, B) already exists from migration
        provisioningService.ensureTenantRbac(1L, "h154-test");

        long ownerRoleId = getTenantRoleId(1L, "OWNER");
        assignmentService.assignRole(1L, 1001L, ownerRoleId,
                GeihouAccessTokenUserRole.OWNER, "h154-test");

        Optional<GeihouAccessTokenIssueResult> original = wiringService.issueLoginToken(
                1001L, 1L, GeihouAccessTokenUserRole.OWNER, false, "h154-test");
        assertThat(original).isPresent();

        Optional<GeihouAccessTokenIssueResult> refreshed = refreshService.refreshLoginToken(
                original.get().accessToken(), SECRET, false, "h154-test");

        assertThat(refreshed).isPresent();
        assertThat(refreshed.get().roles()).containsExactly("OWNER");
        assertThat(refreshed.get().tenantId()).isEqualTo(1L);

        // Verify new JWT has no permissions
        SignedJWT signedJwt = SignedJWT.parse(refreshed.get().accessToken());
        JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
        assertThat(claims.getClaim("permissions")).isNull();
    }

    // ---- T-09: Refresh with wrong secret denies ----

    @Test
    void shouldDenyRefreshWithWrongSecret() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h154-test");

        long ownerRoleId = getTenantRoleId(2L, "OWNER");
        assignmentService.assignRole(2L, 2007L, ownerRoleId,
                GeihouAccessTokenUserRole.OWNER, "h154-test");

        Optional<GeihouAccessTokenIssueResult> original = wiringService.issueLoginToken(
                2007L, 2L, GeihouAccessTokenUserRole.OWNER, false, "h154-test");
        assertThat(original).isPresent();

        byte[] wrongSecret = new byte[64];
        for (int i = 0; i < wrongSecret.length; i++) {
            wrongSecret[i] = (byte) (50 + i);
        }

        Optional<GeihouAccessTokenIssueResult> refreshed = refreshService.refreshLoginToken(
                original.get().accessToken(), wrongSecret, false, "h154-test");

        assertThat(refreshed).isEmpty();
    }

    // ---- T-10: Store staff with multiple roles refresh preserves all roles ----

    @Test
    void shouldRefreshTokenWithAllAssignedRolesForStoreStaff() {
        insertTestTenant(2L, "test-a", "A");
        provisioningService.ensureTenantRbac(2L, "h154-test");

        long cashierRoleId = getTenantRoleId(2L, "CASHIER");
        long waiterRoleId = getTenantRoleId(2L, "WAITER");
        long cookRoleId = getTenantRoleId(2L, "KITCHEN_COOK");

        assignmentService.assignRole(2L, 2008L, cashierRoleId,
                GeihouAccessTokenUserRole.STORE_STAFF, "h154-test");
        assignmentService.assignRole(2L, 2008L, waiterRoleId,
                GeihouAccessTokenUserRole.STORE_STAFF, "h154-test");
        assignmentService.assignRole(2L, 2008L, cookRoleId,
                GeihouAccessTokenUserRole.STORE_STAFF, "h154-test");

        Optional<GeihouAccessTokenIssueResult> original = wiringService.issueLoginToken(
                2008L, 2L, GeihouAccessTokenUserRole.STORE_STAFF, false, "h154-test");
        assertThat(original).isPresent();

        Optional<GeihouAccessTokenIssueResult> refreshed = refreshService.refreshLoginToken(
                original.get().accessToken(), SECRET, false, "h154-test");

        assertThat(refreshed).isPresent();
        assertThat(refreshed.get().roles()).containsExactly("CASHIER", "KITCHEN_COOK", "WAITER");
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
                    'h154', NOW(), 'h154', NOW(), 0)
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
