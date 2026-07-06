package com.geihou.module.system.service.auth;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class GeihouTotpVerifier {

    private static final int CODE_DIGITS = 6;
    private static final long TIME_STEP_SECONDS = 30L;
    private static final int ALLOWED_WINDOW_STEPS = 1;
    private static final int[] POWERS_OF_10 = {1, 10, 100, 1000, 10000, 100000, 1000000};
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final TwoFactorSecretCryptoProvider cryptoProvider;
    private final Clock clock;

    public GeihouTotpVerifier(TwoFactorSecretCryptoProvider cryptoProvider) {
        this(cryptoProvider, Clock.systemUTC());
    }

    GeihouTotpVerifier(TwoFactorSecretCryptoProvider cryptoProvider, Clock clock) {
        this.cryptoProvider = Objects.requireNonNull(cryptoProvider, "cryptoProvider must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public boolean verify(String encryptedSecret, String code) {
        if (encryptedSecret == null || encryptedSecret.isBlank() || !isSixDigitCode(code)) {
            return false;
        }
        try {
            byte[] secret = decodeBase32(cryptoProvider.decrypt(encryptedSecret));
            long currentCounter = clock.instant().getEpochSecond() / TIME_STEP_SECONDS;
            for (int offset = -ALLOWED_WINDOW_STEPS; offset <= ALLOWED_WINDOW_STEPS; offset++) {
                if (currentCounter + offset >= 0 && generateCode(secret, currentCounter + offset).equals(code)) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean isSixDigitCode(String code) {
        return code != null && code.length() == CODE_DIGITS && code.chars().allMatch(Character::isDigit);
    }

    private static String generateCode(byte[] secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % POWERS_OF_10[CODE_DIGITS];
            return String.format(Locale.ROOT, "%06d", otp);
        } catch (Exception ex) {
            throw new IllegalStateException("TOTP generation failed", ex);
        }
    }

    private static byte[] decodeBase32(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Base32 value must not be blank");
        }
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        int buffer = 0;
        int bitsLeft = 0;
        byte[] output = new byte[normalized.length() * 5 / 8];
        int index = 0;
        for (char ch : normalized.toCharArray()) {
            int val = BASE32_ALPHABET.indexOf(ch);
            if (val < 0) {
                throw new IllegalArgumentException("Invalid Base32 character");
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output[index++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        if (index == output.length) {
            return output;
        }
        return Arrays.copyOf(output, index);
    }
}
