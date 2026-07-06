package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmTwoFactorSecretCryptoProviderTest {

    private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void shouldEncryptAndDecryptSecretWithEnvelope() {
        AesGcmTwoFactorSecretCryptoProvider provider = provider(KEY);

        String encrypted = provider.encrypt("JBSWY3DPEHPK3PXP");
        String decrypted = provider.decrypt(encrypted);

        assertThat(encrypted).startsWith("GEIHOU_2FA_SECRET_AES_GCM_V1:");
        assertThat(encrypted).doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(decrypted).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void shouldUseRandomNonceForSamePlaintext() {
        AesGcmTwoFactorSecretCryptoProvider provider = provider(KEY);

        String first = provider.encrypt("JBSWY3DPEHPK3PXP");
        String second = provider.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(first).isNotEqualTo(second);
        assertThat(provider.decrypt(first)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(provider.decrypt(second)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void shouldFailClosedForMalformedEnvelopeAndBlankInput() {
        AesGcmTwoFactorSecretCryptoProvider provider = provider(KEY);

        assertThatThrownBy(() -> provider.encrypt(null)).isInstanceOf(TwoFactorSecretCryptoException.class);
        assertThatThrownBy(() -> provider.encrypt(" ")).isInstanceOf(TwoFactorSecretCryptoException.class);
        assertThatThrownBy(() -> provider.decrypt(null)).isInstanceOf(TwoFactorSecretCryptoException.class);
        assertThatThrownBy(() -> provider.decrypt(" ")).isInstanceOf(TwoFactorSecretCryptoException.class);
        assertThatThrownBy(() -> provider.decrypt("plaintext-secret"))
                .isInstanceOf(TwoFactorSecretCryptoException.class);
        assertThatThrownBy(() -> provider.decrypt("GEIHOU_2FA_SECRET_AES_GCM_V1:not-base64:not-base64"))
                .isInstanceOf(TwoFactorSecretCryptoException.class);
    }

    @Test
    void shouldFailClosedForTamperedCiphertext() {
        AesGcmTwoFactorSecretCryptoProvider provider = provider(KEY);
        String encrypted = provider.encrypt("JBSWY3DPEHPK3PXP");
        String[] parts = encrypted.split(":", -1);
        byte[] ciphertext = Base64.getDecoder().decode(parts[2]);
        ciphertext[ciphertext.length - 1] ^= 1;
        String tampered = parts[0] + ":" + parts[1] + ":" + Base64.getEncoder().encodeToString(ciphertext);

        assertThatThrownBy(() -> provider.decrypt(tampered))
                .isInstanceOf(TwoFactorSecretCryptoException.class);
    }

    @Test
    void shouldFailClosedForWrongKeyOrInvalidKeyLength() {
        AesGcmTwoFactorSecretCryptoProvider provider = provider(KEY);
        String encrypted = provider.encrypt("JBSWY3DPEHPK3PXP");

        assertThatThrownBy(() -> provider("abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8))
                .decrypt(encrypted)).isInstanceOf(TwoFactorSecretCryptoException.class);
        assertThatThrownBy(() -> provider("short".getBytes(StandardCharsets.UTF_8))
                .encrypt("JBSWY3DPEHPK3PXP")).isInstanceOf(TwoFactorSecretCryptoException.class);
    }

    private static AesGcmTwoFactorSecretCryptoProvider provider(byte[] key) {
        return new AesGcmTwoFactorSecretCryptoProvider(() -> key);
    }
}
