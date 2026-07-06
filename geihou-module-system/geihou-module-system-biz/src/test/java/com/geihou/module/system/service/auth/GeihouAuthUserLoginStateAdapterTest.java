package com.geihou.module.system.service.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GeihouAuthUserLoginStateAdapterTest {

    private final AuthUserRepository repository = mock(AuthUserRepository.class);
    private final GeihouAuthUserLoginStateAdapter adapter = new GeihouAuthUserLoginStateAdapter(repository);

    @Test
    void shouldPersistPasswordFailureWithoutLock() {
        LocalDateTime now = LocalDateTime.parse("2026-06-20T02:00:00");

        adapter.recordPasswordFailure(1001L, 3, null, now);

        verify(repository).updateLoginFailureState(1001L, 3, "ACTIVE", null, null, now);
    }

    @Test
    void shouldPersistPasswordFailureWithLock() {
        LocalDateTime now = LocalDateTime.parse("2026-06-20T02:00:00");
        LocalDateTime lockUntil = LocalDateTime.parse("2026-06-20T02:30:00");

        adapter.recordPasswordFailure(1001L, 5, lockUntil, now);

        verify(repository).updateLoginFailureState(
                1001L, 5, "LOCKED", "LOGIN_FAIL_LOCKED", lockUntil, now);
    }

    @Test
    void shouldResetAfterSuccessOrExpiredLock() {
        LocalDateTime now = LocalDateTime.parse("2026-06-20T02:00:00");

        adapter.resetAfterSuccessfulAuthentication(1001L, "127.0.0.1", now);
        adapter.resetAfterLockExpired(1001L, now);

        verify(repository).resetLoginState(1001L, "127.0.0.1", now);
        verify(repository).resetLoginState(1001L, null, now);
    }
}
