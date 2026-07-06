package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.geihou.module.system.dal.dataobject.auth.AuthLoginAttemptDO;
import com.geihou.module.system.dal.mysql.auth.AuthLoginAttemptRepository;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.DenyReason;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.TwoFactorAttempt;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.TwoFactorAttemptStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GeihouTwoFactorAttemptRecorderAdapterTest {

    private final AuthLoginAttemptRepository repository = mock(AuthLoginAttemptRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-20T03:00:00Z"), ZoneId.of("UTC"));
    private final GeihouTwoFactorAttemptRecorderAdapter adapter =
            new GeihouTwoFactorAttemptRecorderAdapter(repository, clock);

    @Test
    void shouldPersistDeniedTwoFactorAttemptSnapshot() {
        adapter.record(new TwoFactorAttempt(
                " consultant-user ",
                TwoFactorAttemptStatus.DENIED,
                DenyReason.TOTP_MISMATCH,
                " 1.2.3.4 ",
                " Geihou-Test-Agent "));

        ArgumentCaptor<AuthLoginAttemptDO> captor = ArgumentCaptor.forClass(AuthLoginAttemptDO.class);
        verify(repository).insert(captor.capture());
        AuthLoginAttemptDO entity = captor.getValue();
        assertThat(entity.getUsername()).isEqualTo("consultant-user");
        assertThat(entity.getAuthStatus()).isEqualTo("DENIED");
        assertThat(entity.getDenyReason()).isEqualTo("TOTP_MISMATCH");
        assertThat(entity.getClientIp()).isEqualTo("1.2.3.4");
        assertThat(entity.getUserAgent()).isEqualTo("Geihou-Test-Agent");
        assertThat(entity.getAttemptTime()).isEqualTo(LocalDateTime.parse("2026-06-20T03:00:00"));
        assertThat(entity.getCreateTime()).isEqualTo(LocalDateTime.parse("2026-06-20T03:00:00"));
    }

    @Test
    void shouldNormalizeBlankOptionalFieldsToNull() {
        adapter.record(new TwoFactorAttempt(
                " ",
                TwoFactorAttemptStatus.DENIED,
                DenyReason.TEMP_TOKEN_INVALID,
                "",
                null));

        ArgumentCaptor<AuthLoginAttemptDO> captor = ArgumentCaptor.forClass(AuthLoginAttemptDO.class);
        verify(repository).insert(captor.capture());
        AuthLoginAttemptDO entity = captor.getValue();
        assertThat(entity.getUsername()).isNull();
        assertThat(entity.getAuthStatus()).isEqualTo("DENIED");
        assertThat(entity.getDenyReason()).isEqualTo("TEMP_TOKEN_INVALID");
        assertThat(entity.getClientIp()).isNull();
        assertThat(entity.getUserAgent()).isNull();
    }
}
