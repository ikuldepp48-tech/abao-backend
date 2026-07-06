package com.geihou.module.system.framework.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeihouTestSigningSecretProviderTest {

    private final GeihouTestSigningSecretProvider provider = new GeihouTestSigningSecretProvider();

    @Test
    void shouldResolveKnownTestKidWithHs512Secret() {
        GeihouSigningSecret secret = provider.resolve(GeihouJwtTestTokenFactory.KID).orElseThrow();

        assertThat(secret.kid()).isEqualTo(GeihouJwtTestTokenFactory.KID);
        assertThat(secret.algorithm()).isEqualTo("HS512");
        assertThat(secret.keyBytes()).hasSize(64);
        assertThat(secret.toString()).contains("keyBytes=<redacted>");
    }

    @Test
    void shouldFailClosedForBlankOrUnknownKid() {
        assertThat(provider.resolve(null)).isEmpty();
        assertThat(provider.resolve("")).isEmpty();
        assertThat(provider.resolve(" ")).isEmpty();
        assertThat(provider.resolve("unknown-key")).isEmpty();
    }

    @Test
    void shouldNotExposeMutableSecretState() {
        byte[] first = provider.resolve(GeihouJwtTestTokenFactory.KID).orElseThrow().keyBytes();
        first[0] = 99;

        byte[] second = provider.resolve(GeihouJwtTestTokenFactory.KID).orElseThrow().keyBytes();

        assertThat(second[0]).isEqualTo(GeihouJwtTestTokenFactory.testSecret()[0]);
    }
}
