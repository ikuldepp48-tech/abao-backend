package com.geihou.module.system.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesGcmTwoFactorSecretCryptoProvider implements TwoFactorSecretCryptoProvider {

    private static final String ENVELOPE_PREFIX = "GEIHOU_2FA_SECRET_AES_GCM_V1";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final TwoFactorSecretKeyProvider keyProvider;
    private final SecureRandom secureRandom;

    public AesGcmTwoFactorSecretCryptoProvider(TwoFactorSecretKeyProvider keyProvider) {
        this(keyProvider, new SecureRandom());
    }

    AesGcmTwoFactorSecretCryptoProvider(TwoFactorSecretKeyProvider keyProvider, SecureRandom secureRandom) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    @Override
    public String encrypt(String plaintextSecret) {
        if (plaintextSecret == null || plaintextSecret.isBlank()) {
            throw new TwoFactorSecretCryptoException("2FA secret plaintext is unavailable");
        }
        byte[] key = validatedKey();
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintextSecret.getBytes(StandardCharsets.UTF_8));
            return ENVELOPE_PREFIX + ":"
                    + Base64.getEncoder().encodeToString(nonce) + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (GeneralSecurityException ex) {
            throw new TwoFactorSecretCryptoException("2FA secret encryption failed");
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    @Override
    public String decrypt(String encryptedSecret) {
        if (encryptedSecret == null || encryptedSecret.isBlank()) {
            throw new TwoFactorSecretCryptoException("2FA secret ciphertext is unavailable");
        }
        String[] parts = encryptedSecret.split(":", -1);
        if (parts.length != 3 || !ENVELOPE_PREFIX.equals(parts[0])) {
            throw new TwoFactorSecretCryptoException("2FA secret envelope is invalid");
        }
        byte[] key = validatedKey();
        try {
            byte[] nonce = Base64.getDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[2]);
            if (nonce.length != NONCE_BYTES || ciphertext.length == 0) {
                throw new TwoFactorSecretCryptoException("2FA secret envelope is invalid");
            }
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new TwoFactorSecretCryptoException("2FA secret decryption failed");
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private byte[] validatedKey() {
        byte[] key = keyProvider.currentKey();
        if (key == null || !(key.length == 16 || key.length == 24 || key.length == 32)) {
            throw new TwoFactorSecretCryptoException("2FA secret key is invalid");
        }
        return Arrays.copyOf(key, key.length);
    }
}
