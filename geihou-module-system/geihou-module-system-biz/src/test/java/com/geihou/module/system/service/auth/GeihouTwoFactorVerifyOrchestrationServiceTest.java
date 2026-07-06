package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geihou.module.system.dal.dataobject.auth.AuthUserDO;
import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TempTokenConsumeResult;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TempTokenFailureResult;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort;
import com.geihou.module.system.service.auth.GeihouRefreshTokenPort.RefreshTokenIssueResult;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.DenyReason;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.Status;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.TwoFactorAttempt;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.TwoFactorAttemptRecorderPort;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.TwoFactorVerifyResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("deprecation")
class GeihouTwoFactorVerifyOrchestrationServiceTest {

    private final TwoFactorTempTokenPort tempTokenPort = mock(TwoFactorTempTokenPort.class);
    private final AuthUserRepository authUserRepository = mock(AuthUserRepository.class);
    private final GeihouTotpVerifier totpVerifier = mock(GeihouTotpVerifier.class);
    private final GeihouAuthLoginTokenWiringService tokenWiringService =
            mock(GeihouAuthLoginTokenWiringService.class);
    private final GeihouRefreshTokenPort refreshTokenPort = mock(GeihouRefreshTokenPort.class);
    private final TwoFactorAttemptRecorderPort attemptRecorderPort = mock(TwoFactorAttemptRecorderPort.class);
    private final GeihouTwoFactorVerifyOrchestrationService service =
            new GeihouTwoFactorVerifyOrchestrationService(
                    tempTokenPort,
                    authUserRepository,
                    totpVerifier,
                    tokenWiringService,
                    refreshTokenPort,
                    attemptRecorderPort);

    @Test
    void shouldVerifyTotpConsumeTempTokenAndIssueTokens() {
        validSubjectAndUser();
        when(totpVerifier.verify("encrypted-secret", "123456")).thenReturn(true);
        when(tempTokenPort.consume("temp-token")).thenReturn(subject());
        when(refreshTokenPort.issue(2001L, 1L, "CONSULTANT"))
                .thenReturn(new RefreshTokenIssueResult("refresh-token", LocalDateTime.parse("2026-06-27T03:00:00")));
        when(tokenWiringService.issueLoginToken(
                2001L, 1L, GeihouAccessTokenUserRole.CONSULTANT, true, "2FA_VERIFY"))
                .thenReturn(Optional.of(new GeihouAccessTokenIssueResult(
                        "access-token", 14400L, 2001L, 1L, "CONSULTANT", List.of("CONSULTANT"))));

        TwoFactorVerifyResult result = service.verify("temp-token", "123456");

        assertThat(result.status()).isEqualTo(Status.SUCCESS);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.expiresInSeconds()).isEqualTo(14400L);
        verify(tempTokenPort).consume("temp-token");
        verify(attemptRecorderPort, never()).record(org.mockito.Mockito.any());
    }

    @Test
    void shouldNotConsumeTempTokenWhenTotpCodeDoesNotMatch() {
        validSubjectAndUser();
        when(totpVerifier.verify("encrypted-secret", "000000")).thenReturn(false);
        when(tempTokenPort.recordFailure("temp-token"))
                .thenReturn(new TempTokenFailureResult(subject(), 1, false));

        TwoFactorVerifyResult result = service.verify(
                "temp-token", "000000", "1.2.3.4", "Geihou-Test-Agent");

        assertThat(result.status()).isEqualTo(Status.DENIED);
        assertThat(result.denyReason()).isEqualTo(DenyReason.TOTP_MISMATCH);
        verify(tempTokenPort).recordFailure("temp-token");
        assertRecordedAttempt("consultant-user", DenyReason.TOTP_MISMATCH, "1.2.3.4", "Geihou-Test-Agent");
        verify(tempTokenPort, never()).consume(anyString());
        verify(refreshTokenPort, never()).issue(anyLong(), anyLong(), anyString());
    }

    @Test
    void shouldInvalidateTempTokenWhenTotpFailureThresholdExceeded() {
        validSubjectAndUser();
        when(totpVerifier.verify("encrypted-secret", "000000")).thenReturn(false);
        when(tempTokenPort.recordFailure("temp-token"))
                .thenReturn(new TempTokenFailureResult(subject(), 5, true));

        TwoFactorVerifyResult result = service.verify("temp-token", "000000");

        assertThat(result.status()).isEqualTo(Status.DENIED);
        assertThat(result.denyReason()).isEqualTo(DenyReason.TEMP_TOKEN_ATTEMPTS_EXCEEDED);
        assertRecordedAttempt("consultant-user", DenyReason.TEMP_TOKEN_ATTEMPTS_EXCEEDED, null, null);
        verify(tempTokenPort, never()).consume(anyString());
        verify(refreshTokenPort, never()).issue(anyLong(), anyLong(), anyString());
    }

    @Test
    void shouldDenyWhenTempTokenAlreadyConsumedByConcurrentRequest() {
        validSubjectAndUser();
        when(totpVerifier.verify("encrypted-secret", "123456")).thenReturn(true);
        when(tempTokenPort.consume("temp-token")).thenReturn(null);

        TwoFactorVerifyResult result = service.verify("temp-token", "123456");

        assertThat(result.status()).isEqualTo(Status.DENIED);
        assertThat(result.denyReason()).isEqualTo(DenyReason.TEMP_TOKEN_ALREADY_CONSUMED);
        assertRecordedAttempt("consultant-user", DenyReason.TEMP_TOKEN_ALREADY_CONSUMED, null, null);
        verify(refreshTokenPort, never()).issue(anyLong(), anyLong(), anyString());
    }

    @Test
    void shouldDenyCrossContextMismatchBeforeTotpVerification() {
        when(tempTokenPort.inspect("temp-token")).thenReturn(subject());
        AuthUserDO user = activeUser();
        user.setTenantId(2L);
        when(authUserRepository.selectById(2001L)).thenReturn(user);

        TwoFactorVerifyResult result = service.verify("temp-token", "123456");

        assertThat(result.status()).isEqualTo(Status.DENIED);
        assertThat(result.denyReason()).isEqualTo(DenyReason.CROSS_CONTEXT_MISMATCH);
        assertRecordedAttempt("consultant-user", DenyReason.CROSS_CONTEXT_MISMATCH, null, null);
        verify(totpVerifier, never()).verify(anyString(), anyString());
    }

    @Test
    void shouldRevokeRefreshTokenWhenAccessTokenIssueFails() {
        validSubjectAndUser();
        when(totpVerifier.verify("encrypted-secret", "123456")).thenReturn(true);
        when(tempTokenPort.consume("temp-token")).thenReturn(subject());
        when(refreshTokenPort.issue(2001L, 1L, "CONSULTANT"))
                .thenReturn(new RefreshTokenIssueResult("refresh-token", LocalDateTime.parse("2026-06-27T03:00:00")));
        when(tokenWiringService.issueLoginToken(anyLong(), anyLong(), org.mockito.Mockito.any(), anyBoolean(), anyString()))
                .thenReturn(Optional.empty());

        TwoFactorVerifyResult result = service.verify("temp-token", "123456");

        assertThat(result.status()).isEqualTo(Status.DENIED);
        assertThat(result.denyReason()).isEqualTo(DenyReason.TOKEN_ISSUANCE_FAILED);
        verify(refreshTokenPort).revoke("refresh-token", "TWO_FACTOR_ACCESS_TOKEN_ISSUE_FAILED");
        verify(attemptRecorderPort, never()).record(org.mockito.Mockito.any());
    }

    @Test
    void shouldDenyInvalidTempTokenWithoutUserLookup() {
        when(tempTokenPort.inspect("temp-token")).thenReturn(null);

        TwoFactorVerifyResult result = service.verify(
                "temp-token", "123456", "1.2.3.4", "Geihou-Test-Agent");

        assertThat(result.status()).isEqualTo(Status.DENIED);
        assertThat(result.denyReason()).isEqualTo(DenyReason.TEMP_TOKEN_INVALID);
        assertRecordedAttempt(null, DenyReason.TEMP_TOKEN_INVALID, "1.2.3.4", "Geihou-Test-Agent");
        verify(authUserRepository, never()).selectById(anyLong());
    }

    @Test
    void shouldDenyIncompleteTwoFactorConfig() {
        when(tempTokenPort.inspect("temp-token")).thenReturn(subject());
        AuthUserDO user = activeUser();
        user.setTwoFactorSecret(" ");
        when(authUserRepository.selectById(2001L)).thenReturn(user);

        TwoFactorVerifyResult result = service.verify("temp-token", "123456");

        assertThat(result.status()).isEqualTo(Status.DENIED);
        assertThat(result.denyReason()).isEqualTo(DenyReason.TWO_FACTOR_CONFIG_INCOMPLETE);
        assertRecordedAttempt("consultant-user", DenyReason.TWO_FACTOR_CONFIG_INCOMPLETE, null, null);
        verify(totpVerifier, never()).verify(anyString(), anyString());
    }

    @Test
    void shouldStillDenyWhenAttemptRecorderFails() {
        validSubjectAndUser();
        when(totpVerifier.verify("encrypted-secret", "000000")).thenReturn(false);
        when(tempTokenPort.recordFailure("temp-token"))
                .thenReturn(new TempTokenFailureResult(subject(), 1, false));
        doThrow(new IllegalStateException("audit offline"))
                .when(attemptRecorderPort).record(org.mockito.Mockito.any());

        TwoFactorVerifyResult result = service.verify("temp-token", "000000");

        assertThat(result.status()).isEqualTo(Status.DENIED);
        assertThat(result.denyReason()).isEqualTo(DenyReason.TOTP_MISMATCH);
    }

    private void assertRecordedAttempt(
            String username,
            DenyReason denyReason,
            String clientIp,
            String userAgent) {
        ArgumentCaptor<TwoFactorAttempt> captor = ArgumentCaptor.forClass(TwoFactorAttempt.class);
        verify(attemptRecorderPort).record(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo(username);
        assertThat(captor.getValue().status().name()).isEqualTo("DENIED");
        assertThat(captor.getValue().denyReason()).isEqualTo(denyReason);
        assertThat(captor.getValue().clientIp()).isEqualTo(clientIp);
        assertThat(captor.getValue().userAgent()).isEqualTo(userAgent);
    }

    private void validSubjectAndUser() {
        when(tempTokenPort.inspect("temp-token")).thenReturn(subject());
        when(authUserRepository.selectById(2001L)).thenReturn(activeUser());
    }

    private static TempTokenConsumeResult subject() {
        return new TempTokenConsumeResult(2001L, 1L, "CONSULTANT");
    }

    private static AuthUserDO activeUser() {
        AuthUserDO user = new AuthUserDO();
        user.setId(2001L);
        user.setUsername("consultant-user");
        user.setTenantId(1L);
        user.setUserRole("CONSULTANT");
        user.setStatus("ACTIVE");
        user.setDeleted(false);
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret("encrypted-secret");
        return user;
    }
}
