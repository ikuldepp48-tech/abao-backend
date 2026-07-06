package com.geihou.module.system.framework.jwt;

import java.util.Optional;

/**
 * Resolves signing secrets by JWT key id.
 *
 * <p>Implementations should fail closed by returning {@link Optional#empty()} for null, blank, or unknown key ids.
 */
public interface GeihouSigningSecretProvider {

    Optional<GeihouSigningSecret> resolve(String kid);
}
