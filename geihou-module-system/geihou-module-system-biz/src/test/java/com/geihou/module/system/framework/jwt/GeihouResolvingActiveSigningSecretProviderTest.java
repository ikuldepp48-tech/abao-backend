package com.geihou.module.system.framework.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geihou.module.system.api.auth.dto.AuthTokenVerifyRespDTO;
import com.nimbusds.jwt.SignedJWT;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GeihouResolvingActiveSigningSecretProviderTest {

    private static final Instant NOW = Instant.parse("2026-06-14T10:30:00Z");
    private static final String ACTIVE_KID = "active-kid-202606";

    private final GeihouSigningSecret activeSecret = secret(ACTIVE_KID);

    @Test
    void shouldResolveActiveSigningSecretByCurrentKid() {
        AtomicReference<String> resolvedKid = new AtomicReference<>();
        GeihouResolvingActiveSigningSecretProvider provider = new GeihouResolvingActiveSigningSecretProvider(
                () -> Optional.of(ACTIVE_KID),
                kid -> {
                    resolvedKid.set(kid);
                    return Optional.of(activeSecret);
                });

        Optional<GeihouSigningSecret> result = provider.current();

        assertThat(result).hasValue(activeSecret);
        assertThat(resolvedKid).hasValue(ACTIVE_KID);
    }

    @Test
    void shouldReturnEmptyForMissingOrBlankActiveKidWithoutCallingResolver() {
        assertThat(providerWithCountingResolver(() -> null).current()).isEmpty();
        assertThat(providerWithCountingResolver(Optional::empty).current()).isEmpty();
        assertThat(providerWithCountingResolver(() -> Optional.of("")).current()).isEmpty();
        assertThat(providerWithCountingResolver(() -> Optional.of(" ")).current()).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenActiveKidProviderThrowsWithoutCallingResolver() {
        GeihouResolvingActiveSigningSecretProvider provider = providerWithCountingResolver(() -> {
            throw new IllegalStateException("active kid unavailable");
        });

        assertThat(provider.current()).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenResolverReturnsEmptyOrThrows() {
        GeihouResolvingActiveSigningSecretProvider unknownKidProvider = new GeihouResolvingActiveSigningSecretProvider(
                () -> Optional.of(ACTIVE_KID),
                kid -> Optional.empty());
        GeihouResolvingActiveSigningSecretProvider nullResolverProvider = new GeihouResolvingActiveSigningSecretProvider(
                () -> Optional.of(ACTIVE_KID),
                kid -> null);
        GeihouResolvingActiveSigningSecretProvider failingResolverProvider =
                new GeihouResolvingActiveSigningSecretProvider(
                        () -> Optional.of(ACTIVE_KID),
                        kid -> {
                            throw new IllegalStateException("resolver unavailable");
                        });

        assertThat(unknownKidProvider.current()).isEmpty();
        assertThat(nullResolverProvider.current()).isEmpty();
        assertThat(failingResolverProvider.current()).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenResolvedSecretKidDoesNotMatchActiveKid() {
        GeihouResolvingActiveSigningSecretProvider provider = new GeihouResolvingActiveSigningSecretProvider(
                () -> Optional.of(ACTIVE_KID),
                kid -> Optional.of(secret("other-kid")));

        assertThat(provider.current()).isEmpty();
    }

    @Test
    void shouldFeedAccessTokenIssuerAndVerifyIssuedToken() throws Exception {
        GeihouResolvingActiveSigningSecretProvider activeProvider = new GeihouResolvingActiveSigningSecretProvider(
                () -> Optional.of(ACTIVE_KID),
                kid -> ACTIVE_KID.equals(kid) ? Optional.of(activeSecret) : Optional.empty());
        GeihouAccessTokenIssuer issuer = new GeihouAccessTokenIssuer(
                activeProvider,
                Clock.fixed(NOW, ZoneOffset.UTC));
        GeihouAccessTokenRequest request = new GeihouAccessTokenRequest(
                100L,
                200L,
                GeihouAccessTokenAudience.ADMIN,
                "OWNER",
                List.of("OWNER"));

        Optional<String> issuedToken = issuer.issue(request);

        assertThat(issuedToken).isPresent();
        SignedJWT signedJWT = SignedJWT.parse(issuedToken.get());
        assertThat(signedJWT.getHeader().getKeyID()).isEqualTo(ACTIVE_KID);
        assertThat(new GeihouJwtHeaderParser().parse(issuedToken.get()).map(GeihouJwtHeader::kid))
                .hasValue(ACTIVE_KID);
        AuthTokenVerifyRespDTO verified = new GeihouJwtTokenParser(
                "geihou-platform",
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC))
                .verifyToken(issuedToken.get(), activeSecret.keyBytes());
        assertThat(verified.getValid()).isTrue();
        assertThat(verified.getTokenId()).isNotBlank();
        assertThat(verified.getRoles()).containsExactly("OWNER");
    }

    @Test
    void shouldRejectNullConstructorArguments() {
        assertThatThrownBy(() -> new GeihouResolvingActiveSigningSecretProvider(
                null, kid -> Optional.of(activeSecret)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GeihouResolvingActiveSigningSecretProvider(
                () -> Optional.of(ACTIVE_KID), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldKeepVerifierFacingSigningSecretProviderContractUnchanged() {
        Method[] methods = GeihouSigningSecretProvider.class.getDeclaredMethods();

        assertThat(methods).hasSize(1);
        assertThat(methods[0].getName()).isEqualTo("resolve");
        assertThat(methods[0].getParameterTypes()).containsExactly(String.class);
        assertThat(methods[0].getReturnType()).isEqualTo(Optional.class);
    }

    private GeihouResolvingActiveSigningSecretProvider providerWithCountingResolver(
            GeihouActiveKidProvider activeKidProvider) {
        AtomicInteger calls = new AtomicInteger();
        return new GeihouResolvingActiveSigningSecretProvider(
                activeKidProvider,
                kid -> {
                    calls.incrementAndGet();
                    throw new AssertionError("resolver must not be called");
                }) {
            @Override
            public Optional<GeihouSigningSecret> current() {
                Optional<GeihouSigningSecret> result = super.current();
                assertThat(calls).hasValue(0);
                return result;
            }
        };
    }

    private static GeihouSigningSecret secret(String kid) {
        byte[] secretBytes = new byte[64];
        for (int i = 0; i < secretBytes.length; i++) {
            secretBytes[i] = (byte) (i + 11);
        }
        return new GeihouSigningSecret(kid, "HS512", secretBytes);
    }
}
