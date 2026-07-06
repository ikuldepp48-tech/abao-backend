package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geihou.module.system.dal.dataobject.auth.AuthRefreshTokenDO;
import com.geihou.module.system.dal.mysql.auth.AuthRefreshTokenRepository;
import com.geihou.module.system.service.auth.GeihouRefreshTokenPort.RefreshTokenIssueResult;
import com.geihou.module.system.service.auth.GeihouRefreshTokenPort.RefreshTokenVerifyResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class GeihouRefreshTokenIssueServiceTest {

    private final AuthRefreshTokenRepository repository = mock(AuthRefreshTokenRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-20T03:00:00Z"), ZoneId.of("UTC"));
    private final GeihouRefreshTokenIssueService service = new GeihouRefreshTokenIssueService(repository, clock);

    @Test
    void shouldIssueOpaqueRefreshTokenWithSevenDayExpiryWithoutPersistingRawToken() {
        RefreshTokenIssueResult issue = service.issue(2001L, 0L, "CONSULTANT");

        ArgumentCaptor<AuthRefreshTokenDO> captor = ArgumentCaptor.forClass(AuthRefreshTokenDO.class);
        Mockito.verify(repository).insert(captor.capture());
        AuthRefreshTokenDO entity = captor.getValue();

        assertThat(entity.getTokenKey()).hasSize(64);
        assertThat(entity.getTokenKey()).doesNotContain(issue.refreshToken());
        assertThat(entity.getTokenHash()).hasSize(64);
        assertThat(entity.getTokenHash()).doesNotContain(issue.refreshToken());
        assertThat(issue.expireTime()).isEqualTo(LocalDateTime.parse("2026-06-27T03:00:00"));
        assertThat(entity.getUserId()).isEqualTo(2001L);
        assertThat(entity.getTenantId()).isZero();
        assertThat(entity.getUserRole()).isEqualTo("CONSULTANT");
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
    void shouldVerifyValidRefreshTokenRepeatedlyAndReturnSubject() {
        RefreshTokenIssueResult issue = service.issue(2001L, 0L, "CONSULTANT");
        AuthRefreshTokenDO persisted = capturedToken();
        String tokenKey = persisted.getTokenKey();
        LocalDateTime now = LocalDateTime.parse("2026-06-20T03:00:00");
        when(repository.selectUsableByTokenKey(tokenKey, now)).thenReturn(persisted);

        RefreshTokenVerifyResult result = service.verify(issue.refreshToken());
        RefreshTokenVerifyResult replay = service.verify(issue.refreshToken());

        assertThat(result).isEqualTo(new RefreshTokenVerifyResult(2001L, 0L, "CONSULTANT"));
        assertThat(replay).isEqualTo(result);
    }

    @Test
    void shouldFailClosedForBlankMissingExpiredRevokedConsumedOrTamperedToken() {
        assertThat(service.verify(null)).isNull();
        assertThat(service.verify(" ")).isNull();

        LocalDateTime now = LocalDateTime.parse("2026-06-20T03:00:00");
        when(repository.selectUsableByTokenKey(Mockito.anyString(), Mockito.eq(now))).thenReturn(null);
        assertThat(service.verify("missing")).isNull();

        RefreshTokenIssueResult issue = service.issue(2001L, 0L, "CONSULTANT");
        AuthRefreshTokenDO tampered = capturedToken();
        String tokenKey = tampered.getTokenKey();
        tampered.setTokenHash("0".repeat(64));
        when(repository.selectUsableByTokenKey(tokenKey, now)).thenReturn(tampered);

        assertThat(service.verify(issue.refreshToken())).isNull();
    }

    @Test
    void shouldRevokeByHashedLookupKey() {
        RefreshTokenIssueResult issue = service.issue(2001L, 0L, "CONSULTANT");
        AuthRefreshTokenDO persisted = capturedToken();
        LocalDateTime now = LocalDateTime.parse("2026-06-20T03:00:00");
        when(repository.revoke(persisted.getTokenKey(), "ACCESS_TOKEN_ISSUE_FAILED", now)).thenReturn(true);

        assertThat(service.revoke(issue.refreshToken(), " ACCESS_TOKEN_ISSUE_FAILED ")).isTrue();
        assertThat(service.revoke(null, "ACCESS_TOKEN_ISSUE_FAILED")).isFalse();
        assertThat(service.revoke(issue.refreshToken(), " ")).isFalse();
    }

    private AuthRefreshTokenDO capturedToken() {
        ArgumentCaptor<AuthRefreshTokenDO> captor = ArgumentCaptor.forClass(AuthRefreshTokenDO.class);
        Mockito.verify(repository, Mockito.atLeastOnce()).insert(captor.capture());
        return captor.getValue();
    }
}
