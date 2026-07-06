package com.geihou.module.system.framework.jwt;

import java.util.Optional;

/**
 * Provides the active signing secret for issuer-side JWT creation.
 */
public interface GeihouActiveSigningSecretProvider {

    Optional<GeihouSigningSecret> current();
}
