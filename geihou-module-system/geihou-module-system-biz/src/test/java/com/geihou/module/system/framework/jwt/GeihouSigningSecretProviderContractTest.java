package com.geihou.module.system.framework.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeihouSigningSecretProviderContractTest {

    @Test
    void shouldDefensivelyCopyInputAndOutputKeyBytes() {
        byte[] source = secretBytes();
        GeihouSigningSecret secret = new GeihouSigningSecret("kid-1", "HS512", source);

        source[0] = 99;
        byte[] firstRead = secret.keyBytes();
        firstRead[1] = 88;

        assertThat(secret.keyBytes()[0]).isEqualTo((byte) 1);
        assertThat(secret.keyBytes()[1]).isEqualTo((byte) 2);
    }

    @Test
    void shouldRedactKeyBytesInToString() {
        GeihouSigningSecret secret = new GeihouSigningSecret("kid-1", "HS512", secretBytes());

        assertThat(secret.toString())
                .contains("kid-1")
                .contains("HS512")
                .contains("<redacted>")
                .doesNotContain(Arrays.toString(secretBytes()));
    }

    @Test
    void shouldRejectBlankKidWrongAlgorithmAndEmptyKeyBytes() {
        assertThatThrownBy(() -> new GeihouSigningSecret(" ", "HS512", secretBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeihouSigningSecret("kid-1", "HS256", secretBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeihouSigningSecret("kid-1", "HS512", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeihouSigningSecret("kid-1", "HS512", new byte[63]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldResolveKnownKidAndFailClosedForUnknownKid() {
        InMemorySigningSecretProvider provider = new InMemorySigningSecretProvider(Map.of(
                "kid-1", new GeihouSigningSecret("kid-1", "HS512", secretBytes())));

        Optional<GeihouSigningSecret> secret = provider.resolve("kid-1");

        assertThat(secret).isPresent();
        assertThat(secret.get().kid()).isEqualTo("kid-1");
        assertThat(provider.resolve("unknown")).isEmpty();
        assertThat(provider.resolve(null)).isEmpty();
        assertThat(provider.resolve(" ")).isEmpty();
    }

    @Test
    void shouldNotExposeMutableInternalSecretAcrossCalls() {
        InMemorySigningSecretProvider provider = new InMemorySigningSecretProvider(Map.of(
                "kid-1", new GeihouSigningSecret("kid-1", "HS512", secretBytes())));

        byte[] first = provider.resolve("kid-1").orElseThrow().keyBytes();
        first[0] = 99;

        assertThat(provider.resolve("kid-1").orElseThrow().keyBytes()[0]).isEqualTo((byte) 1);
    }

    private static byte[] secretBytes() {
        byte[] bytes = new byte[64];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i + 1);
        }
        return bytes;
    }

    private record InMemorySigningSecretProvider(Map<String, GeihouSigningSecret> secrets)
            implements GeihouSigningSecretProvider {

        @Override
        public Optional<GeihouSigningSecret> resolve(String kid) {
            if (kid == null || kid.isBlank()) {
                return Optional.empty();
            }
            return Optional.ofNullable(secrets.get(kid));
        }
    }
}
