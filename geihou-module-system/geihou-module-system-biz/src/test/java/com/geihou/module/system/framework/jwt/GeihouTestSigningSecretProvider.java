package com.geihou.module.system.framework.jwt;

import java.util.Map;
import java.util.Optional;

final class GeihouTestSigningSecretProvider implements GeihouSigningSecretProvider {

    private final Map<String, GeihouSigningSecret> secrets = Map.of(
            GeihouJwtTestTokenFactory.KID,
            new GeihouSigningSecret(GeihouJwtTestTokenFactory.KID, "HS512",
                    GeihouJwtTestTokenFactory.testSecret()));

    @Override
    public Optional<GeihouSigningSecret> resolve(String kid) {
        if (kid == null || kid.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(secrets.get(kid));
    }
}
