package com.geihou.framework.security.core.context;

import com.alibaba.ttl.TtlRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GeihouSecurityContextHolderTest {

    @AfterEach
    void tearDown() {
        GeihouSecurityContextHolder.clear();
    }

    @Test
    void shouldSetGetAndClearPrincipal() {
        GeihouPrincipal principal = principal(1L);

        GeihouSecurityContextHolder.set(principal);
        assertThat(GeihouSecurityContextHolder.get()).isEqualTo(principal);

        GeihouSecurityContextHolder.clear();
        assertThat(GeihouSecurityContextHolder.get()).isNull();
    }

    @Test
    void shouldPropagateToChildThreadAndKeepParentValueStable() throws Exception {
        GeihouSecurityContextHolder.set(principal(1L));
        AtomicReference<GeihouPrincipal> childInitialPrincipal = new AtomicReference<>();

        Thread thread = new Thread(() -> {
            childInitialPrincipal.set(GeihouSecurityContextHolder.get());
            GeihouSecurityContextHolder.set(principal(2L));
        });
        thread.start();
        thread.join();

        assertThat(childInitialPrincipal.get().userId()).isEqualTo(1L);
        assertThat(GeihouSecurityContextHolder.get().userId()).isEqualTo(1L);
    }

    @Test
    void shouldPropagateWhenWrappedByTtlRunnable() throws Exception {
        GeihouPrincipal principal = principal(1L);
        GeihouSecurityContextHolder.set(principal);
        AtomicReference<GeihouPrincipal> childPrincipal = new AtomicReference<>();

        Thread thread = new Thread(TtlRunnable.get(() -> childPrincipal.set(GeihouSecurityContextHolder.get())));
        thread.start();
        thread.join();

        assertThat(childPrincipal.get()).isEqualTo(principal);
    }

    private GeihouPrincipal principal(Long userId) {
        return new GeihouPrincipal(userId, "user-" + userId, 10L, Set.of("read"), Set.of("microservice:read"), "token");
    }
}
