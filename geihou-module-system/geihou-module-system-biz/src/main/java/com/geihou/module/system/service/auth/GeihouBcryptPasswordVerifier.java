package com.geihou.module.system.service.auth;

import org.springframework.security.crypto.bcrypt.BCrypt;

/**
 * BCrypt password verifier port for PRD 0-05.
 *
 * <p>H157D adds the explicit Spring Security Crypto dependency so this verifier
 * can use BCrypt directly instead of reflective lookup.
 */
public class GeihouBcryptPasswordVerifier implements GeihouAdminLoginAuthenticationService.PasswordVerifierPort {

    private static final String BCRYPT_PREFIX = "$2";

    @Override
    public boolean verify(String rawPassword, String passwordHash, String passwordSalt) {
        if (rawPassword == null || rawPassword.isBlank() || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        if (!passwordHash.startsWith(BCRYPT_PREFIX)) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, passwordHash);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
