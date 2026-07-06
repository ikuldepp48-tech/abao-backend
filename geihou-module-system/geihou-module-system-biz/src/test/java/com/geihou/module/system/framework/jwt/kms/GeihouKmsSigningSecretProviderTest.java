package com.geihou.module.system.framework.jwt.kms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aliyun.dkms.gcs.openapi.util.models.RuntimeOptions;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueRequest;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueResponse;
import com.geihou.module.system.framework.jwt.GeihouSigningSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeihouKmsSigningSecretProviderTest {

    private static final String KID = "kid-2026-06";
    private static final String PREFIX = "geihou/auth/jwt/";
    private static final String ENVELOPE = "GEIHOU_HS512_V1:";

    @Test
    void shouldResolveSigningSecretAndPassRequestAndRuntimeOptions() {
        byte[] keyBytes = keyBytes(64);
        CapturingSecretClient client = CapturingSecretClient.returning(response(envelope(keyBytes)));
        GeihouKmsSigningSecretProvider provider = provider(validProperties(), client);

        Optional<GeihouSigningSecret> resolved = provider.resolve(KID);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().kid()).isEqualTo(KID);
        assertThat(resolved.get().algorithm()).isEqualTo("HS512");
        assertThat(resolved.get().keyBytes()).containsExactly(keyBytes);
        assertThat(client.callCount).isEqualTo(1);
        assertThat(client.request.getSecretName()).isEqualTo(PREFIX + KID);
        assertThat(client.request.getFetchExtendedConfig()).isFalse();
        assertThat(client.runtimeOptions.getConnectTimeout()).isEqualTo(2_000L);
        assertThat(client.runtimeOptions.getReadTimeout()).isEqualTo(4_000L);
        assertThat(client.runtimeOptions.getIgnoreSSL()).isFalse();
        assertThat(client.runtimeOptions.getAutoretry()).isFalse();
        assertThat(client.runtimeOptions.getMaxAttempts()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyWithoutCallingClientForBlankOrDisabledOrUnsafeKid() {
        CapturingSecretClient nullKidClient = CapturingSecretClient.returning(response(envelope(keyBytes(64))));
        CapturingSecretClient blankKidClient = CapturingSecretClient.returning(response(envelope(keyBytes(64))));
        CapturingSecretClient disabledClient = CapturingSecretClient.returning(response(envelope(keyBytes(64))));
        CapturingSecretClient unsafeKidClient = CapturingSecretClient.returning(response(envelope(keyBytes(64))));

        assertThat(provider(validProperties(), nullKidClient).resolve(null)).isEmpty();
        assertThat(provider(validProperties(), blankKidClient).resolve(" ")).isEmpty();
        assertThat(provider(GeihouKmsProperties.disabledDefaults(), disabledClient).resolve(KID)).isEmpty();
        assertThat(provider(validProperties(), unsafeKidClient).resolve("kid/with/slash")).isEmpty();

        assertThat(nullKidClient.callCount).isZero();
        assertThat(blankKidClient.callCount).isZero();
        assertThat(disabledClient.callCount).isZero();
        assertThat(unsafeKidClient.callCount).isZero();
    }

    @Test
    void shouldReturnEmptyWhenClientOrDecoderFails() {
        CapturingSecretClient throwingClient = CapturingSecretClient.throwing();
        CapturingSecretClient nullResponseClient = CapturingSecretClient.returning(null);
        CapturingSecretClient malformedDataClient = CapturingSecretClient.returning(response("not-an-envelope"));
        GeihouKmsSigningSecretProvider throwingProvider = provider(validProperties(), throwingClient);
        GeihouKmsSigningSecretProvider nullResponseProvider = provider(validProperties(), nullResponseClient);
        GeihouKmsSigningSecretProvider malformedDataProvider = provider(validProperties(), malformedDataClient);

        assertThat(throwingProvider.resolve(KID)).isEmpty();
        assertThat(nullResponseProvider.resolve(KID)).isEmpty();
        assertThat(malformedDataProvider.resolve(KID)).isEmpty();

        assertThat(throwingClient.callCount).isEqualTo(1);
        assertThat(nullResponseClient.callCount).isEqualTo(1);
        assertThat(malformedDataClient.callCount).isEqualTo(1);
    }

    @Test
    void shouldUseFreshCacheWithoutCallingClientAgain() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-14T00:00:00Z"));
        byte[] keyBytes = keyBytes(64);
        CapturingSecretClient client = CapturingSecretClient.returning(response(envelope(keyBytes)));
        GeihouKmsSigningSecretProvider provider = provider(validProperties(), client, clock);

        Optional<GeihouSigningSecret> first = provider.resolve(KID);
        clock.advance(Duration.ofMinutes(2));
        Optional<GeihouSigningSecret> second = provider.resolve(KID);

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(second.get().keyBytes()).containsExactly(keyBytes);
        assertThat(client.callCount).isEqualTo(1);
    }

    @Test
    void shouldRefreshCacheAfterCacheTtlExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-14T00:00:00Z"));
        byte[] firstKey = keyBytes(64);
        byte[] secondKey = keyBytes(80);
        CapturingSecretClient client = CapturingSecretClient.sequence(
                response(envelope(firstKey)),
                response(envelope(secondKey)));
        GeihouKmsSigningSecretProvider provider = provider(validProperties(), client, clock);

        Optional<GeihouSigningSecret> first = provider.resolve(KID);
        clock.advance(Duration.ofMinutes(3));
        Optional<GeihouSigningSecret> second = provider.resolve(KID);

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(second.get().keyBytes()).containsExactly(secondKey);
        assertThat(client.callCount).isEqualTo(2);
    }

    @Test
    void shouldReturnStaleCacheWhenRefreshFailsInsideStaleWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-14T00:00:00Z"));
        byte[] keyBytes = keyBytes(64);
        CapturingSecretClient client = CapturingSecretClient.sequence(
                response(envelope(keyBytes)),
                new GeihouKmsClientException("KMS GetSecretValue failed"));
        GeihouKmsSigningSecretProvider provider = provider(
                validProperties(Duration.ofMinutes(3), Duration.ofMinutes(10)), client, clock);

        Optional<GeihouSigningSecret> first = provider.resolve(KID);
        clock.advance(Duration.ofMinutes(4));
        Optional<GeihouSigningSecret> stale = provider.resolve(KID);

        assertThat(first).isPresent();
        assertThat(stale).isPresent();
        assertThat(stale.get().keyBytes()).containsExactly(keyBytes);
        assertThat(client.callCount).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyWhenExpiredCacheRefreshFailsAndStaleCacheIsDisabled() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-14T00:00:00Z"));
        CapturingSecretClient client = CapturingSecretClient.sequence(
                response(envelope(keyBytes(64))),
                new GeihouKmsClientException("KMS GetSecretValue failed"));
        GeihouKmsSigningSecretProvider provider = provider(validProperties(), client, clock);

        assertThat(provider.resolve(KID)).isPresent();
        clock.advance(Duration.ofMinutes(4));

        assertThat(provider.resolve(KID)).isEmpty();
        assertThat(client.callCount).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyWhenDeadCacheRefreshFails() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-14T00:00:00Z"));
        CapturingSecretClient client = CapturingSecretClient.sequence(
                response(envelope(keyBytes(64))),
                new GeihouKmsClientException("KMS GetSecretValue failed"));
        GeihouKmsSigningSecretProvider provider = provider(
                validProperties(Duration.ofMinutes(3), Duration.ofMinutes(10)), client, clock);

        assertThat(provider.resolve(KID)).isPresent();
        clock.advance(Duration.ofMinutes(14));

        assertThat(provider.resolve(KID)).isEmpty();
        assertThat(client.callCount).isEqualTo(2);
    }

    @Test
    void shouldKeepDifferentKidsInSeparateCacheEntries() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-14T00:00:00Z"));
        String otherKid = "kid-2026-07";
        byte[] firstKey = keyBytes(64);
        byte[] secondKey = keyBytes(80);
        CapturingSecretClient client = CapturingSecretClient.sequence(
                response(envelope(firstKey)),
                response(envelope(secondKey)));
        GeihouKmsSigningSecretProvider provider = provider(validProperties(), client, clock);

        Optional<GeihouSigningSecret> first = provider.resolve(KID);
        Optional<GeihouSigningSecret> second = provider.resolve(otherKid);
        clock.advance(Duration.ofMinutes(2));
        Optional<GeihouSigningSecret> firstAgain = provider.resolve(KID);
        Optional<GeihouSigningSecret> secondAgain = provider.resolve(otherKid);

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(firstAgain).isPresent();
        assertThat(secondAgain).isPresent();
        assertThat(firstAgain.get().kid()).isEqualTo(KID);
        assertThat(firstAgain.get().keyBytes()).containsExactly(firstKey);
        assertThat(secondAgain.get().kid()).isEqualTo(otherKid);
        assertThat(secondAgain.get().keyBytes()).containsExactly(secondKey);
        assertThat(client.callCount).isEqualTo(2);
    }

    @Test
    void shouldRejectNullConstructorDependencies() {
        GeihouKmsProperties properties = validProperties();
        GeihouKmsSecretNameMapper mapper = new GeihouKmsSecretNameMapper(PREFIX);
        CapturingSecretClient client = CapturingSecretClient.returning(response(envelope(keyBytes(64))));
        GeihouKmsSigningSecretDecoder decoder = new GeihouKmsSigningSecretDecoder();

        assertThatThrownBy(() -> new GeihouKmsSigningSecretProvider(null, mapper, client, decoder))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("properties");
        assertThatThrownBy(() -> new GeihouKmsSigningSecretProvider(properties, null, client, decoder))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("secretNameMapper");
        assertThatThrownBy(() -> new GeihouKmsSigningSecretProvider(properties, mapper, null, decoder))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("secretClient");
        assertThatThrownBy(() -> new GeihouKmsSigningSecretProvider(properties, mapper, client, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("decoder");
        assertThatThrownBy(() -> new GeihouKmsSigningSecretProvider(properties, mapper, client, decoder, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clock");
    }

    private static GeihouKmsSigningSecretProvider provider(
            GeihouKmsProperties properties, GeihouKmsSecretClient client) {
        return new GeihouKmsSigningSecretProvider(
                properties,
                new GeihouKmsSecretNameMapper(PREFIX),
                client,
                new GeihouKmsSigningSecretDecoder());
    }

    private static GeihouKmsSigningSecretProvider provider(
            GeihouKmsProperties properties, GeihouKmsSecretClient client, Clock clock) {
        return new GeihouKmsSigningSecretProvider(
                properties,
                new GeihouKmsSecretNameMapper(PREFIX),
                client,
                new GeihouKmsSigningSecretDecoder(),
                clock);
    }

    private static GeihouKmsProperties validProperties() {
        return validProperties(Duration.ofMinutes(3), Duration.ZERO);
    }

    private static GeihouKmsProperties validProperties(Duration cacheTtl, Duration staleCacheTtl) {
        return GeihouKmsProperties.of(
                true,
                "kms-example.cryptoservice.kms.aliyuncs.com",
                "/missing/geihou/client-key.json",
                "GEIHOU_KMS_CLIENT_KEY_PASSWORD",
                "/missing/geihou/kms-ca.pem",
                PREFIX,
                Duration.ofSeconds(2),
                Duration.ofSeconds(4),
                cacheTtl,
                staleCacheTtl);
    }

    private static GetSecretValueResponse response(String secretData) {
        return new GetSecretValueResponse()
                .setSecretDataType("text")
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

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class CapturingSecretClient implements GeihouKmsSecretClient {

        private final List<Object> outcomes;
        private int callCount;
        private GetSecretValueRequest request;
        private RuntimeOptions runtimeOptions;

        private CapturingSecretClient(List<Object> outcomes) {
            this.outcomes = outcomes;
        }

        private static CapturingSecretClient returning(GetSecretValueResponse response) {
            return new CapturingSecretClient(Collections.singletonList(response));
        }

        private static CapturingSecretClient throwing() {
            return new CapturingSecretClient(Collections.singletonList(
                    new GeihouKmsClientException("KMS GetSecretValue failed")));
        }

        private static CapturingSecretClient sequence(Object... outcomes) {
            return new CapturingSecretClient(Arrays.asList(outcomes));
        }

        @Override
        public GetSecretValueResponse getSecretValue(GetSecretValueRequest request, RuntimeOptions runtimeOptions) {
            Object outcome = outcomes.get(Math.min(callCount, outcomes.size() - 1));
            callCount++;
            this.request = request;
            this.runtimeOptions = runtimeOptions;
            if (outcome instanceof RuntimeException exception) {
                throw exception;
            }
            return (GetSecretValueResponse) outcome;
        }
    }
}
