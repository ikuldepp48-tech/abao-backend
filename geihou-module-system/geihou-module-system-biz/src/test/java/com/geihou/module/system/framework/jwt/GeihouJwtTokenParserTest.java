package com.geihou.module.system.framework.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.module.system.api.auth.dto.AuthTokenVerifyRespDTO;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeihouJwtTokenParserTest {

    private static final Instant NOW = Instant.parse("2026-06-13T08:00:00Z");

    private final byte[] secret = GeihouJwtTestTokenFactory.testSecret();
    private final GeihouJwtTokenParser parser =
            new GeihouJwtTokenParser("geihou-platform", Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void shouldVerifyValidHs512TokenAndMapClaims() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(GeihouJwtTestTokenFactory.validToken(NOW, secret), secret);

        assertThat(response.getValid()).isTrue();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getErrorMessage()).isNull();
        assertThat(response.getUserId()).isEqualTo(100L);
        assertThat(response.getUserName()).isNull();
        assertThat(response.getTenantId()).isEqualTo(200L);
        assertThat(response.getTokenId()).isEqualTo("jwt-id-1");
        assertThat(response.getAudience()).isEqualTo("admin");
        assertThat(response.getUserRole()).isEqualTo("OWNER");
        assertThat(response.getRoles()).containsExactly("OWNER", "SHOP_MANAGER");
    }

    @Test
    void shouldReturnInvalidTokenWhenMalformed() {
        AuthTokenVerifyRespDTO response = parser.verifyToken("not-a-jwt", secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenPayloadIsTampered() {
        String token = GeihouJwtTestTokenFactory.validToken(NOW, secret);
        String[] segments = token.split("\\.");
        String tamperedToken = segments[0] + "." + Base64URL.encode("{\"sub\":\"999\"}") + "." + segments[2];

        AuthTokenVerifyRespDTO response = parser.verifyToken(tamperedToken, secret);

        assertInvalid(response);
        assertThat(response.getErrorMessage()).doesNotContain(tamperedToken);
    }

    @Test
    void shouldReturnInvalidTokenWhenSignatureUsesDifferentSecret() {
        byte[] otherSecret = GeihouJwtTestTokenFactory.testSecret();
        otherSecret[0] = 99;

        AuthTokenVerifyRespDTO response = parser.verifyToken(GeihouJwtTestTokenFactory.validToken(NOW, secret), otherSecret);

        assertInvalid(response);
        assertThat(response.getErrorMessage()).doesNotContain("99");
    }

    @Test
    void shouldReturnInvalidTokenWhenAlgorithmIsNotHs512() {
        String token = tokenWithHeader(GeihouJwtTestTokenFactory.header(JWSAlgorithm.HS256, JOSEObjectType.JWT,
                GeihouJwtTestTokenFactory.KID));

        AuthTokenVerifyRespDTO response = parser.verifyToken(token, secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenTypeIsMissing() {
        String token = tokenWithHeader(GeihouJwtTestTokenFactory.header(JWSAlgorithm.HS512, null,
                GeihouJwtTestTokenFactory.KID));

        AuthTokenVerifyRespDTO response = parser.verifyToken(token, secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenTypeIsNotJwt() {
        String token = tokenWithHeader(GeihouJwtTestTokenFactory.header(JWSAlgorithm.HS512,
                new JOSEObjectType("NOT-JWT"), GeihouJwtTestTokenFactory.KID));

        AuthTokenVerifyRespDTO response = parser.verifyToken(token, secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenKidIsMissing() {
        String token = tokenWithHeader(GeihouJwtTestTokenFactory.header(JWSAlgorithm.HS512, JOSEObjectType.JWT, null));

        AuthTokenVerifyRespDTO response = parser.verifyToken(token, secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenIssuerIsWrong() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder -> builder.issuer("attacker")),
                secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnTokenExpiredWhenTokenIsExpired() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.expirationTime(Date.from(NOW.minusSeconds(1)))), secret);

        assertThat(response.getValid()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(GeihouAuthErrorCodes.TOKEN_EXPIRED);
        assertThat(response.getErrorMessage()).isEqualTo("Token expired");
    }

    @Test
    void shouldReturnInvalidTokenWhenIssuedAtIsFuture() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.issueTime(Date.from(NOW.plusSeconds(1)))), secret);

        assertInvalid(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"iss", "sub", "aud", "iat", "exp", "jti", "tenantId", "userRole", "roles"})
    void shouldReturnInvalidTokenWhenRequiredClaimIsMissing(String claimName) {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(claimsExcluding(claimName).build()), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenSubjectIsNotNumeric() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder -> builder.subject("abc")), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenTenantIdIsNotNumeric() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.claim(GeihouJwtClaimNames.TENANT_ID, "abc")), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenTenantIdIsDecimal() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.claim(GeihouJwtClaimNames.TENANT_ID, 200.5D)), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenAudienceIsMissing() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(claimsExcluding("aud").build()), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenAudienceHasMultipleValues() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.audience(List.of("admin", "platform"))), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenAudienceIsUnknown() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.audience("unknown")), secret);

        assertInvalid(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "PLATFORM_OPERATOR"})
    void shouldReturnInvalidTokenWhenUserRoleIsNotCanonical(String userRole) {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.claim(GeihouJwtClaimNames.USER_ROLE, userRole)), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenUserRoleAndAudienceMismatch() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.claim(GeihouJwtClaimNames.USER_ROLE, "PLATFORM_ADMIN")), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenTenantScopedRoleUsesZeroTenantId() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.claim(GeihouJwtClaimNames.TENANT_ID, 0L)), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenConsultantUsesNonZeroTenantId() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder -> builder
                .audience("consultant")
                .claim(GeihouJwtClaimNames.USER_ROLE, "CONSULTANT")), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenPlatformAdminUsesNonZeroTenantId() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder -> builder
                .audience("platform")
                .claim(GeihouJwtClaimNames.USER_ROLE, "PLATFORM_ADMIN")), secret);

        assertInvalid(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONSULTANT", "PLATFORM_ADMIN"})
    void shouldVerifyGlobalIdentityTokenWhenTenantIdIsZero(String userRole) {
        String audience = "CONSULTANT".equals(userRole) ? "consultant" : "platform";
        String rbacRoleCode = "CONSULTANT".equals(userRole) ? "CONSULTANT" : "PLATFORM_OPERATOR";

        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder -> builder
                .audience(audience)
                .claim(GeihouJwtClaimNames.TENANT_ID, 0L)
                .claim(GeihouJwtClaimNames.USER_ROLE, userRole)
                .claim(GeihouJwtClaimNames.ROLES, List.of(rbacRoleCode))), secret);

        assertThat(response.getValid()).isTrue();
        assertThat(response.getTenantId()).isZero();
        assertThat(response.getAudience()).isEqualTo(audience);
        assertThat(response.getUserRole()).isEqualTo(userRole);
        assertThat(response.getRoles()).containsExactly(rbacRoleCode);
    }

    @Test
    void shouldReturnInvalidTokenWhenRolesTypeIsWrong() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.claim(GeihouJwtClaimNames.ROLES, "OWNER")), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenRoleEntryIsBlank() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.claim(GeihouJwtClaimNames.ROLES, List.of("OWNER", " "))), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenRolesContainRolePrefix() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.claim(GeihouJwtClaimNames.ROLES, List.of("ROLE_OWNER"))), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenRolesContainLowercaseCode() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.claim(GeihouJwtClaimNames.ROLES, List.of("owner"))), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenRolesContainPermissionLikeCode() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.claim(GeihouJwtClaimNames.ROLES, List.of("ORDER:READ"))), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenRolesContainHyphenatedCode() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.claim(GeihouJwtClaimNames.ROLES, List.of("SHOP-MANAGER"))), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenRolesContainDuplicates() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.claim(GeihouJwtClaimNames.ROLES, List.of("OWNER", "OWNER"))), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWhenRolesContainMalformedUnderscoreCode() {
        AuthTokenVerifyRespDTO response = parser.verifyToken(tokenWithClaims(builder ->
                builder.claim(GeihouJwtClaimNames.ROLES, List.of("OWNER__ADMIN"))), secret);

        assertInvalid(response);
    }

    @Test
    void shouldReturnInvalidTokenWithoutLeakingSensitiveValues() {
        String token = tokenWithClaims(builder -> builder.subject("777"));

        AuthTokenVerifyRespDTO response = parser.verifyToken(token, null);

        assertInvalid(response);
        assertThat(response.getErrorMessage())
                .doesNotContain(token)
                .doesNotContain("777")
                .doesNotContain("OWNER")
                .doesNotContain("jwt-id-1");
    }

    private String tokenWithHeader(JWSHeader header) {
        return GeihouJwtTestTokenFactory.sign(header, GeihouJwtTestTokenFactory.validClaimsBuilder(NOW).build(), secret);
    }

    private String tokenWithClaims(Consumer<JWTClaimsSet.Builder> customizer) {
        JWTClaimsSet.Builder builder = GeihouJwtTestTokenFactory.validClaimsBuilder(NOW);
        customizer.accept(builder);
        return tokenWithClaims(builder.build());
    }

    private String tokenWithClaims(JWTClaimsSet claims) {
        return GeihouJwtTestTokenFactory.sign(
                GeihouJwtTestTokenFactory.header(JWSAlgorithm.HS512, JOSEObjectType.JWT, GeihouJwtTestTokenFactory.KID),
                claims,
                secret);
    }

    private JWTClaimsSet.Builder claimsExcluding(String excludedClaim) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        if (!GeihouJwtClaimNames.ISSUER.equals(excludedClaim)) {
            builder.issuer("geihou-platform");
        }
        if (!GeihouJwtClaimNames.SUBJECT.equals(excludedClaim)) {
            builder.subject("100");
        }
        if (!GeihouJwtClaimNames.AUDIENCE.equals(excludedClaim)) {
            builder.audience("admin");
        }
        if (!GeihouJwtClaimNames.ISSUED_AT.equals(excludedClaim)) {
            builder.issueTime(Date.from(NOW.minusSeconds(60)));
        }
        if (!GeihouJwtClaimNames.EXPIRES_AT.equals(excludedClaim)) {
            builder.expirationTime(Date.from(NOW.plusSeconds(3600)));
        }
        if (!GeihouJwtClaimNames.JWT_ID.equals(excludedClaim)) {
            builder.jwtID("jwt-id-1");
        }
        if (!GeihouJwtClaimNames.TENANT_ID.equals(excludedClaim)) {
            builder.claim(GeihouJwtClaimNames.TENANT_ID, 200L);
        }
        if (!GeihouJwtClaimNames.USER_ROLE.equals(excludedClaim)) {
            builder.claim(GeihouJwtClaimNames.USER_ROLE, "OWNER");
        }
        if (!GeihouJwtClaimNames.ROLES.equals(excludedClaim)) {
            builder.claim(GeihouJwtClaimNames.ROLES, List.of("OWNER", "SHOP_MANAGER"));
        }
        return builder;
    }

    private static void assertInvalid(AuthTokenVerifyRespDTO response) {
        assertThat(response.getValid()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(GeihouAuthErrorCodes.INVALID_TOKEN);
        assertThat(response.getErrorMessage()).isEqualTo("Token is invalid");
    }
}
