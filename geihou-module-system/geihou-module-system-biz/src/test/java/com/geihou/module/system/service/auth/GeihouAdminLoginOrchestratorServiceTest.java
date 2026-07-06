package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.AuthenticationResult;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.DenyReason;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TempTokenIssue;
import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService.LoginOrchestrationResult;
import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService.OrchestrationDenyReason;
import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService.OrchestrationStatus;
import com.geihou.module.system.service.auth.GeihouRefreshTokenPort.RefreshTokenIssueResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeihouAdminLoginOrchestratorServiceTest {

    private final GeihouAdminLoginAuthenticationService authenticationService =
            mock(GeihouAdminLoginAuthenticationService.class);
    private final GeihouAuthLoginTokenWiringService tokenWiringService =
            mock(GeihouAuthLoginTokenWiringService.class);
    private final GeihouRefreshTokenPort refreshTokenPort = mock(GeihouRefreshTokenPort.class);
    private final GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort twoFactorTempTokenPort =
            mock(GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort.class);
    private final GeihouAdminLoginOrchestratorService service = new GeihouAdminLoginOrchestratorService(
            authenticationService, tokenWiringService, refreshTokenPort, twoFactorTempTokenPort);

    @Test
    void deniedAuthenticationReturnsDeniedResultWithoutTokenCalls() {
        GeihouAdminLoginAuthenticationCommand command = command();
        when(authenticationService.authenticate(command))
                .thenReturn(AuthenticationResult.denied(DenyReason.INVALID_PASSWORD));

        LoginOrchestrationResult result = service.orchestrate(command);

        assertThat(result.status()).isEqualTo(OrchestrationStatus.DENIED);
        assertThat(result.denyReason()).isEqualTo(OrchestrationDenyReason.INVALID_PASSWORD);
        assertThat(result.accessToken()).isNull();
        assertThat(result.refreshToken()).isNull();
        verify(tokenWiringService, never()).issueLoginToken(anyLong(), anyLong(), any(), anyBoolean(), anyString());
        verify(refreshTokenPort, never()).issue(anyLong(), anyLong(), anyString());
        verify(twoFactorTempTokenPort, never()).issue(anyLong(), anyLong(), anyString());
    }

    @Test
    void authenticatedOwnerReturnsAccessAndRefreshToken() {
        GeihouAdminLoginAuthenticationCommand command = command();
        GeihouAdminLoginAuthenticatedSubject subject = new GeihouAdminLoginAuthenticatedSubject(
                2001L, 1L, GeihouAccessTokenUserRole.OWNER, false);
        when(authenticationService.authenticate(command)).thenReturn(AuthenticationResult.authenticated(subject));
        when(tokenWiringService.issueLoginToken(2001L, 1L, GeihouAccessTokenUserRole.OWNER, false, "h157k"))
                .thenReturn(Optional.of(new GeihouAccessTokenIssueResult(
                        "access-token", 3600L, 2001L, 1L, "OWNER", List.of("OWNER"))));
        when(refreshTokenPort.issue(2001L, 1L, "OWNER"))
                .thenReturn(new RefreshTokenIssueResult("refresh-token", LocalDateTime.parse("2026-06-27T03:00:00")));

        LoginOrchestrationResult result = service.orchestrate(command);

        assertThat(result.status()).isEqualTo(OrchestrationStatus.SUCCESS);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.expiresInSeconds()).isEqualTo(3600L);
        assertThat(result.userId()).isEqualTo(2001L);
        assertThat(result.tenantId()).isEqualTo(1L);
        assertThat(result.userRole()).isEqualTo("OWNER");
    }

    @Test
    void authenticatedButRoleResolutionFailsRevokesRefreshTokenAndReturnsDenied() {
        GeihouAdminLoginAuthenticationCommand command = command();
        GeihouAdminLoginAuthenticatedSubject subject = new GeihouAdminLoginAuthenticatedSubject(
                2001L, 1L, GeihouAccessTokenUserRole.OWNER, false);
        when(authenticationService.authenticate(command)).thenReturn(AuthenticationResult.authenticated(subject));
        when(refreshTokenPort.issue(2001L, 1L, "OWNER"))
                .thenReturn(new RefreshTokenIssueResult("refresh-token", LocalDateTime.parse("2026-06-27T03:00:00")));
        when(tokenWiringService.issueLoginToken(2001L, 1L, GeihouAccessTokenUserRole.OWNER, false, "h157k"))
                .thenReturn(Optional.empty());

        LoginOrchestrationResult result = service.orchestrate(command);

        assertThat(result.status()).isEqualTo(OrchestrationStatus.DENIED);
        assertThat(result.denyReason()).isEqualTo(OrchestrationDenyReason.ROLE_RESOLUTION_FAILED);
        verify(refreshTokenPort).issue(2001L, 1L, "OWNER");
        verify(refreshTokenPort).revoke("refresh-token", "ACCESS_TOKEN_ISSUE_FAILED");
    }

    @Test
    void authenticatedButAccessTokenIssueThrowsRevokesRefreshTokenAndReturnsDenied() {
        GeihouAdminLoginAuthenticationCommand command = command();
        GeihouAdminLoginAuthenticatedSubject subject = new GeihouAdminLoginAuthenticatedSubject(
                2001L, 1L, GeihouAccessTokenUserRole.OWNER, false);
        when(authenticationService.authenticate(command)).thenReturn(AuthenticationResult.authenticated(subject));
        when(refreshTokenPort.issue(2001L, 1L, "OWNER"))
                .thenReturn(new RefreshTokenIssueResult("refresh-token", LocalDateTime.parse("2026-06-27T03:00:00")));
        when(tokenWiringService.issueLoginToken(2001L, 1L, GeihouAccessTokenUserRole.OWNER, false, "h157k"))
                .thenThrow(new IllegalStateException("down"));

        LoginOrchestrationResult result = service.orchestrate(command);

        assertThat(result.status()).isEqualTo(OrchestrationStatus.DENIED);
        assertThat(result.denyReason()).isEqualTo(OrchestrationDenyReason.ROLE_RESOLUTION_FAILED);
        verify(refreshTokenPort).revoke("refresh-token", "ACCESS_TOKEN_ISSUE_FAILED");
    }

    @Test
    void twoFactorRequiredReturnsRealTempToken() {
        GeihouAdminLoginAuthenticationCommand command = command();
        when(authenticationService.authenticate(command))
                .thenReturn(AuthenticationResult.twoFactorRequired(
                        3001L, 0L, GeihouAccessTokenUserRole.CONSULTANT));
        when(twoFactorTempTokenPort.issue(3001L, 0L, "CONSULTANT"))
                .thenReturn(new TempTokenIssue("temp-token", LocalDateTime.parse("2026-06-20T03:05:00")));

        LoginOrchestrationResult result = service.orchestrate(command);

        assertThat(result.status()).isEqualTo(OrchestrationStatus.TWO_FACTOR_REQUIRED);
        assertThat(result.tempToken()).isEqualTo("temp-token");
        assertThat(result.tempTokenExpireTime()).isEqualTo(LocalDateTime.parse("2026-06-20T03:05:00"));
        assertThat(result.twoFactorMethod()).isEqualTo("TOTP");
        assertThat(result.accessToken()).isNull();
        assertThat(result.refreshToken()).isNull();
        verify(tokenWiringService, never()).issueLoginToken(anyLong(), anyLong(), any(), anyBoolean(), anyString());
    }

    @Test
    void twoFactorTempTokenFailureReturnsDenied() {
        GeihouAdminLoginAuthenticationCommand command = command();
        when(authenticationService.authenticate(command))
                .thenReturn(AuthenticationResult.twoFactorRequired(
                        3001L, 0L, GeihouAccessTokenUserRole.CONSULTANT));
        when(twoFactorTempTokenPort.issue(3001L, 0L, "CONSULTANT")).thenThrow(new IllegalStateException("down"));

        LoginOrchestrationResult result = service.orchestrate(command);

        assertThat(result.status()).isEqualTo(OrchestrationStatus.DENIED);
        assertThat(result.denyReason()).isEqualTo(OrchestrationDenyReason.TEMP_TOKEN_ISSUE_FAILED);
    }

    @Test
    void twoFactorTempTokenInvalidIssueReturnsDenied() {
        GeihouAdminLoginAuthenticationCommand command = command();
        when(authenticationService.authenticate(command))
                .thenReturn(AuthenticationResult.twoFactorRequired(
                        3001L, 0L, GeihouAccessTokenUserRole.CONSULTANT));
        when(twoFactorTempTokenPort.issue(3001L, 0L, "CONSULTANT"))
                .thenReturn(new TempTokenIssue(" ", LocalDateTime.parse("2026-06-20T03:05:00")));

        LoginOrchestrationResult result = service.orchestrate(command);

        assertThat(result.status()).isEqualTo(OrchestrationStatus.DENIED);
        assertThat(result.denyReason()).isEqualTo(OrchestrationDenyReason.TEMP_TOKEN_ISSUE_FAILED);
    }

    @Test
    void refreshTokenFailureReturnsDeniedBeforeAccessTokenIssue() {
        GeihouAdminLoginAuthenticationCommand command = command();
        GeihouAdminLoginAuthenticatedSubject subject = new GeihouAdminLoginAuthenticatedSubject(
                2001L, 1L, GeihouAccessTokenUserRole.OWNER, false);
        when(authenticationService.authenticate(command)).thenReturn(AuthenticationResult.authenticated(subject));
        when(refreshTokenPort.issue(2001L, 1L, "OWNER")).thenThrow(new IllegalStateException("down"));

        LoginOrchestrationResult result = service.orchestrate(command);

        assertThat(result.status()).isEqualTo(OrchestrationStatus.DENIED);
        assertThat(result.denyReason()).isEqualTo(OrchestrationDenyReason.REFRESH_TOKEN_ISSUE_FAILED);
        assertThat(result.accessToken()).isNull();
        assertThat(result.refreshToken()).isNull();
        verify(tokenWiringService, never()).issueLoginToken(anyLong(), anyLong(), any(), anyBoolean(), anyString());
    }

    @Test
    void refreshTokenInvalidIssueReturnsDenied() {
        GeihouAdminLoginAuthenticationCommand command = command();
        GeihouAdminLoginAuthenticatedSubject subject = new GeihouAdminLoginAuthenticatedSubject(
                2001L, 1L, GeihouAccessTokenUserRole.OWNER, false);
        when(authenticationService.authenticate(command)).thenReturn(AuthenticationResult.authenticated(subject));
        when(refreshTokenPort.issue(2001L, 1L, "OWNER"))
                .thenReturn(new RefreshTokenIssueResult(" ", LocalDateTime.parse("2026-06-27T03:00:00")));

        LoginOrchestrationResult result = service.orchestrate(command);

        assertThat(result.status()).isEqualTo(OrchestrationStatus.DENIED);
        assertThat(result.denyReason()).isEqualTo(OrchestrationDenyReason.REFRESH_TOKEN_ISSUE_FAILED);
        verify(tokenWiringService, never()).issueLoginToken(anyLong(), anyLong(), any(), anyBoolean(), anyString());
    }

    @Test
    void shouldRejectNullCommandAndNullDependencies() {
        assertThatThrownBy(() -> service.orchestrate(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GeihouAdminLoginOrchestratorService(
                null, tokenWiringService, refreshTokenPort, twoFactorTempTokenPort))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GeihouAdminLoginOrchestratorService(
                authenticationService, null, refreshTokenPort, twoFactorTempTokenPort))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GeihouAdminLoginOrchestratorService(
                authenticationService, tokenWiringService, null, twoFactorTempTokenPort))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GeihouAdminLoginOrchestratorService(
                authenticationService, tokenWiringService, refreshTokenPort, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static GeihouAdminLoginAuthenticationCommand command() {
        return new GeihouAdminLoginAuthenticationCommand(
                "owner",
                "password",
                "abao",
                "1234",
                "captcha-key",
                "127.0.0.1",
                "JUnit",
                "h157k");
    }
}
