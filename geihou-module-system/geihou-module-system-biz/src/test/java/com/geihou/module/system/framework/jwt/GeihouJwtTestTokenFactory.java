package com.geihou.module.system.framework.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Date;
import java.util.List;

final class GeihouJwtTestTokenFactory {

    static final String KID = "test-key-1";

    private GeihouJwtTestTokenFactory() {
    }

    static byte[] testSecret() {
        byte[] secret = new byte[64];
        for (int i = 0; i < secret.length; i++) {
            secret[i] = (byte) (i + 1);
        }
        return secret;
    }

    static String validToken(Instant now, byte[] secret) {
        return sign(header(JWSAlgorithm.HS512, JOSEObjectType.JWT, KID), validClaimsBuilder(now).build(), secret);
    }

    static JWTClaimsSet.Builder validClaimsBuilder(Instant now) {
        return new JWTClaimsSet.Builder()
                .issuer("geihou-platform")
                .subject("100")
                .audience("admin")
                .issueTime(Date.from(now.minusSeconds(60)))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .jwtID("jwt-id-1")
                .claim(GeihouJwtClaimNames.TENANT_ID, 200L)
                .claim(GeihouJwtClaimNames.USER_ROLE, "OWNER")
                .claim(GeihouJwtClaimNames.ROLES, List.of("OWNER", "SHOP_MANAGER"));
    }

    static JWSHeader header(JWSAlgorithm algorithm, JOSEObjectType type, String keyId) {
        JWSHeader.Builder builder = new JWSHeader.Builder(algorithm);
        if (type != null) {
            builder.type(type);
        }
        if (keyId != null) {
            builder.keyID(keyId);
        }
        return builder.build();
    }

    static String sign(JWSHeader header, JWTClaimsSet claims, byte[] secret) {
        SignedJWT signedJWT = new SignedJWT(header, claims);
        try {
            signedJWT.sign(new MACSigner(secret));
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to create test JWT", ex);
        }
        return signedJWT.serialize();
    }
}
