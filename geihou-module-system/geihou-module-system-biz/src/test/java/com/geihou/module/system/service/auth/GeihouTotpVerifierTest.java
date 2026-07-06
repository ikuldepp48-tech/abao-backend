package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class GeihouTotpVerifierTest {

    private static final String RFC_6238_BASE32_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void shouldVerifySixDigitTotpUsingRfc6238Sha1Vectors() {
        assertThat(verifierAt(59).verify("encrypted", "287082")).isTrue();
        assertThat(verifierAt(1111111109).verify("encrypted", "081804")).isTrue();
        assertThat(verifierAt(1111111111).verify("encrypted", "050471")).isTrue();
        assertThat(verifierAt(1234567890).verify("encrypted", "005924")).isTrue();
        assertThat(verifierAt(2000000000).verify("encrypted", "279037")).isTrue();
        assertThat(verifierAt(20000000000L).verify("encrypted", "353130")).isTrue();
    }

    @Test
    void shouldAllowPreviousAndNextThirtySecondWindowOnly() {
        GeihouTotpVerifier verifier = verifierAt(60);

        assertThat(verifier.verify("encrypted", "287082")).isTrue();
        assertThat(verifier.verify("encrypted", "359152")).isTrue();
        assertThat(verifier.verify("encrypted", "969429")).isTrue();
        assertThat(verifier.verify("encrypted", "338314")).isFalse();
    }

    @Test
    void shouldFailClosedForMalformedInputAndDecryptFailure() {
        GeihouTotpVerifier verifier = verifierAt(59);

        assertThat(verifier.verify(null, "287082")).isFalse();
        assertThat(verifier.verify(" ", "287082")).isFalse();
        assertThat(verifier.verify("encrypted", null)).isFalse();
        assertThat(verifier.verify("encrypted", " ")).isFalse();
        assertThat(verifier.verify("encrypted", "28708")).isFalse();
        assertThat(verifier.verify("encrypted", "2870820")).isFalse();
        assertThat(verifier.verify("encrypted", "ABCDEF")).isFalse();
        assertThat(new GeihouTotpVerifier(failingCrypto()).verify("encrypted", "287082")).isFalse();
        assertThat(new GeihouTotpVerifier(cryptoReturning("not-base32!")).verify("encrypted", "287082")).isFalse();
    }

    @Test
    void shouldRejectWrongCode() {
        assertThat(verifierAt(59).verify("encrypted", "000000")).isFalse();
    }

    private static GeihouTotpVerifier verifierAt(long epochSecond) {
        return new GeihouTotpVerifier(
                cryptoReturning(RFC_6238_BASE32_SECRET),
                Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC));
    }

    private static TwoFactorSecretCryptoProvider cryptoReturning(String plaintextSecret) {
        return new TwoFactorSecretCryptoProvider() {
            @Override
            public String encrypt(String plaintextSecret) {
                return plaintextSecret;
            }

            @Override
            public String decrypt(String encryptedSecret) {
                return plaintextSecret;
            }
        };
    }

    private static TwoFactorSecretCryptoProvider failingCrypto() {
        return new TwoFactorSecretCryptoProvider() {
            @Override
            public String encrypt(String plaintextSecret) {
                throw new TwoFactorSecretCryptoException("encrypt failed");
            }

            @Override
            public String decrypt(String encryptedSecret) {
                throw new TwoFactorSecretCryptoException("decrypt failed");
            }
        };
    }
}
