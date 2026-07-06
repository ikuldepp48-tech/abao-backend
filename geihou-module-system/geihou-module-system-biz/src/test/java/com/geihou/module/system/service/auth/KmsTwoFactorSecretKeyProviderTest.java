package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aliyun.dkms.gcs.openapi.util.models.RuntimeOptions;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueRequest;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueResponse;
import com.geihou.module.system.framework.jwt.kms.GeihouKmsClientException;
import com.geihou.module.system.framework.jwt.kms.GeihouKmsProperties;
import com.geihou.module.system.framework.jwt.kms.GeihouKmsSecretClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class KmsTwoFactorSecretKeyProviderTest {

    private static final String SECRET_NAME = "geihou/auth/2fa/aes-key";
    private static final byte[] KEY_16 = bytes(16);
    private static final byte[] KEY_32 = bytes(32);

    @Test
    void shouldFetchAndReturnKeyOnFirstCall() {
        CapturingSecretClient client = CapturingSecretClient.returning(
                response(Base64.getEncoder().encodeToString(KEY_16)));
        KmsTwoFactorSecretKeyProvider provider = provider(validProperties(), client);

        byte[] key = provider.currentKey();

        assertThat(key).containsExactly(KEY_16);
        assertThat(client.callCount).isEqualTo(1);
        assertThat(client.request.getSecretName()).isEqualTo(SECRET_NAME);
        assertThat(client.request.getFetchExtendedConfig()).isFalse();
        assertThat(client.runtimeOptions.getConnectTimeout()).isEqualTo(2_000L);
        assertThat(client.runtimeOptions.getReadTimeout()).isEqualTo(4_000L);
        assertThat(client.runtimeOptions.getIgnoreSSL()).isFalse();
        assertThat(client.runtimeOptions.getAutoretry()).isFalse();
        assertThat(client.runtimeOptions.getMaxAttempts()).isEqualTo(1);
    }

    @Test
    void shouldReturnDefensiveCopyNotInternalArray() {
        CapturingSecretClient client = CapturingSecretClient.returning(
                response(Base64.getEncoder().encodeToString(KEY_32)));
        KmsTwoFactorSecretKeyProvider provider = provider(validProperties(), client);

        byte[] first = provider.currentKey();
        first[0] = (byte) 0xFF;

        assertThat(provider.currentKey()).containsExactly(KEY_32);
    }

    @Test
    void shouldUseFreshCacheWithoutCallingClientAgain() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-21T00:00:00Z"));
        CapturingSecretClient client = CapturingSecretClient.returning(
                response(Base64.getEncoder().encodeToString(KEY_16)));
        KmsTwoFactorSecretKeyProvider provider = provider(validProperties(), client, clock);

        provider.currentKey();
        clock.advance(Duration.ofMinutes(2));
        byte[] second = provider.currentKey();

        assertThat(second).containsExactly(KEY_16);
        assertThat(client.callCount).isEqualTo(1);
    }

    @Test
    void shouldRefreshCacheAfterCacheTtlExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-21T00:00:00Z"));
        byte[] firstKey = KEY_16;
        byte[] secondKey = bytes(24);
        CapturingSecretClient client = CapturingSecretClient.sequence(
                response(Base64.getEncoder().encodeToString(firstKey)),
                response(Base64.getEncoder().encodeToString(secondKey)));
        KmsTwoFactorSecretKeyProvider provider = provider(validProperties(), client, clock);

        byte[] first = provider.currentKey();
        clock.advance(Duration.ofMinutes(4));
        byte[] second = provider.currentKey();

        assertThat(first).containsExactly(firstKey);
        assertThat(second).containsExactly(secondKey);
        assertThat(client.callCount).isEqualTo(2);
    }

    @Test
    void shouldReturnStaleCacheWhenRefreshFailsInsideStaleWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-21T00:00:00Z"));
        CapturingSecretClient client = CapturingSecretClient.sequence(
                response(Base64.getEncoder().encodeToString(KEY_16)),
                new GeihouKmsClientException("KMS GetSecretValue failed"));
        KmsTwoFactorSecretKeyProvider provider = provider(
                validProperties(Duration.ofMinutes(3), Duration.ofMinutes(10)), client, clock);

        byte[] first = provider.currentKey();
        clock.advance(Duration.ofMinutes(4));
        byte[] stale = provider.currentKey();

        assertThat(first).containsExactly(KEY_16);
        assertThat(stale).containsExactly(KEY_16);
        assertThat(client.callCount).isEqualTo(2);
    }

    @Test
    void shouldReturnNullWhenExpiredCacheRefreshFailsAndStaleCacheIsDisabled() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-21T00:00:00Z"));
        CapturingSecretClient client = CapturingSecretClient.sequence(
                response(Base64.getEncoder().encodeToString(KEY_16)),
                new GeihouKmsClientException("KMS GetSecretValue failed"));
        KmsTwoFactorSecretKeyProvider provider = provider(validProperties(), client, clock);

        provider.currentKey();
        clock.advance(Duration.ofMinutes(4));

        assertThat(provider.currentKey()).isNull();
        assertThat(client.callCount).isEqualTo(2);
    }

    @Test
    void shouldReturnNullWhenDeadCacheRefreshFails() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-21T00:00:00Z"));
        CapturingSecretClient client = CapturingSecretClient.sequence(
                response(Base64.getEncoder().encodeToString(KEY_16)),
                new GeihouKmsClientException("KMS GetSecretValue failed"));
        KmsTwoFactorSecretKeyProvider provider = provider(
                validProperties(Duration.ofMinutes(3), Duration.ofMinutes(10)), client, clock);

        provider.currentKey();
        clock.advance(Duration.ofMinutes(14));

        assertThat(provider.currentKey()).isNull();
        assertThat(client.callCount).isEqualTo(2);
    }

    @Test
    void shouldReturnNullWhenClientThrowsAndNoCache() {
        CapturingSecretClient client = CapturingSecretClient.throwing();
        KmsTwoFactorSecretKeyProvider provider = provider(validProperties(), client);

        assertThat(provider.currentKey()).isNull();
        assertThat(client.callCount).isEqualTo(1);
    }

    @Test
    void shouldReturnNullForNullResponse() {
        CapturingSecretClient client = CapturingSecretClient.returning(null);
        KmsTwoFactorSecretKeyProvider provider = provider(validProperties(), client);

        assertThat(provider.currentKey()).isNull();
        assertThat(client.callCount).isEqualTo(1);
    }

    @Test
    void shouldReturnNullForUnsupportedSecretDataType() {
        CapturingSecretClient client = CapturingSecretClient.returning(
                new GetSecretValueResponse()
                        .setSecretDataType("binary")
                        .setSecretData("something"));
        KmsTwoFactorSecretKeyProvider provider = provider(validProperties(), client);

        assertThat(provider.currentKey()).isNull();
    }

    @Test
    void shouldReturnNullForBlankSecretData() {
        CapturingSecretClient client = CapturingSecretClient.returning(
                response("  "));
        KmsTwoFactorSecretKeyProvider provider = provider(validProperties(), client);

        assertThat(provider.currentKey()).isNull();
    }

    @Test
    void shouldReturnNullForInvalidBase64SecretData() {
        CapturingSecretClient client = CapturingSecretClient.returning(
                response("!!!not-base64!!!"));
        KmsTwoFactorSecretKeyProvider provider = provider(validProperties(), client);

        assertThat(provider.currentKey()).isNull();
    }

    @Test
    void shouldReturnNullForInvalidKeyLength() {
        CapturingSecretClient client = CapturingSecretClient.returning(
                response(Base64.getEncoder().encodeToString(bytes(15))));
        KmsTwoFactorSecretKeyProvider provider = provider(validProperties(), client);

        assertThat(provider.currentKey()).isNull();
    }

    @Test
    void shouldRejectNullConstructorDependencies() {
        GeihouKmsProperties properties = validProperties();
        CapturingSecretClient client = CapturingSecretClient.returning(
                response(Base64.getEncoder().encodeToString(KEY_16)));

        assertThatThrownBy(() -> new KmsTwoFactorSecretKeyProvider(null, client, SECRET_NAME))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("kmsProperties");
        assertThatThrownBy(() -> new KmsTwoFactorSecretKeyProvider(properties, null, SECRET_NAME))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("secretClient");
        assertThatThrownBy(() -> new KmsTwoFactorSecretKeyProvider(properties, client, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secret-name");
        assertThatThrownBy(() -> new KmsTwoFactorSecretKeyProvider(properties, client, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secret-name");
        assertThatThrownBy(() -> new KmsTwoFactorSecretKeyProvider(properties, client, SECRET_NAME, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clock");
    }

    @Test
    void toStringShouldNotExposeKeyMaterial() {
        CapturingSecretClient client = CapturingSecretClient.returning(
                response(Base64.getEncoder().encodeToString(KEY_32)));
        KmsTwoFactorSecretKeyProvider provider = provider(validProperties(), client);

        provider.currentKey();

        String str = provider.toString();
        assertThat(str).contains("redacted");
        assertThat(str).contains(SECRET_NAME);
        assertThat(str).doesNotContain(new String(KEY_32));
    }

    private static KmsTwoFactorSecretKeyProvider provider(
            GeihouKmsProperties properties, GeihouKmsSecretClient client) {
        return new KmsTwoFactorSecretKeyProvider(properties, client, SECRET_NAME);
    }

    private static KmsTwoFactorSecretKeyProvider provider(
            GeihouKmsProperties properties, GeihouKmsSecretClient client, Clock clock) {
        return new KmsTwoFactorSecretKeyProvider(properties, client, SECRET_NAME, clock);
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
                "geihou/auth/",
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

    private static byte[] bytes(int length) {
        byte[] b = new byte[length];
        for (int i = 0; i < length; i++) {
            b[i] = (byte) (i + 1);
        }
        return b;
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
        public GetSecretValueResponse getSecretValue(
                GetSecretValueRequest request, RuntimeOptions runtimeOptions) {
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
