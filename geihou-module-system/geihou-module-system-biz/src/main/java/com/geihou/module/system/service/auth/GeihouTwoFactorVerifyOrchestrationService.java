package com.geihou.module.system.service.auth;

import com.geihou.module.system.dal.dataobject.auth.AuthUserDO;
import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TempTokenConsumeResult;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TempTokenFailureResult;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort;
import com.geihou.module.system.service.auth.GeihouRefreshTokenPort.RefreshTokenIssueResult;
import java.util.Objects;
import java.util.Optional;

public class GeihouTwoFactorVerifyOrchestrationService {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String OPERATOR = "2FA_VERIFY";
    private static final String REVOKE_REASON_ACCESS_ISSUE_FAILED = "TWO_FACTOR_ACCESS_TOKEN_ISSUE_FAILED";

    private final TwoFactorTempTokenPort tempTokenPort;
    private final AuthUserRepository authUserRepository;
    private final GeihouTotpVerifier totpVerifier;
    private final GeihouAuthLoginTokenWiringService tokenWiringService;
    private final GeihouRefreshTokenPort refreshTokenPort;
    private final TwoFactorAttemptRecorderPort attemptRecorderPort;

    public GeihouTwoFactorVerifyOrchestrationService(
            TwoFactorTempTokenPort tempTokenPort,
            AuthUserRepository authUserRepository,
            GeihouTotpVerifier totpVerifier,
            GeihouAuthLoginTokenWiringService tokenWiringService,
            GeihouRefreshTokenPort refreshTokenPort,
            TwoFactorAttemptRecorderPort attemptRecorderPort) {
        this.tempTokenPort = Objects.requireNonNull(tempTokenPort, "tempTokenPort must not be null");
        this.authUserRepository = Objects.requireNonNull(authUserRepository, "authUserRepository must not be null");
        this.totpVerifier = Objects.requireNonNull(totpVerifier, "totpVerifier must not be null");
        this.tokenWiringService = Objects.requireNonNull(tokenWiringService, "tokenWiringService must not be null");
        this.refreshTokenPort = Objects.requireNonNull(refreshTokenPort, "refreshTokenPort must not be null");
        this.attemptRecorderPort = Objects.requireNonNull(
                attemptRecorderPort, "attemptRecorderPort must not be null");
    }

    @Deprecated
    public TwoFactorVerifyResult verify(String tempToken, String code) {
        return verify(tempToken, code, null, null);
    }

    public TwoFactorVerifyResult verify(String tempToken, String code, String clientIp, String userAgent) {
        TempTokenConsumeResult subject = tempTokenPort.inspect(tempToken);
        if (subject == null) {
            return denied(DenyReason.TEMP_TOKEN_INVALID, null, clientIp, userAgent);
        }
        AuthUserDO user = authUserRepository.selectById(subject.userId());
        DenyReason userDenyReason = validateUser(subject, user);
        if (userDenyReason != null) {
            return denied(userDenyReason, user, clientIp, userAgent);
        }
        if (!totpVerifier.verify(user.getTwoFactorSecret(), code)) {
            TempTokenFailureResult failure = tempTokenPort.recordFailure(tempToken);
            if (failure != null && failure.thresholdExceeded()) {
                return denied(DenyReason.TEMP_TOKEN_ATTEMPTS_EXCEEDED, user, clientIp, userAgent);
            }
            return denied(DenyReason.TOTP_MISMATCH, user, clientIp, userAgent);
        }
        if (tempTokenPort.consume(tempToken) == null) {
            return denied(DenyReason.TEMP_TOKEN_ALREADY_CONSUMED, user, clientIp, userAgent);
        }
        return issueTokens(subject);
    }

    private TwoFactorVerifyResult denied(
            DenyReason denyReason,
            AuthUserDO user,
            String clientIp,
            String userAgent) {
        try {
            attemptRecorderPort.record(new TwoFactorAttempt(
                    user == null ? null : user.getUsername(),
                    TwoFactorAttemptStatus.DENIED,
                    denyReason,
                    clientIp,
                    userAgent));
        } catch (RuntimeException ignored) {
            // Audit persistence must not convert a denial into a 500 or unlock behavior.
        }
        return TwoFactorVerifyResult.denied(denyReason);
    }

    private TwoFactorVerifyResult issueTokens(TempTokenConsumeResult subject) {
        GeihouAccessTokenUserRole userRole;
        try {
            userRole = GeihouAccessTokenUserRole.fromCode(subject.userRole()).orElse(null);
        } catch (RuntimeException ex) {
            return TwoFactorVerifyResult.denied(DenyReason.INVALID_ROLE_SCOPE);
        }
        if (userRole == null) {
            return TwoFactorVerifyResult.denied(DenyReason.INVALID_ROLE_SCOPE);
        }
        RefreshTokenIssueResult refreshToken;
        try {
            refreshToken = refreshTokenPort.issue(subject.userId(), subject.tenantId(), subject.userRole());
            if (refreshToken == null || isBlank(refreshToken.refreshToken())) {
                return TwoFactorVerifyResult.denied(DenyReason.REFRESH_TOKEN_ISSUE_FAILED);
            }
        } catch (RuntimeException ex) {
            return TwoFactorVerifyResult.denied(DenyReason.REFRESH_TOKEN_ISSUE_FAILED);
        }
        Optional<GeihouAccessTokenIssueResult> accessToken;
        try {
            accessToken = tokenWiringService.issueLoginToken(
                    subject.userId(), subject.tenantId(), userRole, true, OPERATOR);
        } catch (RuntimeException ex) {
            refreshTokenPort.revoke(refreshToken.refreshToken(), REVOKE_REASON_ACCESS_ISSUE_FAILED);
            return TwoFactorVerifyResult.denied(DenyReason.TOKEN_ISSUANCE_FAILED);
        }
        if (accessToken.isEmpty()) {
            refreshTokenPort.revoke(refreshToken.refreshToken(), REVOKE_REASON_ACCESS_ISSUE_FAILED);
            return TwoFactorVerifyResult.denied(DenyReason.TOKEN_ISSUANCE_FAILED);
        }
        GeihouAccessTokenIssueResult access = accessToken.get();
        return TwoFactorVerifyResult.success(
                access.accessToken(),
                access.expiresInSeconds(),
                refreshToken.refreshToken(),
                access.userId(),
                access.tenantId(),
                access.userRole());
    }

    private static DenyReason validateUser(TempTokenConsumeResult subject, AuthUserDO user) {
        if (user == null) {
            return DenyReason.USER_NOT_FOUND;
        }
        if (Boolean.TRUE.equals(user.getDeleted())) {
            return DenyReason.USER_SOFT_DELETED;
        }
        if (!Objects.equals(user.getTenantId(), subject.tenantId())
                || !Objects.equals(user.getUserRole(), subject.userRole())) {
            return DenyReason.CROSS_CONTEXT_MISMATCH;
        }
        if (!ACTIVE_STATUS.equals(user.getStatus())) {
            return DenyReason.USER_INACTIVE;
        }
        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled()) || isBlank(user.getTwoFactorSecret())) {
            return DenyReason.TWO_FACTOR_CONFIG_INCOMPLETE;
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record TwoFactorVerifyResult(
            Status status,
            String accessToken,
            long expiresInSeconds,
            String refreshToken,
            Long userId,
            Long tenantId,
            String userRole,
            DenyReason denyReason) {

        public static TwoFactorVerifyResult success(
                String accessToken,
                long expiresInSeconds,
                String refreshToken,
                long userId,
                long tenantId,
                String userRole) {
            return new TwoFactorVerifyResult(
                    Status.SUCCESS, accessToken, expiresInSeconds, refreshToken, userId, tenantId, userRole, null);
        }

        public static TwoFactorVerifyResult denied(DenyReason denyReason) {
            return new TwoFactorVerifyResult(
                    Status.DENIED, null, 0L, null, null, null, null,
                    Objects.requireNonNull(denyReason, "denyReason must not be null"));
        }
    }

    public enum Status {
        SUCCESS,
        DENIED
    }

    public interface TwoFactorAttemptRecorderPort {
        void record(TwoFactorAttempt attempt);
    }

    public record TwoFactorAttempt(
            String username,
            TwoFactorAttemptStatus status,
            DenyReason denyReason,
            String clientIp,
            String userAgent) {
    }

    public enum TwoFactorAttemptStatus {
        DENIED
    }

    public enum DenyReason {
        TEMP_TOKEN_INVALID,
        TEMP_TOKEN_ALREADY_CONSUMED,
        USER_NOT_FOUND,
        USER_SOFT_DELETED,
        USER_INACTIVE,
        CROSS_CONTEXT_MISMATCH,
        TWO_FACTOR_CONFIG_INCOMPLETE,
        TOTP_MISMATCH,
        TEMP_TOKEN_ATTEMPTS_EXCEEDED,
        INVALID_ROLE_SCOPE,
        REFRESH_TOKEN_ISSUE_FAILED,
        TOKEN_ISSUANCE_FAILED
    }
}
