package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.geihou.module.system.dal.dataobject.auth.AuthLoginAttemptDO;
import com.geihou.module.system.dal.mysql.auth.AuthLoginAttemptRepository;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.AuthenticationStatus;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.DenyReason;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.LoginAttempt;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GeihouAuthLoginAttemptRecorderAdapterTest {

    private final AuthLoginAttemptRepository repository = mock(AuthLoginAttemptRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-19T10:15:30Z"), ZoneId.of("UTC"));
    private final GeihouAuthLoginAttemptRecorderAdapter adapter =
            new GeihouAuthLoginAttemptRecorderAdapter(repository, clock);

    @Test
    void shouldPersistLoginAttemptSnapshot() {
        adapter.record(new LoginAttempt(
                " owner ",
                AuthenticationStatus.DENIED,
                DenyReason.INVALID_PASSWORD,
                " 127.0.0.1 ",
                "JUnit"));

        ArgumentCaptor<AuthLoginAttemptDO> captor = ArgumentCaptor.forClass(AuthLoginAttemptDO.class);
        verify(repository).insert(captor.capture());

        AuthLoginAttemptDO entity = captor.getValue();
        assertThat(entity.getUsername()).isEqualTo("owner");
        assertThat(entity.getAuthStatus()).isEqualTo("DENIED");
        assertThat(entity.getDenyReason()).isEqualTo("INVALID_PASSWORD");
        assertThat(entity.getClientIp()).isEqualTo("127.0.0.1");
        assertThat(entity.getUserAgent()).isEqualTo("JUnit");
        assertThat(entity.getAttemptTime()).isEqualTo(LocalDateTime.parse("2026-06-19T10:15:30"));
        assertThat(entity.getCreateTime()).isEqualTo(entity.getAttemptTime());
    }

    @Test
    void shouldPersistNullDenyReasonForAuthenticatedAttempt() {
        adapter.record(new LoginAttempt(
                "owner",
                AuthenticationStatus.AUTHENTICATED,
                null,
                null,
                null));

        ArgumentCaptor<AuthLoginAttemptDO> captor = ArgumentCaptor.forClass(AuthLoginAttemptDO.class);
        verify(repository).insert(captor.capture());

        assertThat(captor.getValue().getAuthStatus()).isEqualTo("AUTHENTICATED");
        assertThat(captor.getValue().getDenyReason()).isNull();
    }
}
