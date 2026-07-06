package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geihou.module.system.dal.dataobject.auth.AuthTwoFactorTempTokenDO;
import com.geihou.module.system.dal.mysql.auth.AuthTwoFactorTempTokenRepository;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TempTokenConsumeResult;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TempTokenFailureResult;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TempTokenIssue;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class GeihouTwoFactorTempTokenServiceTest {

    private final AuthTwoFactorTempTokenRepository repository = mock(AuthTwoFactorTempTokenRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-20T03:00:00Z"), ZoneId.of("UTC"));
    private final GeihouTwoFactorTempTokenService service = new GeihouTwoFactorTempTokenService(repository, clock);

    @Test
    void shouldIssueOpaqueTokenWithFiveMinuteExpiryWithoutPersistingRawToken() {
        TempTokenIssue issue = service.issue(2001L, 0L, "CONSULTANT");

        ArgumentCaptor<AuthTwoFactorTempTokenDO> captor =
                ArgumentCaptor.forClass(AuthTwoFactorTempTokenDO.class);
        Mockito.verify(repository).insert(captor.capture());
        AuthTwoFactorTempTokenDO entity = captor.getValue();

        assertThat(entity.getTempTokenKey()).hasSize(64);
        assertThat(entity.getTempTokenKey()).doesNotContain(issue.tempToken());
        assertThat(issue.expireTime()).isEqualTo(LocalDateTime.parse("2026-06-20T03:05:00"));
        assertThat(entity.getTokenHash()).hasSize(64);
        assertThat(entity.getTokenHash()).doesNotContain(issue.tempToken());
        assertThat(entity.getUserId()).isEqualTo(2001L);
        assertThat(entity.getTenantId()).isZero();
        assertThat(entity.getUserRole()).isEqualTo("CONSULTANT");
        assertThat(entity.getTwoFactorFailCount()).isNull();
        assertThat(entity.getCreateTime()).isEqualTo(LocalDateTime.parse("2026-06-20T03:00:00"));
    }

    @Test
    void shouldRejectInvalidIssueInput() {
        assertThatThrownBy(() -> service.issue(0L, 0L, "CONSULTANT"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.issue(1L, -1L, "CONSULTANT"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.issue(1L, 0L, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldConsumeValidTokenOnceAndReturnSubject() {
        TempTokenIssue issue = service.issue(2001L, 0L, "CONSULTANT");
        AuthTwoFactorTempTokenDO persisted = capturedToken();
        String tokenKey = persisted.getTempTokenKey();
        LocalDateTime now = LocalDateTime.parse("2026-06-20T03:00:00");
        when(repository.selectUsableByTokenKey(tokenKey, now)).thenReturn(persisted);
        when(repository.consume(tokenKey, now)).thenReturn(true).thenReturn(false);

        TempTokenConsumeResult result = service.consume(issue.tempToken());
        TempTokenConsumeResult replay = service.consume(issue.tempToken());

        assertThat(result).isEqualTo(new TempTokenConsumeResult(2001L, 0L, "CONSULTANT"));
        assertThat(replay).isNull();
    }

    @Test
    void shouldInspectValidTokenSubjectWithoutConsumingIt() {
        TempTokenIssue issue = service.issue(2001L, 0L, "CONSULTANT");
        AuthTwoFactorTempTokenDO persisted = capturedToken();
        String tokenKey = persisted.getTempTokenKey();
        LocalDateTime now = LocalDateTime.parse("2026-06-20T03:00:00");
        when(repository.selectUsableByTokenKey(tokenKey, now)).thenReturn(persisted);
        when(repository.consume(tokenKey, now)).thenReturn(true);

        TempTokenConsumeResult inspected = service.inspect(issue.tempToken());
        TempTokenConsumeResult consumed = service.consume(issue.tempToken());

        assertThat(inspected).isEqualTo(new TempTokenConsumeResult(2001L, 0L, "CONSULTANT"));
        assertThat(consumed).isEqualTo(new TempTokenConsumeResult(2001L, 0L, "CONSULTANT"));
        Mockito.verify(repository, Mockito.times(1)).consume(tokenKey, now);
    }

    @Test
    void shouldFailClosedForBlankMissingExpiredOrTamperedToken() {
        assertThat(service.consume(null)).isNull();
        assertThat(service.consume(" ")).isNull();
        assertThat(service.inspect(null)).isNull();
        assertThat(service.inspect(" ")).isNull();

        LocalDateTime now = LocalDateTime.parse("2026-06-20T03:00:00");
        when(repository.selectUsableByTokenKey(Mockito.anyString(), Mockito.eq(now))).thenReturn(null);
        assertThat(service.consume("missing")).isNull();

        TempTokenIssue issue = service.issue(2001L, 0L, "CONSULTANT");
        AuthTwoFactorTempTokenDO tampered = capturedToken();
        String tokenKey = tampered.getTempTokenKey();
        tampered.setTokenHash("0".repeat(64));
        when(repository.selectUsableByTokenKey(tokenKey, now)).thenReturn(tampered);

        assertThat(service.consume(issue.tempToken())).isNull();
        assertThat(service.inspect(issue.tempToken())).isNull();
    }

    @Test
    void shouldFailClosedWhenAtomicConsumeDoesNotUpdateRow() {
        TempTokenIssue issue = service.issue(2001L, 0L, "CONSULTANT");
        AuthTwoFactorTempTokenDO persisted = capturedToken();
        String tokenKey = persisted.getTempTokenKey();
        LocalDateTime now = LocalDateTime.parse("2026-06-20T03:00:00");
        when(repository.selectUsableByTokenKey(tokenKey, now)).thenReturn(persisted);
        when(repository.consume(tokenKey, now)).thenReturn(false);

        assertThat(service.consume(issue.tempToken())).isNull();
    }

    @Test
    void shouldRecordFailureAndKeepTokenUsableBelowThreshold() {
        TempTokenIssue issue = service.issue(2001L, 0L, "CONSULTANT");
        AuthTwoFactorTempTokenDO persisted = capturedToken();
        String tokenKey = persisted.getTempTokenKey();
        LocalDateTime now = LocalDateTime.parse("2026-06-20T03:00:00");
        AuthTwoFactorTempTokenDO updated = tokenRow(tokenKey, 3, null);
        when(repository.selectUsableByTokenKey(tokenKey, now)).thenReturn(persisted);
        when(repository.recordFailure(tokenKey, 5, now)).thenReturn(true);
        when(repository.selectByTokenKey(tokenKey)).thenReturn(updated);

        TempTokenFailureResult result = service.recordFailure(issue.tempToken());

        assertThat(result).isEqualTo(new TempTokenFailureResult(
                new TempTokenConsumeResult(2001L, 0L, "CONSULTANT"), 3, false));
        Mockito.verify(repository).recordFailure(tokenKey, 5, now);
    }

    @Test
    void shouldRecordFailureAndReportThresholdExceededAtFifthAttempt() {
        TempTokenIssue issue = service.issue(2001L, 0L, "CONSULTANT");
        AuthTwoFactorTempTokenDO persisted = capturedToken();
        String tokenKey = persisted.getTempTokenKey();
        LocalDateTime now = LocalDateTime.parse("2026-06-20T03:00:00");
        AuthTwoFactorTempTokenDO updated = tokenRow(tokenKey, 5, now);
        when(repository.selectUsableByTokenKey(tokenKey, now)).thenReturn(persisted);
        when(repository.recordFailure(tokenKey, 5, now)).thenReturn(true);
        when(repository.selectByTokenKey(tokenKey)).thenReturn(updated);

        TempTokenFailureResult result = service.recordFailure(issue.tempToken());

        assertThat(result).isEqualTo(new TempTokenFailureResult(
                new TempTokenConsumeResult(2001L, 0L, "CONSULTANT"), 5, true));
    }

    @Test
    void shouldFailClosedWhenFailureRecordDoesNotUpdateUsableRow() {
        TempTokenIssue issue = service.issue(2001L, 0L, "CONSULTANT");
        AuthTwoFactorTempTokenDO persisted = capturedToken();
        String tokenKey = persisted.getTempTokenKey();
        LocalDateTime now = LocalDateTime.parse("2026-06-20T03:00:00");
        when(repository.selectUsableByTokenKey(tokenKey, now)).thenReturn(persisted);
        when(repository.recordFailure(tokenKey, 5, now)).thenReturn(false);

        assertThat(service.recordFailure(issue.tempToken())).isNull();
    }

    private AuthTwoFactorTempTokenDO capturedToken() {
        ArgumentCaptor<AuthTwoFactorTempTokenDO> captor =
                ArgumentCaptor.forClass(AuthTwoFactorTempTokenDO.class);
        Mockito.verify(repository, Mockito.atLeastOnce()).insert(captor.capture());
        return captor.getValue();
    }

    private static AuthTwoFactorTempTokenDO tokenRow(String tokenKey, int failCount, LocalDateTime consumedTime) {
        AuthTwoFactorTempTokenDO entity = new AuthTwoFactorTempTokenDO();
        entity.setTempTokenKey(tokenKey);
        entity.setUserId(2001L);
        entity.setTenantId(0L);
        entity.setUserRole("CONSULTANT");
        entity.setTwoFactorFailCount(failCount);
        entity.setConsumedTime(consumedTime);
        return entity;
    }
}
