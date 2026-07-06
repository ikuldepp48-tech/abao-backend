package com.geihou.framework.security.core.context;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeihouPrincipalTest {

    @Test
    void shouldDefensivelyCopySets() {
        Set<String> scopes = new HashSet<>(Set.of("read"));
        Set<String> permissions = new HashSet<>(Set.of("microservice:read"));

        GeihouPrincipal principal = new GeihouPrincipal(1L, "admin", 10L, scopes, permissions, "token-1");

        scopes.add("write");
        permissions.add("microservice:write");

        assertThat(principal.tokenScopes()).containsExactly("read");
        assertThat(principal.permissions()).containsExactly("microservice:read");
        assertThatThrownBy(() -> principal.tokenScopes().add("delete"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> principal.permissions().add("delete"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldAcceptNullTenantAndNullSets() {
        GeihouPrincipal principal = new GeihouPrincipal(1L, "admin", null, null, null, "token-1");

        assertThat(principal.tenantId()).isNull();
        assertThat(principal.tokenScopes()).isEmpty();
        assertThat(principal.permissions()).isEmpty();
    }
}
