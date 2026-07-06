package com.geihou.module.system.framework.jwt;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;

import java.text.ParseException;
import java.util.Optional;

/**
 * Parses only untrusted JWT header metadata needed to select a signing secret.
 *
 * <p>This parser does not verify the signature and does not parse or trust claims.
 */
public class GeihouJwtHeaderParser {

    private static final String TYPE_JWT = "JWT";

    public Optional<GeihouJwtHeader> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            JWSHeader header = JWSObject.parse(token).getHeader();
            if (!JWSAlgorithm.HS512.equals(header.getAlgorithm())) {
                return Optional.empty();
            }
            JOSEObjectType type = header.getType();
            if (type == null || !TYPE_JWT.equalsIgnoreCase(type.getType())) {
                return Optional.empty();
            }
            String keyId = header.getKeyID();
            if (keyId == null || keyId.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new GeihouJwtHeader(keyId, header.getAlgorithm().getName(), type.getType()));
        } catch (ParseException | RuntimeException ex) {
            return Optional.empty();
        }
    }
}
