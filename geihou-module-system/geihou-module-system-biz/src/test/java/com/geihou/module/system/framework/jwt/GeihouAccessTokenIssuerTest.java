package com.geihou.module.system.framework.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.module.system.api.auth.dto.AuthTokenVerifyRespDTO;
import com.geihou.module.system.dal.mysql.auth.AuthTokenRevokedRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import com.geihou.module.system.service.auth.GeihouAuthTokenVerifier;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GeihouAccessTokenIssuerTest {

    private static final Instant NOW = Instant.parse("2026-06-14T09:30:00Z");

    private final GeihouSigningSecret activeSecret =
            new GeihouSigningSecret("active-kid-202606", "HS512", activeSecretBytes());
    private final GeihouAccessTokenRequest request = new GeihouAccessTokenRequest(
            100L,
            200L,
            GeihouAccessTokenAudience.ADMIN,
            "OWNER",
            List.of("OWNER", "SHOP_MANAGER"));
    private final GeihouAccessTokenIssuer issuer = new GeihouAccessTokenIssuer(
            () -> Optional.of(activeSecret),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @ParameterizedTest
    @MethodSource("audienceTtls")
    void shouldIssueAccessTokenWithPrdHeaderClaimsAndTtl(
            GeihouAccessTokenAudience audience, Duration expectedTtl) throws ParseException {
        GeihouAccessTokenRequest audienceRequest = new GeihouAccessTokenRequest(
                100L,
                tenantIdFor(audience),
                audience,
                userRoleFor(audience),
                rolesFor(audience));

        Optional<String> issuedToken = issuer.issue(audienceRequest);

        assertThat(issuedToken).isPresent();
        SignedJWT signedJwt = SignedJWT.parse(issuedToken.get());
        assertThat(signedJwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS512);
        assertThat(signedJwt.getHeader().getType()).isEqualTo(JOSEObjectType.JWT);
        assertThat(signedJwt.getHeader().getKeyID()).isEqualTo(activeSecret.kid());

        JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("geihou-platform");
        assertThat(claims.getSubject()).isEqualTo("100");
        assertThat(claims.getAudience()).containsExactly(audience.value());
        assertThat(claims.getIssueTime()).isEqualTo(Date.from(NOW));
        assertThat(claims.getExpirationTime()).isEqualTo(Date.from(NOW.plus(expectedTtl)));
        assertThat(UUID.fromString(claims.getJWTID())).isNotNull();
        assertThat(claims.getLongClaim(GeihouJwtClaimNames.TENANT_ID)).isEqualTo(tenantIdFor(audience));
        assertThat(claims.getStringClaim(GeihouJwtClaimNames.USER_ROLE)).isEqualTo(userRoleFor(audience));
        assertThat(claims.getStringListClaim(GeihouJwtClaimNames.ROLES))
                .containsExactlyElementsOf(rolesFor(audience));
        assertThat(claims.getClaim("permissions")).isNull();

        Optional<GeihouJwtHeader> parsedHeader = new GeihouJwtHeaderParser().parse(issuedToken.get());
        assertThat(parsedHeader).isPresent();
        assertThat(parsedHeader.get().kid()).isEqualTo(activeSecret.kid());
        assertThat(parsedHeader.get().algorithm()).isEqualTo("HS512");
        assertThat(parsedHeader.get().type()).isEqualTo("JWT");

        GeihouJwtTokenParser parser = new GeihouJwtTokenParser(
                "geihou-platform",
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
        AuthTokenVerifyRespDTO verified = parser.verifyToken(issuedToken.get(), activeSecret.keyBytes());
        assertThat(verified.getValid()).isTrue();
        assertThat(verified.getUserId()).isEqualTo(100L);
        assertThat(verified.getTenantId()).isEqualTo(tenantIdFor(audience));
        assertThat(verified.getAudience()).isEqualTo(audience.value());
        assertThat(verified.getUserRole()).isEqualTo(userRoleFor(audience));
        assertThat(verified.getRoles()).containsExactlyElementsOf(rolesFor(audience));
    }

    @Test
    void shouldReturnEmptyWhenActiveSecretIsMissing() {
        GeihouAccessTokenIssuer missingSecretIssuer = new GeihouAccessTokenIssuer(
                Optional::empty,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(missingSecretIssuer.issue(request)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenActiveSecretProviderFails() {
        GeihouAccessTokenIssuer failingIssuer = new GeihouAccessTokenIssuer(
                () -> {
                    throw new IllegalStateException("active secret unavailable");
                },
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(failingIssuer.issue(request)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForMissingRequest() {
        assertThat(issuer.issue(null)).isEmpty();
    }

    @Test
    void shouldFailExistingVerifierWhenWrongSecretIsUsed() {
        String token = issuer.issue(request).orElseThrow();
        byte[] wrongSecret = activeSecretBytes();
        wrongSecret[0] = 99;

        AuthTokenVerifyRespDTO verified = new GeihouJwtTokenParser(
                "geihou-platform",
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC))
                .verifyToken(token, wrongSecret);

        assertThat(verified.getValid()).isFalse();
        assertThat(verified.getErrorCode()).isEqualTo(GeihouAuthErrorCodes.INVALID_TOKEN);
    }

    @Test
    void shouldFailExistingVerifierWhenIssuedTokenKidIsUnknown() {
        AuthTokenRevokedRepository revokedRepository = mock(AuthTokenRevokedRepository.class);
        AuthUserRepository userRepository = mock(AuthUserRepository.class);
        GeihouAuthTokenVerifier verifier = new GeihouAuthTokenVerifier(
                new GeihouJwtHeaderParser(),
                kid -> Optional.empty(),
                new GeihouJwtTokenParser("geihou-platform", Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC)),
                revokedRepository,
                userRepository);

        AuthTokenVerifyRespDTO verified = verifier.verify(issuer.issue(request).orElseThrow());

        assertThat(verified.getValid()).isFalse();
        assertThat(verified.getErrorCode()).isEqualTo(GeihouAuthErrorCodes.INVALID_TOKEN);
        verifyNoInteractions(revokedRepository, userRepository);
    }

    @Test
    void shouldRejectInvalidAccessTokenRequest() {
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                0L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("OWNER")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                -1L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("OWNER")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 0L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("OWNER")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, -1L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("OWNER")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, null, "OWNER", List.of("OWNER")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, " ", List.of("OWNER")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("OWNER", " ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectRolePrefixedMalformedLowercasePermissionLikeAndDuplicateRoleCodes() {
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("ROLE_OWNER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_");
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("owner")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("ORDER:READ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("SHOP-MANAGER")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("OWNER", "OWNER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicates");
    }

    @Test
    void shouldIssueTokenWithRawRoleCodesSurvivingIssuerToParserRoundTrip() {
        GeihouAccessTokenRequest rawRequest = new GeihouAccessTokenRequest(
                100L,
                200L,
                GeihouAccessTokenAudience.ADMIN,
                "OWNER",
                List.of("SHOP_MANAGER", "OWNER", "CASHIER"));

        Optional<String> issuedToken = issuer.issue(rawRequest);

        assertThat(issuedToken).isPresent();
        GeihouJwtTokenParser roundTripParser = new GeihouJwtTokenParser(
                "geihou-platform",
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
        AuthTokenVerifyRespDTO verified = roundTripParser.verifyToken(issuedToken.get(), activeSecret.keyBytes());
        assertThat(verified.getValid()).isTrue();
        assertThat(verified.getRoles()).containsExactly("CASHIER", "OWNER", "SHOP_MANAGER");
    }

    @Test
    void shouldResolveAudienceFromPrdValue() {
        assertThat(GeihouAccessTokenAudience.fromValue("customer"))
                .hasValue(GeihouAccessTokenAudience.CUSTOMER);
        assertThat(GeihouAccessTokenAudience.fromValue("staff"))
                .hasValue(GeihouAccessTokenAudience.STAFF);
        assertThat(GeihouAccessTokenAudience.fromValue("admin"))
                .hasValue(GeihouAccessTokenAudience.ADMIN);
        assertThat(GeihouAccessTokenAudience.fromValue("consultant"))
                .hasValue(GeihouAccessTokenAudience.CONSULTANT);
        assertThat(GeihouAccessTokenAudience.fromValue("platform"))
                .hasValue(GeihouAccessTokenAudience.PLATFORM);
        assertThat(GeihouAccessTokenAudience.fromValue("manager")).isEmpty();
        assertThat(GeihouAccessTokenAudience.fromValue(null)).isEmpty();
        assertThat(GeihouAccessTokenAudience.fromValue(" ")).isEmpty();
    }

    private static Stream<Arguments> audienceTtls() {
        return Stream.of(
                Arguments.of(GeihouAccessTokenAudience.CUSTOMER, Duration.ofMinutes(15)),
                Arguments.of(GeihouAccessTokenAudience.STAFF, Duration.ofHours(8)),
                Arguments.of(GeihouAccessTokenAudience.ADMIN, Duration.ofHours(8)),
                Arguments.of(GeihouAccessTokenAudience.CONSULTANT, Duration.ofHours(4)),
                Arguments.of(GeihouAccessTokenAudience.PLATFORM, Duration.ofHours(2)));
    }

    private static long tenantIdFor(GeihouAccessTokenAudience audience) {
        return switch (audience) {
            case CONSULTANT, PLATFORM -> 0L;
            case CUSTOMER, STAFF, ADMIN -> 200L;
        };
    }

    private static String userRoleFor(GeihouAccessTokenAudience audience) {
        return switch (audience) {
            case CUSTOMER -> "CUSTOMER";
            case STAFF -> "STORE_STAFF";
            case ADMIN -> "OWNER";
            case CONSULTANT -> "CONSULTANT";
            case PLATFORM -> "PLATFORM_ADMIN";
        };
    }

    private static List<String> rolesFor(GeihouAccessTokenAudience audience) {
        return switch (audience) {
            case CUSTOMER -> List.of("CUSTOMER");
            case STAFF -> List.of("CASHIER");
            case ADMIN -> List.of("OWNER", "SHOP_MANAGER");
            case CONSULTANT -> List.of("CONSULTANT");
            case PLATFORM -> List.of("PLATFORM_OPERATOR");
        };
    }

    private static byte[] activeSecretBytes() {
        byte[] secret = new byte[64];
        for (int i = 0; i < secret.length; i++) {
            secret[i] = (byte) (64 - i);
        }
        return secret;
    }
}
