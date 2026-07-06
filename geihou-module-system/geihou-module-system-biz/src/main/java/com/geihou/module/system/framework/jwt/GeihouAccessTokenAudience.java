package com.geihou.module.system.framework.jwt;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * PRD 0-05 access-token audiences and TTLs.
 */
public enum GeihouAccessTokenAudience {

    CUSTOMER("customer", Duration.ofMinutes(15)),
    STAFF("staff", Duration.ofHours(8)),
    // Compile-gate mapping: revisit when the role-to-audience matrix is implemented.
    ADMIN("admin", Duration.ofHours(8)),
    CONSULTANT("consultant", Duration.ofHours(4)),
    PLATFORM("platform", Duration.ofHours(2));

    private final String value;
    private final Duration ttl;

    GeihouAccessTokenAudience(String value, Duration ttl) {
        this.value = value;
        this.ttl = ttl;
    }

    public String value() {
        return value;
    }

    public Duration ttl() {
        return ttl;
    }

    public static Optional<GeihouAccessTokenAudience> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(audience -> audience.value.equals(value))
                .findFirst();
    }
}
