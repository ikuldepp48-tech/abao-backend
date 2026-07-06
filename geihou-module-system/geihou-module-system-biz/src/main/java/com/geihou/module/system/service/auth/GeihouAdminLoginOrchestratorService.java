package com.geihou.module.system.service.auth;

import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.AuthenticationResult;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.AuthenticationStatus;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.DenyReason;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TempTokenIssue;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TwoFactorChallengeSubject;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort;
import com.geihou.module.system.service.auth.GeihouRefreshTokenPort.RefreshTokenIssueResult;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * H157K service-layer internal login orchestrator.
 *
 * <p>Composes credential authentication, access-token issue, refresh-token
 * issue, and 2FA temp-token issue into an internal result. This class does not
 * expose public HTTP controllers or VOs, does not verify TOTP/SMS codes, and
 * does not implement refresh/logout/auth-me endpoints.
 */
public class GeihouAdminLoginOrchestratorService {

    private static final String TWO_FACTOR_METHOD_TOTP = "TOTP";

    private final GeihouAdminLoginAuthenticationService authenticationService;
    private final GeihouAuthLoginTokenWiringService tokenWiringService;
    private final GeihouRefreshTokenPort refreshTokenPort;
    private final TwoFactorTempTokenPort twoFactorTempTokenPort;

    public GeihouAdminLoginOrchestratorService(
            GeihouAdminLoginAuthenticationService authenticationService,
            GeihouAuthLoginTokenWiringService tokenWiringService,
            GeihouRefreshTokenPort refreshTokenPort,
            TwoFactorTempTokenPort twoFactorTempTokenPort) {
        this.authenticationService = Objects.requireNonNull(
                authenticationService, "authenticationService must not be null");
        this.tokenWiringService = Objects.requireNonNull(tokenWiringService, "tokenWiringService must not be null");
        this.refreshTokenPort = Objects.requireNonNull(refreshTokenPort, "refreshTokenPort must not be null");
        this.twoFactorTempTokenPort = Objects.requireNonNull(
                twoFactorTempTokenPort, "twoFactorTempTokenPort must not be null");
    }

    public LoginOrchestrationResult orchestrate(GeihouAdminLoginAuthenticationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        AuthenticationResult authentication = authenticationService.authenticate(command);
        if (authentication.status() == AuthenticationStatus.DENIED) {
            return LoginOrchestrationResult.denied(mapDenyReason(authentication.denyReason()));
        }
        if (authentication.status() == AuthenticationStatus.TWO_FACTOR_REQUIRED) {
            return issueTwoFactorTempToken(authentication.twoFactorSubject());
        }
        return issueLoginTokens(authentication.subject(), command.operator());
    }

    private LoginOrchestrationResult issueTwoFactorTempToken(TwoFactorChallengeSubject subject) {
        if (subject == null) {
            return LoginOrchestrationResult.denied(OrchestrationDenyReason.INTERNAL_ERROR);
        }
        try {
            TempTokenIssue issue = twoFactorTempTokenPort.issue(
                    subject.userId(), subject.tenantId(), subject.userRole().code());
            if (issue == null || isBlank(issue.tempToken()) || issue.expireTime() == null) {
                return LoginOrchestrationResult.denied(OrchestrationDenyReason.TEMP_TOKEN_ISSUE_FAILED);
            }
            return LoginOrchestrationResult.twoFactorRequired(
                    issue.tempToken(), issue.expireTime(), TWO_FACTOR_METHOD_TOTP);
        } catch (RuntimeException ex) {
            return LoginOrchestrationResult.denied(OrchestrationDenyReason.TEMP_TOKEN_ISSUE_FAILED);
        }
    }

    private LoginOrchestrationResult issueLoginTokens(
            GeihouAdminLoginAuthenticatedSubject subject,
            String operator) {
        if (subject == null) {
            return LoginOrchestrationResult.denied(OrchestrationDenyReason.INTERNAL_ERROR);
        }
        RefreshTokenIssueResult refreshToken;
        try {
            refreshToken = refreshTokenPort.issue(subject.userId(), subject.tenantId(), subject.userRole().code());
            if (refreshToken == null || isBlank(refreshToken.refreshToken())) {
                return LoginOrchestrationResult.denied(OrchestrationDenyReason.REFRESH_TOKEN_ISSUE_FAILED);
            }
        } catch (RuntimeException ex) {
            return LoginOrchestrationResult.denied(OrchestrationDenyReason.REFRESH_TOKEN_ISSUE_FAILED);
        }
        Optional<GeihouAccessTokenIssueResult> accessToken;
        try {
            accessToken = tokenWiringService.issueLoginToken(
                    subject.userId(),
                    subject.tenantId(),
                    subject.userRole(),
                    subject.stepUpVerified(),
                    operator);
        } catch (RuntimeException ex) {
            refreshTokenPort.revoke(refreshToken.refreshToken(), "ACCESS_TOKEN_ISSUE_FAILED");
            return LoginOrchestrationResult.denied(OrchestrationDenyReason.ROLE_RESOLUTION_FAILED);
        }
        if (accessToken.isEmpty()) {
            refreshTokenPort.revoke(refreshToken.refreshToken(), "ACCESS_TOKEN_ISSUE_FAILED");
            return LoginOrchestrationResult.denied(OrchestrationDenyReason.ROLE_RESOLUTION_FAILED);
        }
        GeihouAccessTokenIssueResult access = accessToken.get();
        return LoginOrchestrationResult.success(
                access.accessToken(),
                access.expiresInSeconds(),
                refreshToken.refreshToken(),
                access.userId(),
                access.tenantId(),
                access.userRole());
    }

    private static OrchestrationDenyReason mapDenyReason(DenyReason denyReason) {
        if (denyReason == null) {
            return OrchestrationDenyReason.INTERNAL_ERROR;
        }
        return switch (denyReason) {
            case INVALID_REQUEST -> OrchestrationDenyReason.INVALID_REQUEST;
            case UNKNOWN_TENANT -> OrchestrationDenyReason.UNKNOWN_TENANT;
            case UNKNOWN_USER -> OrchestrationDenyReason.UNKNOWN_USER;
            case USER_NOT_ACTIVE -> OrchestrationDenyReason.USER_NOT_ACTIVE;
            case ACCOUNT_LOCKED -> OrchestrationDenyReason.ACCOUNT_LOCKED;
            case CAPTCHA_INVALID -> OrchestrationDenyReason.CAPTCHA_INVALID;
            case INVALID_PASSWORD -> OrchestrationDenyReason.INVALID_PASSWORD;
            case INVALID_ROLE_SCOPE -> OrchestrationDenyReason.INVALID_ROLE_SCOPE;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record LoginOrchestrationResult(
            OrchestrationStatus status,
            String accessToken,
            long expiresInSeconds,
            String refreshToken,
            Long userId,
            Long tenantId,
            String userRole,
            String tempToken,
            LocalDateTime tempTokenExpireTime,
            String twoFactorMethod,
            OrchestrationDenyReason denyReason) {

        public static LoginOrchestrationResult success(
                String accessToken,
                long expiresInSeconds,
                String refreshToken,
                long userId,
                long tenantId,
                String userRole) {
            return new LoginOrchestrationResult(
                    OrchestrationStatus.SUCCESS,
                    accessToken,
                    expiresInSeconds,
                    refreshToken,
                    userId,
                    tenantId,
                    userRole,
                    null,
                    null,
                    null,
                    null);
        }

        public static LoginOrchestrationResult twoFactorRequired(
                String tempToken,
                LocalDateTime expireTime,
                String method) {
            return new LoginOrchestrationResult(
                    OrchestrationStatus.TWO_FACTOR_REQUIRED,
                    null,
                    0L,
                    null,
                    null,
                    null,
                    null,
                    tempToken,
                    expireTime,
                    method,
                    null);
        }

        public static LoginOrchestrationResult denied(OrchestrationDenyReason reason) {
            return new LoginOrchestrationResult(
                    OrchestrationStatus.DENIED,
                    null,
                    0L,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Objects.requireNonNull(reason, "reason must not be null"));
        }
    }

    public enum OrchestrationStatus {
        SUCCESS,
        TWO_FACTOR_REQUIRED,
        DENIED
    }

    public enum OrchestrationDenyReason {
        INVALID_REQUEST,
        UNKNOWN_TENANT,
        UNKNOWN_USER,
        USER_NOT_ACTIVE,
        ACCOUNT_LOCKED,
        CAPTCHA_INVALID,
        INVALID_PASSWORD,
        INVALID_ROLE_SCOPE,
        ROLE_RESOLUTION_FAILED,
        TEMP_TOKEN_ISSUE_FAILED,
        REFRESH_TOKEN_ISSUE_FAILED,
        INTERNAL_ERROR
    }
}
