package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geihou.module.system.api.auth.dto.AuthTokenVerifyRespDTO;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenAudience;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenIssuer;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenRequest;
import com.geihou.module.system.framework.jwt.GeihouActiveSigningSecretProvider;
import com.geihou.module.system.framework.jwt.GeihouJwtTokenParser;
import com.geihou.module.system.framework.jwt.GeihouSigningSecret;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeihouAccessTokenIssueServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-15T02:00:00Z");
    private static final byte[] SECRET = secretBytes();
    private static final GeihouSigningSecret SIGNING_SECRET = new GeihouSigningSecret("kid-h137", "HS512", SECRET);

    @Test
    void shouldIssueOwnerAdminAccessTokenForAlreadyAuthenticatedInput() {
        GeihouAccessTokenIssueService service = new GeihouAccessTokenIssueService(realIssuer());

        Optional<GeihouAccessTokenIssueResult> issued = service.issue(new GeihouAccessTokenIssueCommand(
                100L,
                200L,
                "OWNER",
                List.of("OWNER", "SHOP_MANAGER"),
                false));

        assertThat(issued).isPresent();
        GeihouAccessTokenIssueResult result = issued.get();
        assertThat(result.expiresInSeconds()).isEqualTo(GeihouAccessTokenAudience.ADMIN.ttl().toSeconds());
        assertThat(result.userId()).isEqualTo(100L);
        assertThat(result.tenantId()).isEqualTo(200L);
        assertThat(result.userRole()).isEqualTo("OWNER");
        assertThat(result.roles()).containsExactly("OWNER", "SHOP_MANAGER");

        AuthTokenVerifyRespDTO verified = parser().verifyToken(result.accessToken(), SECRET);
        assertThat(verified.getValid()).isTrue();
        assertThat(verified.getAudience()).isEqualTo("admin");
        assertThat(verified.getUserRole()).isEqualTo("OWNER");
        assertThat(verified.getTenantId()).isEqualTo(200L);
    }

    @Test
    void shouldDeriveAudienceFromUserRole() {
        GeihouAccessTokenIssueService service = new GeihouAccessTokenIssueService(realIssuer());

        GeihouAccessTokenIssueResult consultant = service.issue(new GeihouAccessTokenIssueCommand(
                101L,
                0L,
                "CONSULTANT",
                List.of("CONSULTANT"),
                true)).orElseThrow();
        GeihouAccessTokenIssueResult platform = service.issue(new GeihouAccessTokenIssueCommand(
                102L,
                0L,
                "PLATFORM_ADMIN",
                List.of("PLATFORM_OPERATOR"),
                true)).orElseThrow();

        assertThat(consultant.expiresInSeconds()).isEqualTo(GeihouAccessTokenAudience.CONSULTANT.ttl().toSeconds());
        assertThat(platform.expiresInSeconds()).isEqualTo(GeihouAccessTokenAudience.PLATFORM.ttl().toSeconds());
        assertThat(parser().verifyToken(consultant.accessToken(), SECRET).getAudience()).isEqualTo("consultant");
        assertThat(parser().verifyToken(platform.accessToken(), SECRET).getAudience()).isEqualTo("platform");
    }

    @Test
    void shouldReturnEmptyWhenIssuerReturnsEmpty() {
        RecordingIssuer issuer = new RecordingIssuer(Optional.empty());
        GeihouAccessTokenIssueService service = new GeihouAccessTokenIssueService(issuer);

        Optional<GeihouAccessTokenIssueResult> issued = service.issue(new GeihouAccessTokenIssueCommand(
                100L,
                200L,
                "OWNER",
                List.of("OWNER"),
                false));

        assertThat(issued).isEmpty();
        assertThat(issuer.calls()).isOne();
    }

    @Test
    void shouldRejectTenantScopeViolationBeforeCallingIssuer() {
        RecordingIssuer issuer = new RecordingIssuer(Optional.of("unused-token"));
        GeihouAccessTokenIssueService service = new GeihouAccessTokenIssueService(issuer);

        Optional<GeihouAccessTokenIssueResult> issued = service.issue(new GeihouAccessTokenIssueCommand(
                100L,
                0L,
                "OWNER",
                List.of("OWNER"),
                false));

        assertThat(issued).isEmpty();
        assertThat(issuer.calls()).isZero();
    }

    @Test
    void shouldCopyRolesDefensively() {
        GeihouAccessTokenIssueService service = new GeihouAccessTokenIssueService(realIssuer());
        List<String> roles = new ArrayList<>(List.of("OWNER"));
        GeihouAccessTokenIssueCommand command = new GeihouAccessTokenIssueCommand(100L, 200L, "OWNER", roles, false);
        roles.add("MUTATED_ROLE");

        GeihouAccessTokenIssueResult result = service.issue(command).orElseThrow();

        assertThat(command.roles()).containsExactly("OWNER");
        assertThat(result.roles()).containsExactly("OWNER");
        assertThatThrownBy(() -> result.roles().add("NEW_ROLE"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectInvalidCommandFields() {
        assertThatThrownBy(() -> new GeihouAccessTokenIssueCommand(0L, 200L, "OWNER", List.of("OWNER"), false))
                .hasMessageContaining("userId");
        assertThatThrownBy(() -> new GeihouAccessTokenIssueCommand(100L, -1L, "OWNER", List.of("OWNER"), false))
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new GeihouAccessTokenIssueCommand(100L, 200L, "STAFF", List.of("OWNER"), false))
                .hasMessageContaining("canonical ENUM_USER_ROLE");
        assertThatThrownBy(() -> new GeihouAccessTokenIssueCommand(100L, 200L, "OWNER", List.of(), false))
                .hasMessageContaining("roles");
        assertThatThrownBy(() -> new GeihouAccessTokenIssueCommand(
                100L, 200L, "OWNER", Arrays.asList("OWNER", " "), false))
                .hasMessageContaining("roles");
    }

    @Test
    void shouldRejectRolePrefixedMalformedLowercasePermissionLikeAndDuplicateRoleCodesInCommand() {
        // ROLE_ prefixed codes must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenIssueCommand(
                100L, 200L, "OWNER", List.of("ROLE_OWNER"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_");
        // Lowercase codes must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenIssueCommand(
                100L, 200L, "OWNER", List.of("owner"), false))
                .isInstanceOf(IllegalArgumentException.class);
        // Colon permission-like values must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenIssueCommand(
                100L, 200L, "OWNER", List.of("ORDER:READ"), false))
                .isInstanceOf(IllegalArgumentException.class);
        // Hyphenated codes must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenIssueCommand(
                100L, 200L, "OWNER", List.of("SHOP-MANAGER"), false))
                .isInstanceOf(IllegalArgumentException.class);
        // Duplicates must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenIssueCommand(
                100L, 200L, "OWNER", List.of("OWNER", "OWNER"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicates");
        // Malformed underscore codes must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenIssueCommand(
                100L, 200L, "OWNER", List.of("OWNER__ADMIN"), false))
                .isInstanceOf(IllegalArgumentException.class);
        // Null list must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenIssueCommand(
                100L, 200L, "OWNER", null, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectInvalidRoleCodesInIssueResultConstructor() {
        // ROLE_ prefixed codes must be rejected by the result constructor.
        assertThatThrownBy(() -> new GeihouAccessTokenIssueResult(
                "token", 3600L, 100L, 200L, "OWNER", List.of("ROLE_OWNER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_");
        // Lowercase codes must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenIssueResult(
                "token", 3600L, 100L, 200L, "OWNER", List.of("owner")))
                .isInstanceOf(IllegalArgumentException.class);
        // Colon permission-like values must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenIssueResult(
                "token", 3600L, 100L, 200L, "OWNER", List.of("ORDER:READ")))
                .isInstanceOf(IllegalArgumentException.class);
        // Duplicates must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenIssueResult(
                "token", 3600L, 100L, 200L, "OWNER", List.of("OWNER", "OWNER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicates");
        // Empty list must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenIssueResult(
                "token", 3600L, 100L, 200L, "OWNER", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void shouldNormalizeRoleCodesInIssueResultConstructor() {
        GeihouAccessTokenIssueResult result = new GeihouAccessTokenIssueResult(
                "token", 3600L, 100L, 200L, "OWNER", List.of("SHOP_MANAGER", "OWNER", "CASHIER"));

        assertThat(result.roles()).containsExactly("CASHIER", "OWNER", "SHOP_MANAGER");
        assertThatThrownBy(() -> result.roles().add("WAITER"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectConsultantAndPlatformTokensWithoutStepUpVerificationBeforeCallingIssuer() {
        RecordingIssuer issuer = new RecordingIssuer(Optional.of("unused-token"));
        GeihouAccessTokenIssueService service = new GeihouAccessTokenIssueService(issuer);

        Optional<GeihouAccessTokenIssueResult> consultant = service.issue(new GeihouAccessTokenIssueCommand(
                101L,
                0L,
                "CONSULTANT",
                List.of("CONSULTANT"),
                false));
        Optional<GeihouAccessTokenIssueResult> platform = service.issue(new GeihouAccessTokenIssueCommand(
                102L,
                0L,
                "PLATFORM_ADMIN",
                List.of("PLATFORM_OPERATOR"),
                false));

        assertThat(consultant).isEmpty();
        assertThat(platform).isEmpty();
        assertThat(issuer.calls()).isZero();
    }

    @Test
    void shouldExposeNoRefreshTokenOrPermissionsFields() {
        assertThat(GeihouAccessTokenIssueResult.class.getDeclaredFields())
                .extracting(field -> field.getName().toLowerCase())
                .noneMatch(name -> name.contains("refreshtoken") || name.contains("permission"));
        assertThat(GeihouAccessTokenIssueResult.class.getDeclaredMethods())
                .extracting(method -> method.getName().toLowerCase())
                .noneMatch(name -> name.contains("refreshtoken") || name.contains("permission"));
    }

    @Test
    void shouldNotBeSpringControllerOrApi() {
        assertThat(GeihouAccessTokenIssueService.class.getAnnotations()).isEmpty();
        assertThat(GeihouAccessTokenIssueCommand.class.getAnnotations()).isEmpty();
        assertThat(GeihouAccessTokenIssueResult.class.getAnnotations()).isEmpty();
    }

    private static GeihouAccessTokenIssuer realIssuer() {
        GeihouActiveSigningSecretProvider activeSigningSecretProvider = () -> Optional.of(SIGNING_SECRET);
        return new GeihouAccessTokenIssuer(activeSigningSecretProvider, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static GeihouJwtTokenParser parser() {
        return new GeihouJwtTokenParser("geihou-platform", Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
    }

    private static byte[] secretBytes() {
        byte[] secret = new byte[64];
        for (int i = 0; i < secret.length; i++) {
            secret[i] = (byte) (i + 1);
        }
        return secret;
    }

    private static final class RecordingIssuer extends GeihouAccessTokenIssuer {

        private final Optional<String> token;
        private int calls;

        private RecordingIssuer(Optional<String> token) {
            super(() -> Optional.empty());
            this.token = token;
        }

        @Override
        public Optional<String> issue(GeihouAccessTokenRequest request) {
            calls++;
            return token;
        }

        private int calls() {
            return calls;
        }
    }
}
