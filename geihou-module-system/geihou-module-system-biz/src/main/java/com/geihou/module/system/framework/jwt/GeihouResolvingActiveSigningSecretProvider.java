package com.geihou.module.system.framework.jwt;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the issuer-side active signing secret from an active key id.
 */
public class GeihouResolvingActiveSigningSecretProvider implements GeihouActiveSigningSecretProvider {

    private final GeihouActiveKidProvider activeKidProvider;
    private final GeihouSigningSecretProvider signingSecretProvider;

    public GeihouResolvingActiveSigningSecretProvider(
            GeihouActiveKidProvider activeKidProvider,
            GeihouSigningSecretProvider signingSecretProvider) {
        this.activeKidProvider = Objects.requireNonNull(activeKidProvider, "activeKidProvider must not be null");
        this.signingSecretProvider = Objects.requireNonNull(
                signingSecretProvider, "signingSecretProvider must not be null");
    }

    @Override
    public Optional<GeihouSigningSecret> current() {
        Optional<String> activeKid = currentKid();
        if (activeKid.isEmpty()) {
            return Optional.empty();
        }

        Optional<GeihouSigningSecret> resolvedSecret = resolve(activeKid.get());
        if (resolvedSecret.isEmpty()) {
            return Optional.empty();
        }
        GeihouSigningSecret secret = resolvedSecret.get();
        if (!activeKid.get().equals(secret.kid())) {
            return Optional.empty();
        }
        return Optional.of(secret);
    }

    private Optional<String> currentKid() {
        try {
            Optional<String> currentKid = activeKidProvider.currentKid();
            if (currentKid == null || currentKid.isEmpty() || currentKid.get().isBlank()) {
                return Optional.empty();
            }
            return currentKid;
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<GeihouSigningSecret> resolve(String activeKid) {
        try {
            Optional<GeihouSigningSecret> secret = signingSecretProvider.resolve(activeKid);
            return secret == null ? Optional.empty() : secret;
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }
}
