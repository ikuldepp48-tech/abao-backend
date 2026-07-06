package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.module.system.api.auth.AuthApi;
import com.geihou.module.system.api.auth.dto.AuthTokenVerifyRespDTO;
import com.geihou.module.system.dal.dataobject.auth.AuthUserDO;
import com.geihou.module.system.dal.mysql.auth.AuthTokenRevokedRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import com.geihou.module.system.framework.jwt.GeihouJwtClaimNames;
import com.geihou.module.system.framework.jwt.GeihouJwtHeaderParser;
import com.geihou.module.system.framework.jwt.GeihouJwtTokenParser;
import com.geihou.module.system.framework.jwt.GeihouSigningSecret;
import com.geihou.module.system.framework.jwt.GeihouSigningSecretProvider;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GeihouAuthTokenVerifierTest {

    private static final Instant NOW = Instant.parse("2026-06-13T08:00:00Z");
    private static final String KID = "kid-1";
    private static final Long USER_ID = 100L;
    private static final Long TENANT_ID = 200L;
    private static final String USER_ROLE = "OWNER";
    private static final String TOKEN_ID = "jwt-id-1";

    private final GeihouJwtHeaderParser headerParser = new GeihouJwtHeaderParser();
    private final GeihouJwtTokenParser jwtTokenParser = new GeihouJwtTokenParser(
            "geihou-platform", Clock.fixed(NOW, ZoneOffset.UTC));
    private final AuthTokenRevokedRepository revokedRepository = mock(AuthTokenRevokedRepository.class);
    private final AuthUserRepository userRepository = mock(AuthUserRepository.class);

    @BeforeEach
    void setUp() {
        when(revokedRepository.existsByJti(TOKEN_ID)).thenReturn(false);
        when(userRepository.selectById(USER_ID)).thenReturn(authUser(TENANT_ID, USER_ROLE, "ACTIVE"));
    }

    @Test
    void shouldReturnSuccessForValidTokenWhenNotRevokedAndActiveUserMatches() {
        AuthTokenVerifyRespDTO response = verifier(providerFor(KID, secretBytes()))
                .verify(validToken(KID, secretBytes(), NOW.plusSeconds(3600), TENANT_ID, USER_ROLE));

        assertThat(response.getValid()).isTrue();
        assertThat(response.getUserId()).isEqualTo(USER_ID);
        assertThat(response.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(response.getTokenId()).isEqualTo(TOKEN_ID);
        assertThat(response.getUserRole()).isEqualTo(USER_ROLE);
    }

    @Test
    void shouldReturnInvalidWhenHeaderCannotBeParsed() {
        AuthTokenVerifyRespDTO response = verifier(providerFor(KID, secretBytes())).verify("not-a-token");

        assertInvalid(response);
        verify(revokedRepository, never()).existsByJti(anyString());
        verify(userRepository, never()).selectById(USER_ID);
    }

    @Test
    void shouldReturnInvalidWhenKidIsUnknownOrProviderThrows() {
        AuthTokenVerifyRespDTO unknownKid = verifier(kid -> Optional.empty())
                .verify(validToken(KID, secretBytes(), NOW.plusSeconds(3600), TENANT_ID, USER_ROLE));
        AuthTokenVerifyRespDTO providerFailure = verifier(kid -> {
            throw new IllegalStateException("provider unavailable");
        }).verify(validToken(KID, secretBytes(), NOW.plusSeconds(3600), TENANT_ID, USER_ROLE));

        assertInvalid(unknownKid);
        assertInvalid(providerFailure);
    }

    @Test
    void shouldReturnParserResultForInvalidOrExpiredJwt() {
        AuthTokenVerifyRespDTO invalidSignature = verifier(providerFor(KID, otherSecretBytes()))
                .verify(validToken(KID, secretBytes(), NOW.plusSeconds(3600), TENANT_ID, USER_ROLE));
        AuthTokenVerifyRespDTO expired = verifier(providerFor(KID, secretBytes()))
                .verify(validToken(KID, secretBytes(), NOW.minusSeconds(1), TENANT_ID, USER_ROLE));

        assertInvalid(invalidSignature);
        assertThat(expired.getValid()).isFalse();
        assertThat(expired.getErrorCode()).isEqualTo(GeihouAuthErrorCodes.TOKEN_EXPIRED);
    }

    @Test
    void shouldReturnRevokedWhenVerifiedJtiIsRevoked() {
        when(revokedRepository.existsByJti(TOKEN_ID)).thenReturn(true);

        AuthTokenVerifyRespDTO response = verifier(providerFor(KID, secretBytes()))
                .verify(validToken(KID, secretBytes(), NOW.plusSeconds(3600), TENANT_ID, USER_ROLE));

        assertThat(response.getValid()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(GeihouAuthErrorCodes.TOKEN_REVOKED);
        verify(userRepository, never()).selectById(USER_ID);
    }

    @Test
    void shouldReturnInvalidWhenVerifiedTokenIdIsBlank() {
        GeihouJwtTokenParser parserWithBlankTokenId = new GeihouJwtTokenParser(
                "geihou-platform", Clock.fixed(NOW, ZoneOffset.UTC)) {
            @Override
            public AuthTokenVerifyRespDTO verifyToken(String token, byte[] hmacSecretKey) {
                AuthTokenVerifyRespDTO response = successfulResponse();
                response.setTokenId(" ");
                return response;
            }
        };

        AuthTokenVerifyRespDTO response = verifier(providerFor(KID, secretBytes()), parserWithBlankTokenId)
                .verify(validToken(KID, secretBytes(), NOW.plusSeconds(3600), TENANT_ID, USER_ROLE));

        assertInvalid(response);
        verify(revokedRepository, never()).existsByJti(anyString());
        verify(userRepository, never()).selectById(USER_ID);
    }

    @Test
    void shouldReturnInvalidWhenUserIsMissingTenantMismatchRoleMismatchOrUnknownStatus() {
        assertInvalid(verifyWithUser(null));
        assertInvalid(verifyWithUser(authUser(999L, USER_ROLE, "ACTIVE")));
        assertInvalid(verifyWithUser(authUser(TENANT_ID, "CONSULTANT", "ACTIVE")));
        assertInvalid(verifyWithUser(authUser(TENANT_ID, USER_ROLE, "SUSPENDED")));
    }

    @Test
    void shouldReturnAccountStateErrorsForLockedAndDisabledUsers() {
        AuthTokenVerifyRespDTO locked = verifyWithUser(authUser(TENANT_ID, USER_ROLE, "LOCKED"));
        AuthTokenVerifyRespDTO disabled = verifyWithUser(authUser(TENANT_ID, USER_ROLE, "DISABLED"));

        assertThat(locked.getValid()).isFalse();
        assertThat(locked.getErrorCode()).isEqualTo(GeihouAuthErrorCodes.ACCOUNT_LOCKED);
        assertThat(disabled.getValid()).isFalse();
        assertThat(disabled.getErrorCode()).isEqualTo(GeihouAuthErrorCodes.ACCOUNT_DISABLED);
    }

    @Test
    void shouldNotShareMutableStateAcrossCalls() {
        GeihouAuthTokenVerifier verifier = verifier(providerFor(KID, secretBytes()));

        AuthTokenVerifyRespDTO first = verifier.verify(validToken(KID, secretBytes(), NOW.plusSeconds(3600),
                TENANT_ID, USER_ROLE));
        when(revokedRepository.existsByJti(TOKEN_ID)).thenReturn(true);
        AuthTokenVerifyRespDTO second = verifier.verify(validToken(KID, secretBytes(), NOW.plusSeconds(3600),
                TENANT_ID, USER_ROLE));

        assertThat(first.getValid()).isTrue();
        assertThat(second.getValid()).isFalse();
        assertThat(second.getErrorCode()).isEqualTo(GeihouAuthErrorCodes.TOKEN_REVOKED);
    }

    @Test
    void shouldNotBeRuntimeRegisteredOrImplementAuthApi() {
        assertThat(GeihouAuthTokenVerifier.class.getAnnotations()).isEmpty();
        assertThat(AuthApi.class.isAssignableFrom(GeihouAuthTokenVerifier.class)).isFalse();
    }

    private AuthTokenVerifyRespDTO verifyWithUser(AuthUserDO user) {
        when(userRepository.selectById(USER_ID)).thenReturn(user);
        return verifier(providerFor(KID, secretBytes()))
                .verify(validToken(KID, secretBytes(), NOW.plusSeconds(3600), TENANT_ID, USER_ROLE));
    }

    private GeihouAuthTokenVerifier verifier(GeihouSigningSecretProvider provider) {
        return verifier(provider, jwtTokenParser);
    }

    private GeihouAuthTokenVerifier verifier(GeihouSigningSecretProvider provider, GeihouJwtTokenParser parser) {
        return new GeihouAuthTokenVerifier(headerParser, provider, parser, revokedRepository, userRepository);
    }

    private static void assertInvalid(AuthTokenVerifyRespDTO response) {
        assertThat(response.getValid()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(GeihouAuthErrorCodes.INVALID_TOKEN);
    }

    private static GeihouSigningSecretProvider providerFor(String kid, byte[] secretBytes) {
        Map<String, GeihouSigningSecret> secrets = Map.of(
                kid, new GeihouSigningSecret(kid, "HS512", secretBytes));
        return keyId -> Optional.ofNullable(secrets.get(keyId));
    }

    private static AuthUserDO authUser(Long tenantId, String userRole, String status) {
        AuthUserDO user = new AuthUserDO();
        user.setId(USER_ID);
        user.setTenantId(tenantId);
        user.setUserRole(userRole);
        user.setStatus(status);
        user.setDeleted(false);
        return user;
    }

    private static AuthTokenVerifyRespDTO successfulResponse() {
        AuthTokenVerifyRespDTO response = new AuthTokenVerifyRespDTO();
        response.setValid(true);
        response.setUserId(USER_ID);
        response.setTenantId(TENANT_ID);
        response.setTokenId(TOKEN_ID);
        response.setAudience("admin");
        response.setUserRole(USER_ROLE);
        response.setRoles(List.of("OWNER"));
        return response;
    }

    private static String validToken(String kid, byte[] secret, Instant expiresAt, Long tenantId, String userRole) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("geihou-platform")
                .subject(USER_ID.toString())
                .audience("admin")
                .issueTime(Date.from(NOW.minusSeconds(60)))
                .expirationTime(Date.from(expiresAt))
                .jwtID(TOKEN_ID)
                .claim(GeihouJwtClaimNames.TENANT_ID, tenantId)
                .claim(GeihouJwtClaimNames.USER_ROLE, userRole)
                .claim(GeihouJwtClaimNames.ROLES, List.of("OWNER"))
                .build();
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS512).type(JOSEObjectType.JWT).keyID(kid).build(),
                claims);
        try {
            signedJWT.sign(new MACSigner(secret));
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to create test JWT", ex);
        }
        return signedJWT.serialize();
    }

    private static byte[] secretBytes() {
        byte[] secret = new byte[64];
        for (int i = 0; i < secret.length; i++) {
            secret[i] = (byte) (i + 1);
        }
        return secret;
    }

    private static byte[] otherSecretBytes() {
        byte[] secret = new byte[64];
        for (int i = 0; i < secret.length; i++) {
            secret[i] = (byte) (i + 65);
        }
        return secret;
    }
}
