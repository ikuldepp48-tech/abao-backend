package com.geihou.module.system.framework.jwt;

import java.util.Optional;

/**
 * Provides the active signing key id for issuer-side JWT creation.
 */
public interface GeihouActiveKidProvider {

    Optional<String> currentKid();
}
