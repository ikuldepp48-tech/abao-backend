package com.geihou.module.system.framework.jwt.kms;

import com.aliyun.dkms.gcs.openapi.util.models.RuntimeOptions;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueRequest;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueResponse;
import com.geihou.module.system.framework.jwt.GeihouSigningSecret;
import com.geihou.module.system.framework.jwt.GeihouSigningSecretProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Pure Java KMS-backed signing-secret provider composition.
 */
public final class GeihouKmsSigningSecretProvider implements GeihouSigningSecretProvider {

    private final GeihouKmsProperties properties;
    private final GeihouKmsSecretNameMapper secretNameMapper;
    private final GeihouKmsSecretClient secretClient;
    private final GeihouKmsSigningSecretDecoder decoder;
    private final Clock clock;
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public GeihouKmsSigningSecretProvider(GeihouKmsProperties properties,
                                          GeihouKmsSecretNameMapper secretNameMapper,
                                          GeihouKmsSecretClient secretClient,
                                          GeihouKmsSigningSecretDecoder decoder) {
        this(properties, secretNameMapper, secretClient, decoder, Clock.systemUTC());
    }

    GeihouKmsSigningSecretProvider(GeihouKmsProperties properties,
                                   GeihouKmsSecretNameMapper secretNameMapper,
                                   GeihouKmsSecretClient secretClient,
                                   GeihouKmsSigningSecretDecoder decoder,
                                   Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.secretNameMapper = Objects.requireNonNull(secretNameMapper, "secretNameMapper must not be null");
        this.secretClient = Objects.requireNonNull(secretClient, "secretClient must not be null");
        this.decoder = Objects.requireNonNull(decoder, "decoder must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Optional<GeihouSigningSecret> resolve(String kid) {
        if (kid == null || kid.isBlank()) {
            return Optional.empty();
        }
        if (!properties.enabled()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        CacheEntry cached = cache.get(kid);
        if (isFresh(cached, now)) {
            return Optional.of(cached.secret());
        }
        try {
            GeihouSigningSecret secret = fetch(kid);
            cache.put(kid, new CacheEntry(secret, now));
            return Optional.of(secret);
        } catch (RuntimeException ex) {
            if (isStaleUsable(cached, now)) {
                return Optional.of(cached.secret());
            }
            return Optional.empty();
        }
    }

    private GeihouSigningSecret fetch(String kid) {
        String secretName = secretNameMapper.toSecretName(kid);
        GetSecretValueRequest request = GeihouKmsGetSecretValueRequestFactory.create(secretName);
        RuntimeOptions runtimeOptions = GeihouKmsRuntimeOptionsFactory.create(properties);
        GetSecretValueResponse response = secretClient.getSecretValue(request, runtimeOptions);
        return decoder.decode(kid, response);
    }

    private boolean isFresh(CacheEntry cached, Instant now) {
        return cached != null && now.isBefore(cached.fetchedAt().plus(properties.cacheTtl()));
    }

    private boolean isStaleUsable(CacheEntry cached, Instant now) {
        if (cached == null || properties.staleCacheTtl().isZero()) {
            return false;
        }
        Instant staleDeadline = cached.fetchedAt()
                .plus(properties.cacheTtl())
                .plus(properties.staleCacheTtl());
        return now.isBefore(staleDeadline);
    }

    private record CacheEntry(GeihouSigningSecret secret, Instant fetchedAt) {
    }
}
