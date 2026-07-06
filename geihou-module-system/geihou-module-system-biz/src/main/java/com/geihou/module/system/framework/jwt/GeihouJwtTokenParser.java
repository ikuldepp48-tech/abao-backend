package com.geihou.module.system.framework.jwt;

import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.module.system.api.auth.dto.AuthTokenVerifyRespDTO;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Pure HS512 JWT parser/validator for the first PRD 0-05 auth runtime slice.
 */
public class GeihouJwtTokenParser {

    private static final String DEFAULT_ISSUER = "geihou-platform";
    private static final String TYPE_JWT = "JWT";
    private static final String INVALID_TOKEN_MESSAGE = "Token is invalid";
    private static final String TOKEN_EXPIRED_MESSAGE = "Token expired";

    private final String expectedIssuer;
    private final Clock clock;

    public GeihouJwtTokenParser() {
        this(DEFAULT_ISSUER, Clock.systemUTC());
    }

    public GeihouJwtTokenParser(String expectedIssuer, Clock clock) {
        this.expectedIssuer = Objects.requireNonNull(expectedIssuer, "expectedIssuer must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public AuthTokenVerifyRespDTO verifyToken(String token, byte[] hmacSecretKey) {
        if (token == null || token.isBlank() || hmacSecretKey == null || hmacSecretKey.length == 0) {
            return invalid();
        }

        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            if (!hasValidHeader(signedJwt)) {
                return invalid();
            }
            if (!signedJwt.verify(new MACVerifier(hmacSecretKey))) {
                return invalid();
            }
            return verifyClaims(signedJwt.getJWTClaimsSet());
        } catch (JOSEException | ParseException | RuntimeException ex) {
            return invalid();
        }
    }

    private static boolean hasValidHeader(SignedJWT signedJwt) {
        if (!JWSAlgorithm.HS512.equals(signedJwt.getHeader().getAlgorithm())) {
            return false;
        }
        JOSEObjectType type = signedJwt.getHeader().getType();
        if (type == null || !TYPE_JWT.equalsIgnoreCase(type.getType())) {
            return false;
        }
        String keyId = signedJwt.getHeader().getKeyID();
        return keyId != null && !keyId.isBlank();
    }

    private AuthTokenVerifyRespDTO verifyClaims(JWTClaimsSet claims) throws ParseException {
        Date expiresAt = claims.getExpirationTime();
        if (expiresAt == null) {
            return invalid();
        }
        Instant now = clock.instant();
        if (!expiresAt.toInstant().isAfter(now)) {
            return expired();
        }

        Date issuedAt = claims.getIssueTime();
        if (issuedAt == null || issuedAt.toInstant().isAfter(now)) {
            return invalid();
        }

        if (!expectedIssuer.equals(requiredString(claims, GeihouJwtClaimNames.ISSUER))) {
            return invalid();
        }

        List<String> audience = claims.getAudience();
        if (audience == null || audience.size() != 1 || audience.get(0) == null || audience.get(0).isBlank()) {
            return invalid();
        }
        String audienceValue = audience.get(0);
        GeihouAccessTokenAudience accessTokenAudience = GeihouAccessTokenAudience.fromValue(audienceValue)
                .orElseThrow(() -> new ParseException("Invalid JWT claim", 0));
        Long tenantId = requiredLong(claims, GeihouJwtClaimNames.TENANT_ID);
        String userRole = requiredString(claims, GeihouJwtClaimNames.USER_ROLE);
        validateRoleAudienceTenant(userRole, accessTokenAudience, tenantId);
        List<String> roles = requiredRoles(claims);

        AuthTokenVerifyRespDTO response = new AuthTokenVerifyRespDTO();
        response.setValid(true);
        response.setUserId(requiredLong(claims, GeihouJwtClaimNames.SUBJECT));
        response.setUserName(null);
        response.setTenantId(tenantId);
        response.setTokenId(requiredString(claims, GeihouJwtClaimNames.JWT_ID));
        response.setAudience(accessTokenAudience.value());
        response.setUserRole(userRole);
        response.setRoles(roles);
        return response;
    }

    private static void validateRoleAudienceTenant(String userRole,
                                                   GeihouAccessTokenAudience audience,
                                                   long tenantId) throws ParseException {
        GeihouAccessTokenUserRole canonicalRole = GeihouAccessTokenUserRole.fromCode(userRole)
                .orElseThrow(() -> new ParseException("Invalid JWT claim", 0));
        if (!GeihouAccessTokenAudienceMapper.supports(userRole, audience)
                || !canonicalRole.acceptsTenantId(tenantId)) {
            throw new ParseException("Invalid JWT claim", 0);
        }
    }

    private static String requiredString(JWTClaimsSet claims, String name) throws ParseException {
        Object value = claims.getClaim(name);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        throw new ParseException("Invalid JWT claim", 0);
    }

    private static Long requiredLong(JWTClaimsSet claims, String name) throws ParseException {
        Object value = claims.getClaim(name);
        if (value instanceof Number numberValue) {
            try {
                return Long.parseLong(numberValue.toString());
            } catch (NumberFormatException ex) {
                throw new ParseException("Invalid JWT claim", 0);
            }
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException ex) {
                throw new ParseException("Invalid JWT claim", 0);
            }
        }
        throw new ParseException("Invalid JWT claim", 0);
    }

    private static List<String> requiredRoles(JWTClaimsSet claims) throws ParseException {
        List<String> roles = claims.getStringListClaim(GeihouJwtClaimNames.ROLES);
        if (roles == null) {
            throw new ParseException("Invalid JWT claim", 0);
        }
        return GeihouRbacRoleCodes.normalize(roles);
    }

    private static AuthTokenVerifyRespDTO invalid() {
        AuthTokenVerifyRespDTO response = new AuthTokenVerifyRespDTO();
        response.setValid(false);
        response.setErrorCode(GeihouAuthErrorCodes.INVALID_TOKEN);
        response.setErrorMessage(INVALID_TOKEN_MESSAGE);
        return response;
    }

    private static AuthTokenVerifyRespDTO expired() {
        AuthTokenVerifyRespDTO response = new AuthTokenVerifyRespDTO();
        response.setValid(false);
        response.setErrorCode(GeihouAuthErrorCodes.TOKEN_EXPIRED);
        response.setErrorMessage(TOKEN_EXPIRED_MESSAGE);
        return response;
    }
}
