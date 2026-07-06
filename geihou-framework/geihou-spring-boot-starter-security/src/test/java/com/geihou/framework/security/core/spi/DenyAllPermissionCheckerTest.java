package com.geihou.framework.security.core.spi;

import com.geihou.framework.security.core.context.GeihouPrincipal;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DenyAllPermissionCheckerTest {

    @Test
    void shouldDenyEveryPermission() {
        DenyAllPermissionChecker checker = new DenyAllPermissionChecker();
        GeihouPrincipal principal = new GeihouPrincipal(
                1L, "admin", 10L, Set.of("read"), Set.of("*:*:*"), "token-1");

        assertThat(checker.hasPermission(principal, "microservice:read")).isFalse();
        assertThat(checker.hasPermission(principal, "*:*:*")).isFalse();
        assertThat(checker.hasPermission(null, "microservice:read")).isFalse();
    }
}
