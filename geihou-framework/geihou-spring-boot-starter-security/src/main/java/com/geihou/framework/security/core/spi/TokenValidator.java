package com.geihou.framework.security.core.spi;

/**
 * Validates a raw Bearer token and returns a Geihou principal result.
 */
@FunctionalInterface
public interface TokenValidator {

    TokenValidationResult validate(String token);
}
