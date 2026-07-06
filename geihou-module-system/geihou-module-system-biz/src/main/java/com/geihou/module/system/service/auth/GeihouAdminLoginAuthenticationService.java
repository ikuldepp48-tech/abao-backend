package com.geihou.module.system.service.auth;

import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Credential-proof boundary for PRD 0-05 backstage login.
 *
 * <p>This service proves username/password/captcha/account-state enough to
 * produce an already-authenticated subject for the later token wiring layer.
 * It does not sign JWTs, resolve RBAC roles, issue refresh tokens, write public
 * HTTP responses, or call {@link GeihouAuthLoginTokenWiringService}.
 */
public class GeihouAdminLoginAuthenticationService {

    private static final String ACTIVE = "ACTIVE";
    private static final String LOCKED = "LOCKED";
    private static final int MAX_FAIL_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final UserLookupPort userLookupPort;
    private final PasswordVerifierPort passwordVerifierPort;
    private final CaptchaPort captchaPort;
    private final TenantCodeResolverPort tenantCodeResolverPort;
    private final LoginAttemptRecorderPort loginAttemptRecorderPort;
    private final LoginStatePort loginStatePort;
    private final Clock clock;

    public GeihouAdminLoginAuthenticationService(
            UserLookupPort userLookupPort,
            PasswordVerifierPort passwordVerifierPort,
            CaptchaPort captchaPort,
            TenantCodeResolverPort tenantCodeResolverPort,
            LoginAttemptRecorderPort loginAttemptRecorderPort,
            LoginStatePort loginStatePort) {
        this(userLookupPort, passwordVerifierPort, captchaPort, tenantCodeResolverPort,
                loginAttemptRecorderPort, loginStatePort, Clock.systemDefaultZone());
    }

    GeihouAdminLoginAuthenticationService(
            UserLookupPort userLookupPort,
            PasswordVerifierPort passwordVerifierPort,
            CaptchaPort captchaPort,
            TenantCodeResolverPort tenantCodeResolverPort,
            LoginAttemptRecorderPort loginAttemptRecorderPort,
            LoginStatePort loginStatePort,
            Clock clock) {
        this.userLookupPort = Objects.requireNonNull(userLookupPort, "userLookupPort must not be null");
        this.passwordVerifierPort = Objects.requireNonNull(passwordVerifierPort, "passwordVerifierPort must not be null");
        this.captchaPort = Objects.requireNonNull(captchaPort, "captchaPort must not be null");
        this.tenantCodeResolverPort = Objects.requireNonNull(
                tenantCodeResolverPort, "tenantCodeResolverPort must not be null");
        this.loginAttemptRecorderPort = Objects.requireNonNull(
                loginAttemptRecorderPort, "loginAttemptRecorderPort must not be null");
        this.loginStatePort = Objects.requireNonNull(loginStatePort, "loginStatePort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public AuthenticationResult authenticate(GeihouAdminLoginAuthenticationCommand command) {
        if (command == null || isBlank(command.username()) || isBlank(command.password())) {
            record(command, AuthenticationStatus.DENIED, DenyReason.INVALID_REQUEST);
            return AuthenticationResult.denied(DenyReason.INVALID_REQUEST);
        }

        String username = command.username().trim();
        Optional<Optional<Long>> requestedTenantId = resolveRequestedTenantId(command);
        if (requestedTenantId.isEmpty()) {
            record(command, AuthenticationStatus.DENIED, DenyReason.UNKNOWN_TENANT);
            return AuthenticationResult.denied(DenyReason.UNKNOWN_TENANT);
        }

        Optional<LoginUser> userOptional = userLookupPort.findByUsername(username, requestedTenantId.get());
        if (userOptional.isEmpty()) {
            record(command, AuthenticationStatus.DENIED, DenyReason.UNKNOWN_USER);
            return AuthenticationResult.denied(DenyReason.UNKNOWN_USER);
        }

        LoginUser user = userOptional.get();
        LocalDateTime now = LocalDateTime.now(clock);
        if (LOCKED.equals(user.status())) {
            if (user.lockUntil() == null || now.isBefore(user.lockUntil())) {
                record(command, AuthenticationStatus.DENIED, DenyReason.ACCOUNT_LOCKED);
                return AuthenticationResult.denied(DenyReason.ACCOUNT_LOCKED);
            }
            loginStatePort.resetAfterLockExpired(user.userId(), now);
            user = user.withLoginState(ACTIVE, 0, null);
        }

        if (!ACTIVE.equals(user.status())) {
            record(command, AuthenticationStatus.DENIED, DenyReason.USER_NOT_ACTIVE);
            return AuthenticationResult.denied(DenyReason.USER_NOT_ACTIVE);
        }

        if (captchaPort.captchaRequired(user.userId(), user.loginFailCount())
                && !captchaPort.verify(command.captchaKey(), command.captcha())) {
            record(command, AuthenticationStatus.DENIED, DenyReason.CAPTCHA_INVALID);
            return AuthenticationResult.denied(DenyReason.CAPTCHA_INVALID);
        }

        if (!passwordVerifierPort.verify(command.password(), user.passwordHash(), user.passwordSalt())) {
            int nextFailCount = user.loginFailCount() + 1;
            if (nextFailCount >= MAX_FAIL_ATTEMPTS) {
                LocalDateTime lockUntil = now.plus(LOCK_DURATION);
                loginStatePort.recordPasswordFailure(user.userId(), nextFailCount, lockUntil, now);
                record(command, AuthenticationStatus.DENIED, DenyReason.ACCOUNT_LOCKED);
                return AuthenticationResult.denied(DenyReason.ACCOUNT_LOCKED);
            }
            loginStatePort.recordPasswordFailure(user.userId(), nextFailCount, null, now);
            record(command, AuthenticationStatus.DENIED, DenyReason.INVALID_PASSWORD);
            return AuthenticationResult.denied(DenyReason.INVALID_PASSWORD);
        }

        Optional<GeihouAccessTokenUserRole> userRole = GeihouAccessTokenUserRole.fromCode(user.userRole());
        if (userRole.isEmpty() || !userRole.get().acceptsTenantId(user.tenantId())) {
            record(command, AuthenticationStatus.DENIED, DenyReason.INVALID_ROLE_SCOPE);
            return AuthenticationResult.denied(DenyReason.INVALID_ROLE_SCOPE);
        }

        if (requiresTwoFactor(userRole.get(), user.twoFactorEnabled())) {
            loginStatePort.resetAfterSuccessfulAuthentication(user.userId(), command.clientIp(), now);
            record(command, AuthenticationStatus.TWO_FACTOR_REQUIRED, null);
            return AuthenticationResult.twoFactorRequired(user.userId(), user.tenantId(), userRole.get());
        }

        GeihouAdminLoginAuthenticatedSubject subject = new GeihouAdminLoginAuthenticatedSubject(
                user.userId(), user.tenantId(), userRole.get(), false);
        loginStatePort.resetAfterSuccessfulAuthentication(user.userId(), command.clientIp(), now);
        record(command, AuthenticationStatus.AUTHENTICATED, null);
        return AuthenticationResult.authenticated(subject);
    }

    private Optional<Optional<Long>> resolveRequestedTenantId(GeihouAdminLoginAuthenticationCommand command) {
        if (isBlank(command.tenantCode())) {
            return Optional.of(Optional.empty());
        }
        return tenantCodeResolverPort.resolveTenantId(command.tenantCode().trim())
                .map(Optional::of);
    }

    private void record(GeihouAdminLoginAuthenticationCommand command,
                        AuthenticationStatus status,
                        DenyReason denyReason) {
        String username = command == null ? null : command.username();
        String clientIp = command == null ? null : command.clientIp();
        String userAgent = command == null ? null : command.userAgent();
        loginAttemptRecorderPort.record(new LoginAttempt(username, status, denyReason, clientIp, userAgent));
    }

    private static boolean requiresTwoFactor(GeihouAccessTokenUserRole userRole, boolean twoFactorEnabled) {
        return twoFactorEnabled
                || GeihouAccessTokenUserRole.CONSULTANT == userRole
                || GeihouAccessTokenUserRole.PLATFORM_ADMIN == userRole;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public interface UserLookupPort {
        Optional<LoginUser> findByUsername(String username, Optional<Long> requestedTenantId);
    }

    public interface PasswordVerifierPort {
        boolean verify(String rawPassword, String passwordHash, String passwordSalt);
    }

    public interface CaptchaPort {
        boolean captchaRequired(long userId, int loginFailCount);

        boolean verify(String captchaKey, String captcha);
    }

    public interface TenantCodeResolverPort {
        Optional<Long> resolveTenantId(String tenantCode);
    }

    public interface LoginAttemptRecorderPort {
        void record(LoginAttempt attempt);
    }

    public interface LoginStatePort {
        void recordPasswordFailure(long userId, int loginFailCount, LocalDateTime lockUntil, LocalDateTime now);

        void resetAfterSuccessfulAuthentication(long userId, String clientIp, LocalDateTime now);

        void resetAfterLockExpired(long userId, LocalDateTime now);
    }

    public interface TwoFactorTempTokenPort {
        TempTokenIssue issue(long userId, long tenantId, String userRole);

        TempTokenConsumeResult inspect(String tempToken);

        TempTokenConsumeResult consume(String tempToken);

        TempTokenFailureResult recordFailure(String tempToken);
    }

    public record LoginUser(
            long userId,
            long tenantId,
            String userRole,
            String username,
            String passwordHash,
            String passwordSalt,
            boolean twoFactorEnabled,
            String status,
            int loginFailCount,
            LocalDateTime lockUntil) {

        LoginUser withLoginState(String status, int loginFailCount, LocalDateTime lockUntil) {
            return new LoginUser(
                    userId,
                    tenantId,
                    userRole,
                    username,
                    passwordHash,
                    passwordSalt,
                    twoFactorEnabled,
                    status,
                    loginFailCount,
                    lockUntil);
        }
    }

    public record LoginAttempt(
            String username,
            AuthenticationStatus status,
            DenyReason denyReason,
            String clientIp,
            String userAgent) {
    }

    public record AuthenticationResult(
            AuthenticationStatus status,
            GeihouAdminLoginAuthenticatedSubject subject,
            TwoFactorChallengeSubject twoFactorSubject,
            DenyReason denyReason) {

        public static AuthenticationResult authenticated(GeihouAdminLoginAuthenticatedSubject subject) {
            return new AuthenticationResult(AuthenticationStatus.AUTHENTICATED, subject, null, null);
        }

        public static AuthenticationResult twoFactorRequired(
                long userId,
                long tenantId,
                GeihouAccessTokenUserRole userRole) {
            return new AuthenticationResult(
                    AuthenticationStatus.TWO_FACTOR_REQUIRED,
                    null,
                    new TwoFactorChallengeSubject(userId, tenantId, userRole),
                    null);
        }

        public static AuthenticationResult denied(DenyReason denyReason) {
            return new AuthenticationResult(AuthenticationStatus.DENIED, null, null, denyReason);
        }
    }

    public record TwoFactorChallengeSubject(
            long userId,
            long tenantId,
            GeihouAccessTokenUserRole userRole) {
    }

    public record TempTokenIssue(String tempToken, LocalDateTime expireTime) {
    }

    public record TempTokenConsumeResult(long userId, long tenantId, String userRole) {
    }

    public record TempTokenFailureResult(
            TempTokenConsumeResult subject,
            int failCount,
            boolean thresholdExceeded) {
    }

    public enum AuthenticationStatus {
        AUTHENTICATED,
        TWO_FACTOR_REQUIRED,
        DENIED
    }

    public enum DenyReason {
        INVALID_REQUEST,
        UNKNOWN_TENANT,
        UNKNOWN_USER,
        USER_NOT_ACTIVE,
        ACCOUNT_LOCKED,
        CAPTCHA_INVALID,
        INVALID_PASSWORD,
        INVALID_ROLE_SCOPE
    }
}
