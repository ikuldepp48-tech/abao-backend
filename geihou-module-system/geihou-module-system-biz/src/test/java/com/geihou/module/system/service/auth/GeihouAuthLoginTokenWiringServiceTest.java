package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geihou.module.system.framework.jwt.GeihouAccessTokenAudience;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenIssuer;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenRequest;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import com.geihou.module.system.framework.jwt.GeihouActiveSigningSecretProvider;
import com.geihou.module.system.framework.jwt.GeihouJwtClaimNames;
import com.geihou.module.system.framework.jwt.GeihouJwtTokenParser;
import com.geihou.module.system.framework.jwt.GeihouSigningSecret;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Pure unit test for {@link GeihouAuthLoginTokenWiringService}.
 *
 * <p>No H2, no Spring context. Uses a stub read service and a recording issuer
 * to verify wiring semantics. Verifies:
 * <ul>
 *   <li>Non-empty roles success</li>
 *   <li>Empty roles deny / no issue call</li>
 *   <li>Issue service empty optional propagation</li>
 *   <li>Tenant scope fail deny / no issue call</li>
 *   <li>Permissions never in JWT</li>
 * </ul>
 */
class GeihouAuthLoginTokenWiringServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-19T02:00:00Z");
    private static final byte[] SECRET = secretBytes();
    private static final GeihouSigningSecret SIGNING_SECRET =
            new GeihouSigningSecret("kid-h152", "HS512", SECRET);

    // ---- Non-empty roles success ----

    @Test
    void shouldIssueTokenWhenRolesAreNonEmpty() {
        StubReadService readService = new StubReadService();
        readService.rolesToReturn = List.of("OWNER");
        RecordingIssuer issuer = new RecordingIssuer(Optional.of("fake-token"));
        GeihouAccessTokenIssueService issueService = new GeihouAccessTokenIssueService(issuer);
        GeihouAuthLoginTokenWiringService wiring =
                new GeihouAuthLoginTokenWiringService(readService, issueService);

        Optional<GeihouAccessTokenIssueResult> result = wiring.issueLoginToken(
                100L, 1L, GeihouAccessTokenUserRole.OWNER, false, "test-op");

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo(100L);
        assertThat(result.get().tenantId()).isEqualTo(1L);
        assertThat(result.get().userRole()).isEqualTo("OWNER");
        assertThat(result.get().roles()).containsExactly("OWNER");
        assertThat(readService.resolveCalls).isOne();
        assertThat(issuer.issueCalls).isOne();
    }

    @Test
    void shouldIssueTokenWithRealIssuerAndVerifyNoPermissionsInJwt() throws Exception {
        StubReadService readService = new StubReadService();
        readService.rolesToReturn = List.of("OWNER", "SHOP_MANAGER");
        GeihouAccessTokenIssueService issueService = new GeihouAccessTokenIssueService(realIssuer());
        GeihouAuthLoginTokenWiringService wiring =
                new GeihouAuthLoginTokenWiringService(readService, issueService);

        Optional<GeihouAccessTokenIssueResult> result = wiring.issueLoginToken(
                100L, 200L, GeihouAccessTokenUserRole.OWNER, false, "test-op");

        assertThat(result).isPresent();
        GeihouAccessTokenIssueResult token = result.get();
        assertThat(token.roles()).containsExactly("OWNER", "SHOP_MANAGER");
        assertThat(token.expiresInSeconds())
                .isEqualTo(GeihouAccessTokenAudience.ADMIN.ttl().toSeconds());

        // Parse the JWT and verify no permissions claim exists
        SignedJWT signedJwt = SignedJWT.parse(token.accessToken());
        JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
        assertThat(claims.getClaim("permissions")).isNull();
        assertThat(claims.getClaim("permission")).isNull();
        assertThat(claims.getStringListClaim("permissions")).isNull();

        // Verify expected claims are present
        assertThat(claims.getClaim(GeihouJwtClaimNames.TENANT_ID)).isEqualTo(200L);
        assertThat(claims.getClaim(GeihouJwtClaimNames.USER_ROLE)).isEqualTo("OWNER");
        assertThat(claims.getStringListClaim(GeihouJwtClaimNames.ROLES))
                .containsExactly("OWNER", "SHOP_MANAGER");

        // Verify token via parser
        var verified = new GeihouJwtTokenParser("geihou-platform",
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC))
                .verifyToken(token.accessToken(), SECRET);
        assertThat(verified.getValid()).isTrue();
        assertThat(verified.getRoles()).containsExactly("OWNER", "SHOP_MANAGER");
    }

    @Test
    void shouldPassStepUpVerifiedToCommandForConsultant() {
        StubReadService readService = new StubReadService();
        readService.rolesToReturn = List.of("CONSULTANT");
        RecordingIssuer issuer = new RecordingIssuer(Optional.of("consultant-token"));
        GeihouAccessTokenIssueService issueService = new GeihouAccessTokenIssueService(issuer);
        GeihouAuthLoginTokenWiringService wiring =
                new GeihouAuthLoginTokenWiringService(readService, issueService);

        // Without step-up → issue service is called but internally returns empty
        // (CONSULTANT requires step-up; the issue service short-circuits before the issuer)
        Optional<GeihouAccessTokenIssueResult> denied = wiring.issueLoginToken(
                101L, 0L, GeihouAccessTokenUserRole.CONSULTANT, false, "test-op");
        assertThat(denied).isEmpty();
        assertThat(readService.resolveCalls).isOne();
        assertThat(issuer.issueCalls).isZero(); // issue service short-circuits on step-up

        // With step-up → success, issuer called
        Optional<GeihouAccessTokenIssueResult> granted = wiring.issueLoginToken(
                101L, 0L, GeihouAccessTokenUserRole.CONSULTANT, true, "test-op");
        assertThat(granted).isPresent();
        assertThat(issuer.issueCalls).isOne();
    }

    // ---- Empty roles deny / no issue call ----

    @Test
    void shouldDenyAndNotCallIssueServiceWhenRolesAreEmpty() {
        StubReadService readService = new StubReadService();
        readService.rolesToReturn = List.of(); // empty roles
        RecordingIssuer issuer = new RecordingIssuer(Optional.of("should-not-be-used"));
        GeihouAccessTokenIssueService issueService = new GeihouAccessTokenIssueService(issuer);
        GeihouAuthLoginTokenWiringService wiring =
                new GeihouAuthLoginTokenWiringService(readService, issueService);

        Optional<GeihouAccessTokenIssueResult> result = wiring.issueLoginToken(
                100L, 1L, GeihouAccessTokenUserRole.OWNER, false, "test-op");

        assertThat(result).isEmpty();
        assertThat(readService.resolveCalls).isOne();
        assertThat(issuer.issueCalls).isZero(); // issue service NOT called
    }

    @Test
    void shouldDenyWhenReadServiceReturnsNullRoles() {
        StubReadService readService = new StubReadService();
        readService.rolesToReturn = null; // defensive null
        RecordingIssuer issuer = new RecordingIssuer(Optional.of("should-not-be-used"));
        GeihouAccessTokenIssueService issueService = new GeihouAccessTokenIssueService(issuer);
        GeihouAuthLoginTokenWiringService wiring =
                new GeihouAuthLoginTokenWiringService(readService, issueService);

        Optional<GeihouAccessTokenIssueResult> result = wiring.issueLoginToken(
                100L, 1L, GeihouAccessTokenUserRole.OWNER, false, "test-op");

        assertThat(result).isEmpty();
        assertThat(issuer.issueCalls).isZero();
    }

    // ---- Issue service empty optional ----

    @Test
    void shouldPropagateEmptyWhenIssueServiceReturnsEmpty() {
        StubReadService readService = new StubReadService();
        readService.rolesToReturn = List.of("OWNER");
        RecordingIssuer issuer = new RecordingIssuer(Optional.empty()); // issuer fails
        GeihouAccessTokenIssueService issueService = new GeihouAccessTokenIssueService(issuer);
        GeihouAuthLoginTokenWiringService wiring =
                new GeihouAuthLoginTokenWiringService(readService, issueService);

        Optional<GeihouAccessTokenIssueResult> result = wiring.issueLoginToken(
                100L, 1L, GeihouAccessTokenUserRole.OWNER, false, "test-op");

        assertThat(result).isEmpty();
        assertThat(readService.resolveCalls).isOne();
        assertThat(issuer.issueCalls).isOne(); // issue service WAS called but returned empty
    }

    // ---- Tenant scope fail ----

    @Test
    void shouldDenyAndNotCallReadModelOrIssueServiceWhenTenantScopeMismatches() {
        StubReadService readService = new StubReadService();
        readService.rolesToReturn = List.of("OWNER"); // should never be used
        RecordingIssuer issuer = new RecordingIssuer(Optional.of("should-not-be-used"));
        GeihouAccessTokenIssueService issueService = new GeihouAccessTokenIssueService(issuer);
        GeihouAuthLoginTokenWiringService wiring =
                new GeihouAuthLoginTokenWiringService(readService, issueService);

        // OWNER is tenant scope (tenantId > 0), tenantId=0 should fail
        Optional<GeihouAccessTokenIssueResult> result = wiring.issueLoginToken(
                100L, 0L, GeihouAccessTokenUserRole.OWNER, false, "test-op");

        assertThat(result).isEmpty();
        assertThat(readService.resolveCalls).isZero(); // read model NOT called
        assertThat(issuer.issueCalls).isZero(); // issue service NOT called
    }

    @Test
    void shouldDenyWhenConsultantUsesNonZeroTenantId() {
        StubReadService readService = new StubReadService();
        readService.rolesToReturn = List.of("CONSULTANT");
        RecordingIssuer issuer = new RecordingIssuer(Optional.of("should-not-be-used"));
        GeihouAccessTokenIssueService issueService = new GeihouAccessTokenIssueService(issuer);
        GeihouAuthLoginTokenWiringService wiring =
                new GeihouAuthLoginTokenWiringService(readService, issueService);

        // CONSULTANT is platform scope (tenantId = 0), tenantId=1 should fail
        Optional<GeihouAccessTokenIssueResult> result = wiring.issueLoginToken(
                101L, 1L, GeihouAccessTokenUserRole.CONSULTANT, true, "test-op");

        assertThat(result).isEmpty();
        assertThat(readService.resolveCalls).isZero();
        assertThat(issuer.issueCalls).isZero();
    }

    // ---- Parameter validation ----

    @Test
    void shouldRejectNonPositiveUserId() {
        StubReadService readService = new StubReadService();
        RecordingIssuer issuer = new RecordingIssuer(Optional.empty());
        GeihouAuthLoginTokenWiringService wiring =
                new GeihouAuthLoginTokenWiringService(readService,
                        new GeihouAccessTokenIssueService(issuer));

        assertThatThrownBy(() -> wiring.issueLoginToken(
                0L, 1L, GeihouAccessTokenUserRole.OWNER, false, "op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
        assertThatThrownBy(() -> wiring.issueLoginToken(
                -1L, 1L, GeihouAccessTokenUserRole.OWNER, false, "op"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeTenantId() {
        StubReadService readService = new StubReadService();
        GeihouAuthLoginTokenWiringService wiring =
                new GeihouAuthLoginTokenWiringService(readService,
                        new GeihouAccessTokenIssueService(new RecordingIssuer(Optional.empty())));

        assertThatThrownBy(() -> wiring.issueLoginToken(
                1L, -1L, GeihouAccessTokenUserRole.OWNER, false, "op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void shouldRejectNullUserRole() {
        StubReadService readService = new StubReadService();
        GeihouAuthLoginTokenWiringService wiring =
                new GeihouAuthLoginTokenWiringService(readService,
                        new GeihouAccessTokenIssueService(new RecordingIssuer(Optional.empty())));

        assertThatThrownBy(() -> wiring.issueLoginToken(
                1L, 1L, null, false, "op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userRole");
    }

    @Test
    void shouldRejectBlankOperator() {
        StubReadService readService = new StubReadService();
        GeihouAuthLoginTokenWiringService wiring =
                new GeihouAuthLoginTokenWiringService(readService,
                        new GeihouAccessTokenIssueService(new RecordingIssuer(Optional.empty())));

        assertThatThrownBy(() -> wiring.issueLoginToken(
                1L, 1L, GeihouAccessTokenUserRole.OWNER, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator");
        assertThatThrownBy(() -> wiring.issueLoginToken(
                1L, 1L, GeihouAccessTokenUserRole.OWNER, false, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wiring.issueLoginToken(
                1L, 1L, GeihouAccessTokenUserRole.OWNER, false, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Permissions never in JWT (source guard) ----

    @Test
    void wiringServiceMustNotAcceptOrPropagatePermissions() {
        // The wiring service method signature must not have a permissions parameter
        // and the GeihouAccessTokenIssueCommand must not have a permissions field.
        assertThat(GeihouAuthLoginTokenWiringService.class.getDeclaredMethods())
                .as("Wiring service must have exactly one public method: issueLoginToken")
                .hasSize(1);

        // Verify GeihouJwtClaimNames has no PERMISSIONS constant
        assertThat(GeihouJwtClaimNames.class.getDeclaredFields())
                .as("GeihouJwtClaimNames must not define a PERMISSIONS constant")
                .noneMatch(field -> field.getName().toUpperCase().contains("PERMISSION"));

        // Verify GeihouAccessTokenIssueCommand has no permissions field
        assertThat(GeihouAccessTokenIssueCommand.class.getDeclaredFields())
                .as("Issue command must not carry permissions")
                .noneMatch(field -> field.getName().toLowerCase().contains("permission"));
    }

    @Test
    void wiringServiceMustNotBeControllerOrApi() {
        assertThat(GeihouAuthLoginTokenWiringService.class.getAnnotations())
                .as("Wiring service must not have Spring annotations (no @RestController/@Controller/@Service)")
                .isEmpty();
    }

    // ---- Multiple roles success ----

    @Test
    void shouldIssueTokenForStoreStaffWithMultipleRoles() {
        StubReadService readService = new StubReadService();
        readService.rolesToReturn = List.of("CASHIER", "KITCHEN_COOK", "WAITER");
        RecordingIssuer issuer = new RecordingIssuer(Optional.of("staff-token"));
        GeihouAccessTokenIssueService issueService = new GeihouAccessTokenIssueService(issuer);
        GeihouAuthLoginTokenWiringService wiring =
                new GeihouAuthLoginTokenWiringService(readService, issueService);

        Optional<GeihouAccessTokenIssueResult> result = wiring.issueLoginToken(
                200L, 1L, GeihouAccessTokenUserRole.STORE_STAFF, false, "test-op");

        assertThat(result).isPresent();
        assertThat(result.get().roles()).containsExactly("CASHIER", "KITCHEN_COOK", "WAITER");
        assertThat(result.get().userRole()).isEqualTo("STORE_STAFF");
    }

    @Test
    void shouldIssueTokenForPlatformAdminWithStepUp() {
        StubReadService readService = new StubReadService();
        readService.rolesToReturn = List.of("PLATFORM_OPERATOR", "PLATFORM_DEVELOPER");
        RecordingIssuer issuer = new RecordingIssuer(Optional.of("platform-token"));
        GeihouAccessTokenIssueService issueService = new GeihouAccessTokenIssueService(issuer);
        GeihouAuthLoginTokenWiringService wiring =
                new GeihouAuthLoginTokenWiringService(readService, issueService);

        Optional<GeihouAccessTokenIssueResult> result = wiring.issueLoginToken(
                300L, 0L, GeihouAccessTokenUserRole.PLATFORM_ADMIN, true, "test-op");

        assertThat(result).isPresent();
        assertThat(result.get().roles()).containsExactly("PLATFORM_DEVELOPER", "PLATFORM_OPERATOR");
        assertThat(result.get().tenantId()).isEqualTo(0L);
    }

    // ---- Helpers ----

    private static GeihouAccessTokenIssuer realIssuer() {
        GeihouActiveSigningSecretProvider provider = () -> Optional.of(SIGNING_SECRET);
        return new GeihouAccessTokenIssuer(provider, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static byte[] secretBytes() {
        byte[] secret = new byte[64];
        for (int i = 0; i < secret.length; i++) {
            secret[i] = (byte) (i + 1);
        }
        return secret;
    }

    /**
     * Stub read service that records calls and returns a configurable role list.
     */
    private static final class StubReadService extends GeihouAuthRolePermissionReadService {

        int resolveCalls = 0;
        List<String> rolesToReturn = List.of();

        StubReadService() {
            super(null, null, null, null);
        }

        @Override
        public List<String> resolveActiveRoleCodes(Long tenantId, Long userId,
                                                    GeihouAccessTokenUserRole userRole) {
            resolveCalls++;
            return rolesToReturn;
        }
    }

    /**
     * Recording issuer that extends the real issuer to count issue() calls.
     */
    private static final class RecordingIssuer extends GeihouAccessTokenIssuer {

        private final Optional<String> token;
        int issueCalls = 0;

        RecordingIssuer(Optional<String> token) {
            super(() -> Optional.empty());
            this.token = token;
        }

        @Override
        public Optional<String> issue(GeihouAccessTokenRequest request) {
            issueCalls++;
            return token;
        }
    }
}
