package com.geihou.framework.security.core.context;

import java.util.Set;

/**
 * Request-scoped authenticated Geihou principal.
 */
public record GeihouPrincipal(
        Long userId,
        String userName,
        Long tenantId,
        Set<String> tokenScopes,
        Set<String> permissions,
        String tokenId) {

    public GeihouPrincipal {
        tokenScopes = tokenScopes == null ? Set.of() : Set.copyOf(tokenScopes);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
