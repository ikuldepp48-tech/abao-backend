package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Pure unit tests for H154 refresh service-layer wiring.
 */
class GeihouAuthLoginTokenRefreshServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-19T03:00:00Z");
    private static final byte[] SECRET = secretBytes();
    private static final GeihouSigningSecret SIGNING_SECRET =
            new GeihouSigningSecret("kid-h154", "HS512", SECRET);

    @Test
    void shouldIgnoreOldRolesAndIssueWithFreshRoles() throws Exception {
        String oldToken = issueOldToken(100L, 1L, GeihouAccessTokenUserRole.OWNER, List.of("OWNER"), false);
        StubReadService readService = new StubReadService(List.of("SHOP_MANAGER"));
        GeihouAuthLoginTokenRefreshService refreshService = newRefreshService(readService, realIssueService());

        Optional<GeihouAccessTokenIssueResult> result =
                refreshService.refreshLoginToken(oldToken, SECRET, false, "h154");

        assertThat(result).isPresent();
        assertThat(result.get().roles()).containsExactly("SHOP_MANAGER");
        SignedJWT jwt = SignedJWT.parse(result.get().accessToken());
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getStringListClaim(GeihouJwtClaimNames.ROLES)).containsExactly("SHOP_MANAGER");
        assertThat(readService.resolveCalls).isOne();
    }

    @Test
    void shouldDenyFreshEmptyRolesAndNotCallIssuer() {
        String oldToken = issueOldToken(100L, 1L, GeihouAccessTokenUserRole.OWNER, List.of("OWNER"), false);
        StubReadService readService = new StubReadService(List.of());
        RecordingIssuer issuer = new RecordingIssuer(Optional.of("should-not-be-used"));
        GeihouAuthLoginTokenRefreshService refreshService =
                newRefreshService(readService, new GeihouAccessTokenIssueService(issuer));

        Optional<GeihouAccessTokenIssueResult> result =
                refreshService.refreshLoginToken(oldToken, SECRET, false, "h154");

        assertThat(result).isEmpty();
        assertThat(readService.resolveCalls).isOne();
        assertThat(issuer.issueCalls).isZero();
    }

    @Test
    void shouldDenyInvalidTokenAndNotCallReadModelOrIssuer() {
        StubReadService readService = new StubReadService(List.of("OWNER"));
        RecordingIssuer issuer = new RecordingIssuer(Optional.of("should-not-be-used"));
        GeihouAuthLoginTokenRefreshService refreshService =
                newRefreshService(readService, new GeihouAccessTokenIssueService(issuer));

        Optional<GeihouAccessTokenIssueResult> result =
                refreshService.refreshLoginToken("not-a-jwt", SECRET, false, "h154");

        assertThat(result).isEmpty();
        assertThat(readService.resolveCalls).isZero();
        assertThat(issuer.issueCalls).isZero();
    }

    @Test
    void shouldDenyWhenSecretDoesNotVerify() {
        String oldToken = issueOldToken(100L, 1L, GeihouAccessTokenUserRole.OWNER, List.of("OWNER"), false);
        StubReadService readService = new StubReadService(List.of("OWNER"));
        RecordingIssuer issuer = new RecordingIssuer(Optional.of("should-not-be-used"));
        GeihouAuthLoginTokenRefreshService refreshService =
                newRefreshService(readService, new GeihouAccessTokenIssueService(issuer));

        Optional<GeihouAccessTokenIssueResult> result =
                refreshService.refreshLoginToken(oldToken, differentSecret(), false, "h154");

        assertThat(result).isEmpty();
        assertThat(readService.resolveCalls).isZero();
        assertThat(issuer.issueCalls).isZero();
    }

    @Test
    void shouldDenyConsultantWithoutFreshStepUpAndSucceedWithStepUp() {
        String oldToken = issueOldToken(200L, 0L, GeihouAccessTokenUserRole.CONSULTANT,
                List.of("CONSULTANT"), true);
        StubReadService readService = new StubReadService(List.of("CONSULTANT"));
        GeihouAuthLoginTokenRefreshService refreshService = newRefreshService(readService, realIssueService());

        Optional<GeihouAccessTokenIssueResult> denied =
                refreshService.refreshLoginToken(oldToken, SECRET, false, "h154");
        Optional<GeihouAccessTokenIssueResult> issued =
                refreshService.refreshLoginToken(oldToken, SECRET, true, "h154");

        assertThat(denied).isEmpty();
        assertThat(issued).isPresent();
        assertThat(issued.get().roles()).containsExactly("CONSULTANT");
    }

    @Test
    void shouldAllowTenantScopeRefreshWithoutStepUp() {
        String oldToken = issueOldToken(300L, 1L, GeihouAccessTokenUserRole.STORE_STAFF,
                List.of("CASHIER"), false);
        StubReadService readService = new StubReadService(List.of("WAITER"));
        GeihouAuthLoginTokenRefreshService refreshService = newRefreshService(readService, realIssueService());

        Optional<GeihouAccessTokenIssueResult> result =
                refreshService.refreshLoginToken(oldToken, SECRET, false, "h154");

        assertThat(result).isPresent();
        assertThat(result.get().roles()).containsExactly("WAITER");
    }

    @Test
    void shouldNotExposePermissionsInSignatureOrJwt() throws Exception {
        assertThat(GeihouAuthLoginTokenRefreshService.class.getDeclaredMethods())
                .as("Refresh service exposes only refreshLoginToken")
                .hasSize(1);
        String oldToken = issueOldToken(100L, 1L, GeihouAccessTokenUserRole.OWNER, List.of("OWNER"), false);
        GeihouAuthLoginTokenRefreshService refreshService =
                newRefreshService(new StubReadService(List.of("OWNER")), realIssueService());

        Optional<GeihouAccessTokenIssueResult> result =
                refreshService.refreshLoginToken(oldToken, SECRET, false, "h154");

        assertThat(result).isPresent();
        JWTClaimsSet claims = SignedJWT.parse(result.get().accessToken()).getJWTClaimsSet();
        assertThat(claims.getClaim("permissions")).isNull();
        assertThat(claims.getClaim("permission")).isNull();
    }

    @Test
    void shouldRejectInvalidParameters() {
        GeihouAuthLoginTokenRefreshService refreshService =
                newRefreshService(new StubReadService(List.of("OWNER")), realIssueService());

        assertThatThrownBy(() -> refreshService.refreshLoginToken("", SECRET, false, "h154"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("oldToken");
        assertThatThrownBy(() -> refreshService.refreshLoginToken("token", new byte[0], false, "h154"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hmacSecretKey");
        assertThatThrownBy(() -> refreshService.refreshLoginToken("token", SECRET, false, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator");
    }

    private static GeihouAuthLoginTokenRefreshService newRefreshService(
            GeihouAuthRolePermissionReadService readService,
            GeihouAccessTokenIssueService issueService) {
        return new GeihouAuthLoginTokenRefreshService(
                readService,
                issueService,
                new GeihouJwtTokenParser("geihou-platform", Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC)));
    }

    private static GeihouAccessTokenIssueService realIssueService() {
        return new GeihouAccessTokenIssueService(realIssuer(NOW.plusSeconds(2)));
    }

    private static String issueOldToken(long userId, long tenantId, GeihouAccessTokenUserRole userRole,
                                        List<String> roles, boolean stepUpVerified) {
        GeihouAccessTokenIssueService issueService =
                new GeihouAccessTokenIssueService(realIssuer(NOW));
        return issueService.issue(new GeihouAccessTokenIssueCommand(
                        userId, tenantId, userRole.code(), roles, stepUpVerified))
                .orElseThrow()
                .accessToken();
    }

    private static GeihouAccessTokenIssuer realIssuer(Instant now) {
        GeihouActiveSigningSecretProvider provider = () -> Optional.of(SIGNING_SECRET);
        return new GeihouAccessTokenIssuer(provider, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static byte[] secretBytes() {
        byte[] secret = new byte[64];
        for (int i = 0; i < secret.length; i++) {
            secret[i] = (byte) (i + 1);
        }
        return secret;
    }

    private static byte[] differentSecret() {
        byte[] secret = new byte[64];
        for (int i = 0; i < secret.length; i++) {
            secret[i] = (byte) (64 - i);
        }
        return secret;
    }

    private static final class StubReadService extends GeihouAuthRolePermissionReadService {

        private final List<String> rolesToReturn;
        int resolveCalls;

        StubReadService(List<String> rolesToReturn) {
            super(null, null, null, null);
            this.rolesToReturn = rolesToReturn;
        }

        @Override
        public List<String> resolveActiveRoleCodes(Long tenantId, Long userId,
                                                    GeihouAccessTokenUserRole userRole) {
            resolveCalls++;
            return rolesToReturn;
        }
    }

    private static final class RecordingIssuer extends GeihouAccessTokenIssuer {

        private final Optional<String> token;
        int issueCalls;

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
