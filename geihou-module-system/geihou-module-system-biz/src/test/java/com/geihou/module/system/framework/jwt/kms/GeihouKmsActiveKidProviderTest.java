package com.geihou.module.system.framework.jwt.kms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aliyun.dkms.gcs.openapi.util.models.RuntimeOptions;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueRequest;
import com.aliyun.dkms.gcs.sdk.models.GetSecretValueResponse;
import com.geihou.module.system.api.auth.dto.AuthTokenVerifyRespDTO;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenAudience;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenIssuer;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenRequest;
import com.geihou.module.system.framework.jwt.GeihouJwtHeader;
import com.geihou.module.system.framework.jwt.GeihouJwtHeaderParser;
import com.geihou.module.system.framework.jwt.GeihouJwtTokenParser;
import com.geihou.module.system.framework.jwt.GeihouResolvingActiveSigningSecretProvider;
import com.geihou.module.system.framework.jwt.GeihouSigningSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeihouKmsActiveKidProviderTest {

    private static final String PREFIX = "geihou/auth/jwt/";
    private static final String MARKER_KID = "__active-kid";
    private static final String ACTIVE_KID = "kid-2026-06";
    private static final Instant NOW = Instant.parse("2026-06-14T09:30:00Z");

    @Test
    void shouldReadActiveKidMarkerAndPassRequestAndRuntimeOptions() {
        CapturingSecretClient client = CapturingSecretClient.returning(response("text", ACTIVE_KID));
        GeihouKmsActiveKidProvider provider = provider(validProperties(), client);

        Optional<String> currentKid = provider.currentKid();

        assertThat(currentKid).hasValue(ACTIVE_KID);
        assertThat(client.callCount).isEqualTo(1);
        assertThat(client.request.getSecretName()).isEqualTo(PREFIX + MARKER_KID);
        assertThat(client.request.getFetchExtendedConfig()).isFalse();
        assertThat(client.runtimeOptions.getConnectTimeout()).isEqualTo(2_000L);
        assertThat(client.runtimeOptions.getReadTimeout()).isEqualTo(4_000L);
        assertThat(client.runtimeOptions.getIgnoreSSL()).isFalse();
        assertThat(client.runtimeOptions.getAutoretry()).isFalse();
        assertThat(client.runtimeOptions.getMaxAttempts()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyWithoutCallingClientWhenKmsIsDisabled() {
        CapturingSecretClient client = CapturingSecretClient.returning(response("text", ACTIVE_KID));
        GeihouKmsActiveKidProvider provider = provider(GeihouKmsProperties.disabledDefaults(), client);

        assertThat(provider.currentKid()).isEmpty();
        assertThat(client.callCount).isZero();
    }

    @Test
    void shouldReturnEmptyForMissingBlankUnsafeOrRecursiveMarkerValue() {
        assertThat(provider(validProperties(), CapturingSecretClient.returning(response("text", null))).currentKid())
                .isEmpty();
        assertThat(provider(validProperties(), CapturingSecretClient.returning(response("text", " "))).currentKid())
                .isEmpty();
        assertThat(provider(validProperties(), CapturingSecretClient.returning(response("text", "kid/with/slash")))
                .currentKid()).isEmpty();
        assertThat(provider(validProperties(), CapturingSecretClient.returning(response("text", "short"))).currentKid())
                .isEmpty();
        assertThat(provider(validProperties(), CapturingSecretClient.returning(response("text", MARKER_KID)))
                .currentKid()).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnsupportedSecretDataType() {
        assertThat(provider(validProperties(), CapturingSecretClient.returning(response(null, ACTIVE_KID))).currentKid())
                .isEmpty();
        assertThat(provider(validProperties(), CapturingSecretClient.returning(response(" ", ACTIVE_KID))).currentKid())
                .isEmpty();
        assertThat(provider(validProperties(), CapturingSecretClient.returning(response("binary", ACTIVE_KID)))
                .currentKid()).isEmpty();
        assertThat(provider(validProperties(), CapturingSecretClient.returning(response("TEXT", ACTIVE_KID))).currentKid())
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenClientFailsOrResponseIsMissing() {
        CapturingSecretClient throwingClient = CapturingSecretClient.throwing();
        CapturingSecretClient nullResponseClient = CapturingSecretClient.returning(null);

        assertThat(provider(validProperties(), throwingClient).currentKid()).isEmpty();
        assertThat(provider(validProperties(), nullResponseClient).currentKid()).isEmpty();
        assertThat(throwingClient.callCount).isEqualTo(1);
        assertThat(nullResponseClient.callCount).isEqualTo(1);
    }

    @Test
    void shouldRejectNullConstructorDependencies() {
        GeihouKmsProperties properties = validProperties();
        GeihouKmsSecretNameMapper mapper = new GeihouKmsSecretNameMapper(PREFIX);
        CapturingSecretClient client = CapturingSecretClient.returning(response("text", ACTIVE_KID));

        assertThatThrownBy(() -> new GeihouKmsActiveKidProvider(null, mapper, client))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("properties");
        assertThatThrownBy(() -> new GeihouKmsActiveKidProvider(properties, null, client))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("secretNameMapper");
        assertThatThrownBy(() -> new GeihouKmsActiveKidProvider(properties, mapper, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("secretClient");
    }

    @Test
    void shouldComposeWithActiveSigningSecretBridgeAndAccessTokenIssuer() {
        CapturingSecretClient markerClient = CapturingSecretClient.returning(response("text", ACTIVE_KID));
        GeihouKmsActiveKidProvider activeKidProvider = provider(validProperties(), markerClient);
        GeihouSigningSecret signingSecret = new GeihouSigningSecret(ACTIVE_KID, "HS512", activeSecretBytes());
        GeihouResolvingActiveSigningSecretProvider bridge = new GeihouResolvingActiveSigningSecretProvider(
                activeKidProvider,
                kid -> ACTIVE_KID.equals(kid) ? Optional.of(signingSecret) : Optional.empty());
        GeihouAccessTokenIssuer issuer = new GeihouAccessTokenIssuer(bridge, Clock.fixed(NOW, ZoneOffset.UTC));

        Optional<String> issuedToken = issuer.issue(new GeihouAccessTokenRequest(
                100L,
                200L,
                GeihouAccessTokenAudience.ADMIN,
                "OWNER",
                List.of("OWNER")));

        assertThat(issuedToken).isPresent();
        Optional<GeihouJwtHeader> parsedHeader = new GeihouJwtHeaderParser().parse(issuedToken.get());
        assertThat(parsedHeader).isPresent();
        assertThat(parsedHeader.get().kid()).isEqualTo(ACTIVE_KID);
        AuthTokenVerifyRespDTO verified = new GeihouJwtTokenParser(
                "geihou-platform",
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC))
                .verifyToken(issuedToken.get(), signingSecret.keyBytes());
        assertThat(verified.getValid()).isTrue();
        assertThat(verified.getUserId()).isEqualTo(100L);
        assertThat(verified.getTenantId()).isEqualTo(200L);
        assertThat(markerClient.callCount).isEqualTo(1);
    }

    private static GeihouKmsActiveKidProvider provider(
            GeihouKmsProperties properties, CapturingSecretClient client) {
        return new GeihouKmsActiveKidProvider(properties, new GeihouKmsSecretNameMapper(PREFIX), client);
    }

    private static GeihouKmsProperties validProperties() {
        return GeihouKmsProperties.of(
                true,
                "kms-example.cryptoservice.kms.aliyuncs.com",
                "/missing/geihou/client-key.json",
                "GEIHOU_KMS_CLIENT_KEY_PASSWORD",
                "/missing/geihou/kms-ca.pem",
                PREFIX,
                Duration.ofSeconds(2),
                Duration.ofSeconds(4),
                Duration.ofMinutes(3),
                Duration.ZERO);
    }

    private static GetSecretValueResponse response(String secretDataType, String secretData) {
        return new GetSecretValueResponse()
                .setSecretDataType(secretDataType)
                .setSecretData(secretData);
    }

    private static byte[] activeSecretBytes() {
        byte[] secret = new byte[64];
        for (int i = 0; i < secret.length; i++) {
            secret[i] = (byte) (64 - i);
        }
        return secret;
    }

    private static final class CapturingSecretClient implements GeihouKmsSecretClient {

        private final Object outcome;
        private int callCount;
        private GetSecretValueRequest request;
        private RuntimeOptions runtimeOptions;

        private CapturingSecretClient(Object outcome) {
            this.outcome = outcome;
        }

        private static CapturingSecretClient returning(GetSecretValueResponse response) {
            return new CapturingSecretClient(response);
        }

        private static CapturingSecretClient throwing() {
            return new CapturingSecretClient(new GeihouKmsClientException("KMS GetSecretValue failed"));
        }

        @Override
        public GetSecretValueResponse getSecretValue(GetSecretValueRequest request, RuntimeOptions runtimeOptions) {
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
