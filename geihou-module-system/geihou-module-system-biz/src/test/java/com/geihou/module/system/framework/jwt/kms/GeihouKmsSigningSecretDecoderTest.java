package com.geihou.module.system.framework.jwt.kms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aliyun.dkms.gcs.sdk.models.GetSecretValueResponse;
import com.geihou.module.system.framework.jwt.GeihouSigningSecret;
import java.lang.reflect.Modifier;
import java.util.Base64;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class GeihouKmsSigningSecretDecoderTest {

    private static final String KID = "kid-2026-06";
    private static final String ENVELOPE = "GEIHOU_HS512_V1:";

    private final GeihouKmsSigningSecretDecoder decoder = new GeihouKmsSigningSecretDecoder();

    @Test
    void shouldDecodeTextEnvelopeToSigningSecret() {
        byte[] keyBytes = keyBytes(64);
        GetSecretValueResponse response = response("text", envelope(keyBytes));

        GeihouSigningSecret secret = decoder.decode(KID, response);

        assertThat(secret.kid()).isEqualTo(KID);
        assertThat(secret.algorithm()).isEqualTo("HS512");
        assertThat(secret.keyBytes()).containsExactly(keyBytes);
    }

    @Test
    void shouldAcceptLongerHs512KeyBytes() {
        byte[] keyBytes = keyBytes(128);
        GetSecretValueResponse response = response("text", envelope(keyBytes));

        GeihouSigningSecret secret = decoder.decode(KID, response);

        assertThat(secret.keyBytes()).containsExactly(keyBytes);
    }

    @Test
    void shouldRejectShortDecodedKey() {
        String secretData = envelope(keyBytes(63));

        assertRedactedFailure(() -> decoder.decode(KID, response("text", secretData)), secretData)
                .isInstanceOf(GeihouKmsSecretDataException.class)
                .hasMessage("KMS signing secret key is too short");
    }

    @Test
    void shouldRejectNullResponseOrKidBeforeDecoding() {
        String secretData = envelope(keyBytes(64));

        assertRedactedFailure(() -> decoder.decode(null, response("text", secretData)), secretData)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kid");
        assertRedactedFailure(() -> decoder.decode(" ", response("text", secretData)), secretData)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kid");
        assertThatThrownBy(() -> decoder.decode(KID, null))
                .isInstanceOf(GeihouKmsSecretDataException.class)
                .hasMessage("KMS signing secret data is unavailable");
    }

    @Test
    void shouldRejectMissingOrBlankSecretData() {
        assertThatThrownBy(() -> decoder.decode(KID, response("text", null)))
                .isInstanceOf(GeihouKmsSecretDataException.class)
                .hasMessage("KMS signing secret data is unavailable");
        assertThatThrownBy(() -> decoder.decode(KID, response("text", "")))
                .isInstanceOf(GeihouKmsSecretDataException.class)
                .hasMessage("KMS signing secret data is unavailable");
        assertThatThrownBy(() -> decoder.decode(KID, response("text", "   ")))
                .isInstanceOf(GeihouKmsSecretDataException.class)
                .hasMessage("KMS signing secret data is unavailable");
    }

    @Test
    void shouldRejectMissingEnvelopeEvenWhenDataIsBase64() {
        String secretData = Base64.getEncoder().encodeToString(keyBytes(64));

        assertRedactedFailure(() -> decoder.decode(KID, response("text", secretData)), secretData)
                .isInstanceOf(GeihouKmsSecretDataException.class)
                .hasMessage("KMS signing secret data envelope is invalid");
    }

    @Test
    void shouldRejectUnsupportedSecretDataType() {
        String secretData = envelope(keyBytes(64));

        for (String type : new String[]{null, "", " ", "binary", "BINARY", "TEXT", "plaintext"}) {
            assertRedactedFailure(() -> decoder.decode(KID, response(type, secretData)), secretData)
                    .isInstanceOf(GeihouKmsSecretDataException.class)
                    .hasMessage("KMS signing secret data type is unsupported");
        }
    }

    @Test
    void shouldRejectInvalidOrNonCanonicalBase64() {
        assertRedactedFailure(() -> decoder.decode(KID, response("text", ENVELOPE + "@@@@")), "@@@@")
                .isInstanceOf(GeihouKmsSecretDataException.class)
                .hasMessage("KMS signing secret data decoding failed");

        String base64Url = Base64.getUrlEncoder().encodeToString(keyBytes(64));
        assertRedactedFailure(() -> decoder.decode(KID, response("text", ENVELOPE + base64Url)), base64Url)
                .isInstanceOf(GeihouKmsSecretDataException.class)
                .hasMessage("KMS signing secret data decoding failed");

        String noPadding = Base64.getEncoder().withoutPadding().encodeToString(keyBytes(64));
        assertRedactedFailure(() -> decoder.decode(KID, response("text", ENVELOPE + noPadding)), noPadding)
                .isInstanceOf(GeihouKmsSecretDataException.class)
                .hasMessage("KMS signing secret data decoding failed");
    }

    @Test
    void shouldNotKeepDecodedSecretBytesInInstanceFields() {
        assertThat(GeihouKmsSigningSecretDecoder.class.getDeclaredFields())
                .allMatch(field -> Modifier.isStatic(field.getModifiers()));
    }

    private static GetSecretValueResponse response(String secretDataType, String secretData) {
        return new GetSecretValueResponse()
                .setSecretDataType(secretDataType)
                .setSecretData(secretData);
    }

    private static String envelope(byte[] keyBytes) {
        return ENVELOPE + Base64.getEncoder().encodeToString(keyBytes);
    }

    private static byte[] keyBytes(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i + 1);
        }
        return bytes;
    }

    private static AbstractThrowableAssert<?, ? extends Throwable> assertRedactedFailure(
            ThrowingCallable callable, String secretText) {
        return assertThatThrownBy(callable)
                .hasMessageNotContaining(secretText)
                .hasMessageNotContaining(toHex(secretText.getBytes()));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
