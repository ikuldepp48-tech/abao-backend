package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class EnvTwoFactorSecretKeyProviderTest {

    private static final String ENV_VAR = "GEIHOU_2FA_AES_KEY";
    private static final byte[] KEY_16 = bytes(16);
    private static final byte[] KEY_24 = bytes(24);
    private static final byte[] KEY_32 = bytes(32);

    @Test
    void shouldReadAndReturnKeyForValid16ByteKey() {
        Function<String, String> env = name -> ENV_VAR.equals(name)
                ? Base64.getEncoder().encodeToString(KEY_16) : null;

        EnvTwoFactorSecretKeyProvider provider = new EnvTwoFactorSecretKeyProvider(ENV_VAR, env);

        assertThat(provider.currentKey()).containsExactly(KEY_16);
    }

    @Test
    void shouldReadAndReturnKeyForValid24ByteKey() {
        Function<String, String> env = name -> ENV_VAR.equals(name)
                ? Base64.getEncoder().encodeToString(KEY_24) : null;

        EnvTwoFactorSecretKeyProvider provider = new EnvTwoFactorSecretKeyProvider(ENV_VAR, env);

        assertThat(provider.currentKey()).containsExactly(KEY_24);
    }

    @Test
    void shouldReadAndReturnKeyForValid32ByteKey() {
        Function<String, String> env = name -> ENV_VAR.equals(name)
                ? Base64.getEncoder().encodeToString(KEY_32) : null;

        EnvTwoFactorSecretKeyProvider provider = new EnvTwoFactorSecretKeyProvider(ENV_VAR, env);

        assertThat(provider.currentKey()).containsExactly(KEY_32);
    }

    @Test
    void shouldReturnDefensiveCopyNotInternalArray() {
        Function<String, String> env = name -> ENV_VAR.equals(name)
                ? Base64.getEncoder().encodeToString(KEY_16) : null;

        EnvTwoFactorSecretKeyProvider provider = new EnvTwoFactorSecretKeyProvider(ENV_VAR, env);
        byte[] first = provider.currentKey();
        first[0] = (byte) 0xFF;

        assertThat(provider.currentKey()).containsExactly(KEY_16);
    }

    @Test
    void shouldFailFastWhenEnvVarIsMissing() {
        Function<String, String> env = name -> null;

        assertThatThrownBy(() -> new EnvTwoFactorSecretKeyProvider(ENV_VAR, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2FA AES key environment variable is missing or blank")
                .hasMessageContaining(ENV_VAR);
    }

    @Test
    void shouldFailFastWhenEnvVarIsBlank() {
        Function<String, String> env = name -> "   ";

        assertThatThrownBy(() -> new EnvTwoFactorSecretKeyProvider(ENV_VAR, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing or blank");
    }

    @Test
    void shouldFailFastWhenEnvVarIsNotValidBase64() {
        Function<String, String> env = name -> "!!!not-base64!!!";

        assertThatThrownBy(() -> new EnvTwoFactorSecretKeyProvider(ENV_VAR, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid Base64");
    }

    @Test
    void shouldFailFastWhenDecodedKeyIs15Bytes() {
        Function<String, String> env = name -> ENV_VAR.equals(name)
                ? Base64.getEncoder().encodeToString(bytes(15)) : null;

        assertThatThrownBy(() -> new EnvTwoFactorSecretKeyProvider(ENV_VAR, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16, 24, or 32 bytes")
                .hasMessageContaining("15");
    }

    @Test
    void shouldFailFastWhenDecodedKeyIs33Bytes() {
        Function<String, String> env = name -> ENV_VAR.equals(name)
                ? Base64.getEncoder().encodeToString(bytes(33)) : null;

        assertThatThrownBy(() -> new EnvTwoFactorSecretKeyProvider(ENV_VAR, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16, 24, or 32 bytes")
                .hasMessageContaining("33");
    }

    @Test
    void shouldRejectBlankEnvVarName() {
        Function<String, String> env = name -> "irrelevant";

        assertThatThrownBy(() -> new EnvTwoFactorSecretKeyProvider("  ", env))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aes-key-env must not be blank");
    }

    @Test
    void shouldRejectNullEnvVarName() {
        Function<String, String> env = name -> "irrelevant";

        assertThatThrownBy(() -> new EnvTwoFactorSecretKeyProvider(null, env))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aes-key-env must not be blank");
    }

    @Test
    void shouldRejectNullEnvLookup() {
        assertThatThrownBy(() -> new EnvTwoFactorSecretKeyProvider(ENV_VAR, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("envLookup");
    }

    @Test
    void shouldHandleWhitespaceAroundBase64Value() {
        Function<String, String> env = name -> ENV_VAR.equals(name)
                ? "  " + Base64.getEncoder().encodeToString(KEY_16) + "  " : null;

        EnvTwoFactorSecretKeyProvider provider = new EnvTwoFactorSecretKeyProvider(ENV_VAR, env);

        assertThat(provider.currentKey()).containsExactly(KEY_16);
    }

    @Test
    void toStringShouldNotExposeKeyMaterial() {
        Function<String, String> env = name -> ENV_VAR.equals(name)
                ? Base64.getEncoder().encodeToString(KEY_32) : null;

        EnvTwoFactorSecretKeyProvider provider = new EnvTwoFactorSecretKeyProvider(ENV_VAR, env);

        String str = provider.toString();
        assertThat(str).contains("redacted");
        assertThat(str).doesNotContain(new String(KEY_32));
    }

    private static byte[] bytes(int length) {
        byte[] b = new byte[length];
        for (int i = 0; i < length; i++) {
            b[i] = (byte) (i + 1);
        }
        return b;
    }
}
