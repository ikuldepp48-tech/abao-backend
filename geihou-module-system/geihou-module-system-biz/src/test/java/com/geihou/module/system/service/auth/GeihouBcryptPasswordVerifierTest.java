package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;

class GeihouBcryptPasswordVerifierTest {

    private final GeihouBcryptPasswordVerifier verifier = new GeihouBcryptPasswordVerifier();

    @Test
    void shouldFailClosedForBlankInputs() {
        assertThat(verifier.verify(null, "$2a$10$hash", null)).isFalse();
        assertThat(verifier.verify("password", null, null)).isFalse();
        assertThat(verifier.verify(" ", "$2a$10$hash", null)).isFalse();
        assertThat(verifier.verify("password", " ", null)).isFalse();
    }

    @Test
    void shouldRejectNonBcryptHashWithoutFallback() {
        assertThat(verifier.verify("password", "plain-or-md5-hash", "salt")).isFalse();
        assertThat(verifier.verify("password", "{noop}password", null)).isFalse();
    }

    @Test
    void shouldVerifyBcryptHash() {
        String hash = BCrypt.hashpw("correct-password", BCrypt.gensalt());

        assertThat(verifier.verify("correct-password", hash, "legacy-salt")).isTrue();
        assertThat(verifier.verify("wrong-password", hash, "legacy-salt")).isFalse();
    }
}
