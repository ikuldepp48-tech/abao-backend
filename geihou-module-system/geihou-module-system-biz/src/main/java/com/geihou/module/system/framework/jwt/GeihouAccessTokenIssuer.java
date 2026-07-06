package com.geihou.module.system.framework.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure Java access-token issuer for PRD 0-05.
 */
public class GeihouAccessTokenIssuer {

    private static final String DEFAULT_ISSUER = "geihou-platform";

    private final GeihouActiveSigningSecretProvider activeSigningSecretProvider;
    private final Clock clock;

    public GeihouAccessTokenIssuer(GeihouActiveSigningSecretProvider activeSigningSecretProvider) {
        this(activeSigningSecretProvider, Clock.systemUTC());
    }

    public GeihouAccessTokenIssuer(GeihouActiveSigningSecretProvider activeSigningSecretProvider, Clock clock) {
        this.activeSigningSecretProvider = Objects.requireNonNull(
                activeSigningSecretProvider, "activeSigningSecretProvider must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Optional<String> issue(GeihouAccessTokenRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        try {
            Optional<GeihouSigningSecret> activeSecret = activeSigningSecretProvider.current();
            if (activeSecret.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(sign(request, activeSecret.get()));
        } catch (JOSEException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    private String sign(GeihouAccessTokenRequest request, GeihouSigningSecret activeSecret) throws JOSEException {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(request.audience().ttl());

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(DEFAULT_ISSUER)
                .subject(Long.toString(request.userId()))
                .audience(request.audience().value())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim(GeihouJwtClaimNames.TENANT_ID, request.tenantId())
                .claim(GeihouJwtClaimNames.USER_ROLE, request.userRole())
                .claim(GeihouJwtClaimNames.ROLES, request.roles())
                .build();

        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS512)
                        .type(JOSEObjectType.JWT)
                        .keyID(activeSecret.kid())
                        .build(),
                claims);
        signedJwt.sign(new MACSigner(activeSecret.keyBytes()));
        return signedJwt.serialize();
    }
}
