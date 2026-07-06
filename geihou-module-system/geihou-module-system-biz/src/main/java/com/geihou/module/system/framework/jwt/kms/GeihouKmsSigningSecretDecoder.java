package com.geihou.module.system.framework.jwt.kms;

import com.aliyun.dkms.gcs.sdk.models.GetSecretValueResponse;
import com.geihou.module.system.framework.jwt.GeihouSigningSecret;
import java.util.Arrays;
import java.util.Base64;

/**
 * Decodes the Geihou v1 KMS text envelope into HS512 signing-secret material.
 */
public final class GeihouKmsSigningSecretDecoder {

    private static final String ALGORITHM = "HS512";
    private static final String SECRET_DATA_TYPE_TEXT = "text";
    private static final String ENVELOPE_PREFIX = "GEIHOU_HS512_V1:";
    private static final int HS512_MINIMUM_KEY_BYTES = 64;

    private static final String UNAVAILABLE_MESSAGE = "KMS signing secret data is unavailable";
    private static final String UNSUPPORTED_TYPE_MESSAGE = "KMS signing secret data type is unsupported";
    private static final String INVALID_ENVELOPE_MESSAGE = "KMS signing secret data envelope is invalid";
    private static final String DECODING_FAILED_MESSAGE = "KMS signing secret data decoding failed";
    private static final String TOO_SHORT_MESSAGE = "KMS signing secret key is too short";

    public GeihouSigningSecret decode(String kid, GetSecretValueResponse response) {
        if (kid == null || kid.isBlank()) {
            throw new IllegalArgumentException("kid must not be blank");
        }
        if (response == null) {
            throw new GeihouKmsSecretDataException(UNAVAILABLE_MESSAGE);
        }
        if (!SECRET_DATA_TYPE_TEXT.equals(response.getSecretDataType())) {
            throw new GeihouKmsSecretDataException(UNSUPPORTED_TYPE_MESSAGE);
        }
        String secretData = response.getSecretData();
        if (secretData == null || secretData.isBlank()) {
            throw new GeihouKmsSecretDataException(UNAVAILABLE_MESSAGE);
        }
        if (!secretData.startsWith(ENVELOPE_PREFIX)) {
            throw new GeihouKmsSecretDataException(INVALID_ENVELOPE_MESSAGE);
        }
        byte[] decoded = decodeCanonicalBase64(secretData.substring(ENVELOPE_PREFIX.length()));
        if (decoded.length < HS512_MINIMUM_KEY_BYTES) {
            Arrays.fill(decoded, (byte) 0);
            throw new GeihouKmsSecretDataException(TOO_SHORT_MESSAGE);
        }
        return new GeihouSigningSecret(kid, ALGORITHM, decoded);
    }

    private static byte[] decodeCanonicalBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new GeihouKmsSecretDataException(DECODING_FAILED_MESSAGE);
        }
        if (encoded.length() % 4 != 0) {
            throw new GeihouKmsSecretDataException(DECODING_FAILED_MESSAGE);
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            String canonical = Base64.getEncoder().encodeToString(decoded);
            if (!canonical.equals(encoded)) {
                Arrays.fill(decoded, (byte) 0);
                throw new GeihouKmsSecretDataException(DECODING_FAILED_MESSAGE);
            }
            return decoded;
        } catch (IllegalArgumentException ex) {
            throw new GeihouKmsSecretDataException(DECODING_FAILED_MESSAGE);
        }
    }
}
