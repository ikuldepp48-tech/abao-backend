package com.geihou.module.system.service.auth;

import com.aliyun.dkms.gcs.openapi.util.models.RuntimeOptions;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueRequest;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueResponse;
import com.geihou.module.system.framework.jwt.kms.GeihouKmsGetSecretValueRequestFactory;
import com.geihou.module.system.framework.jwt.kms.GeihouKmsProperties;
import com.geihou.module.system.framework.jwt.kms.GeihouKmsRuntimeOptionsFactory;
import com.geihou.module.system.framework.jwt.kms.GeihouKmsSecretClient;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * KMS-backed {@link TwoFactorSecretKeyProvider} that fetches a Base64-encoded
 * AES key from a dedicated KMS secret.
 *
 * <p>H157S-M: reuses the existing {@link GeihouKmsSecretClient} /
 * {@link GeihouKmsProperties} infrastructure (originally built for JWT
 * signing secrets) but reads a separate 2FA-specific secret name. The
 * expected KMS secret data format is plain Base64 (no envelope prefix)
 * decoding to exactly 16, 24, or 32 bytes.
 *
 * <p>Fail-closed semantics:
 * <ul>
 *   <li>On successful fetch, the key is cached for {@code cache-ttl}.</li>
 *   <li>On fetch failure after cache expiry, if {@code stale-cache-ttl} is
 *       non-zero and the stale entry is still within the stale window, the
 *       stale key is returned.</li>
 *   <li>On fetch failure with no usable cache, {@code currentKey()} returns
 *       {@code null}, causing
 *       {@link AesGcmTwoFactorSecretCryptoProvider#validatedKey()} to throw
 *       {@link TwoFactorSecretCryptoException}.</li>
 * </ul>
 */
public final class KmsTwoFactorSecretKeyProvider implements TwoFactorSecretKeyProvider {

    private static final String TEXT_SECRET_DATA_TYPE = "text";
    private static final String CACHE_KEY = "current";

    private final GeihouKmsProperties kmsProperties;
    private final GeihouKmsSecretClient secretClient;
    private final String secretName;
    private final Clock clock;
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Public constructor using the system UTC clock.
     *
     * @param kmsProperties KMS runtime properties (must be enabled)
     * @param secretClient  KMS secret client adapter
     * @param secretName    the KMS secret name holding the Base64-encoded AES key
     */
    public KmsTwoFactorSecretKeyProvider(GeihouKmsProperties kmsProperties,
                                          GeihouKmsSecretClient secretClient,
                                          String secretName) {
        this(kmsProperties, secretClient, secretName, Clock.systemUTC());
    }

    /**
     * Package-private constructor accepting a custom clock for testing.
     */
    KmsTwoFactorSecretKeyProvider(GeihouKmsProperties kmsProperties,
                                   GeihouKmsSecretClient secretClient,
                                   String secretName,
                                   Clock clock) {
        this.kmsProperties = Objects.requireNonNull(kmsProperties, "kmsProperties must not be null");
        this.secretClient = Objects.requireNonNull(secretClient, "secretClient must not be null");
        if (secretName == null || secretName.isBlank()) {
            throw new IllegalArgumentException("kms secret-name must not be blank");
        }
        this.secretName = secretName;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public byte[] currentKey() {
        Instant now = clock.instant();
        CacheEntry cached = cache.get(CACHE_KEY);
        if (isFresh(cached, now)) {
            return Arrays.copyOf(cached.key(), cached.key().length);
        }
        try {
            byte[] key = fetch();
            cache.put(CACHE_KEY, new CacheEntry(key, now));
            return Arrays.copyOf(key, key.length);
        } catch (RuntimeException ex) {
            if (isStaleUsable(cached, now)) {
                return Arrays.copyOf(cached.key(), cached.key().length);
            }
            return null;
        }
    }

    private byte[] fetch() {
        GetSecretValueRequest request = GeihouKmsGetSecretValueRequestFactory.create(secretName);
        RuntimeOptions runtimeOptions = GeihouKmsRuntimeOptionsFactory.create(kmsProperties);
        GetSecretValueResponse response = secretClient.getSecretValue(request, runtimeOptions);
        return decode(response);
    }

    private byte[] decode(GetSecretValueResponse response) {
        if (response == null) {
            throw new IllegalStateException("KMS 2FA secret response is null");
        }
        if (!TEXT_SECRET_DATA_TYPE.equals(response.getSecretDataType())) {
            throw new IllegalStateException("KMS 2FA secret data type is unsupported: "
                    + response.getSecretDataType());
        }
        String secretData = response.getSecretData();
        if (secretData == null || secretData.isBlank()) {
            throw new IllegalStateException("KMS 2FA secret data is unavailable");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secretData.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("KMS 2FA secret data is not valid Base64");
        }
        if (!isValidAesKeyLength(decoded.length)) {
            int invalidLength = decoded.length;
            Arrays.fill(decoded, (byte) 0);
            throw new IllegalStateException(
                    "KMS 2FA AES key must be 16, 24, or 32 bytes but decoded to " + invalidLength);
        }
        return decoded;
    }

    private boolean isFresh(CacheEntry cached, Instant now) {
        return cached != null && now.isBefore(cached.fetchedAt().plus(kmsProperties.cacheTtl()));
    }

    private boolean isStaleUsable(CacheEntry cached, Instant now) {
        if (cached == null || kmsProperties.staleCacheTtl().isZero()) {
            return false;
        }
        Instant staleDeadline = cached.fetchedAt()
                .plus(kmsProperties.cacheTtl())
                .plus(kmsProperties.staleCacheTtl());
        return now.isBefore(staleDeadline);
    }

    private static boolean isValidAesKeyLength(int length) {
        return length == 16 || length == 24 || length == 32;
    }

    private record CacheEntry(byte[] key, Instant fetchedAt) {
    }

    @Override
    public String toString() {
        return "KmsTwoFactorSecretKeyProvider{secretName='" + secretName + "', key=<redacted>}";
    }
}
