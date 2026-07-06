package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.AuthenticationStatus;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.DenyReason;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.LoginAttempt;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.LoginUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeihouAdminLoginAuthenticationServiceTest {

    private final List<LoginAttempt> attempts = new ArrayList<>();
    private final List<String> stateEvents = new ArrayList<>();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-20T02:00:00Z"), ZoneId.of("UTC"));

    @Test
    void shouldDenyBlankUsernameOrPassword() {
        GeihouAdminLoginAuthenticationService service = newService(Optional.empty(), true, false, true);

        GeihouAdminLoginAuthenticationService.AuthenticationResult result =
                service.authenticate(command(" ", "password"));

        assertThat(result.status()).isEqualTo(AuthenticationStatus.DENIED);
        assertThat(result.denyReason()).isEqualTo(DenyReason.INVALID_REQUEST);
        assertThat(attempts).hasSize(1);
    }

    @Test
    void shouldDenyUnknownUser() {
        GeihouAdminLoginAuthenticationService service = newService(Optional.empty(), true, false, true);

        GeihouAdminLoginAuthenticationService.AuthenticationResult result =
                service.authenticate(command("owner", "password"));

        assertThat(result.status()).isEqualTo(AuthenticationStatus.DENIED);
        assertThat(result.denyReason()).isEqualTo(DenyReason.UNKNOWN_USER);
    }

    @Test
    void shouldDenyInvalidPassword() {
        GeihouAdminLoginAuthenticationService service = newService(
                Optional.of(ownerUser()), false, false, true);

        GeihouAdminLoginAuthenticationService.AuthenticationResult result =
                service.authenticate(command("owner", "wrong"));

        assertThat(result.status()).isEqualTo(AuthenticationStatus.DENIED);
        assertThat(result.denyReason()).isEqualTo(DenyReason.INVALID_PASSWORD);
        assertThat(stateEvents).containsExactly("failure:1001:1:null");
    }

    @Test
    void shouldLockAccountOnFifthPasswordFailure() {
        GeihouAdminLoginAuthenticationService service = newService(
                Optional.of(ownerUserWithFailCount(4)), false, false, true);

        GeihouAdminLoginAuthenticationService.AuthenticationResult result =
                service.authenticate(command("owner", "wrong"));

        assertThat(result.status()).isEqualTo(AuthenticationStatus.DENIED);
        assertThat(result.denyReason()).isEqualTo(DenyReason.ACCOUNT_LOCKED);
        assertThat(stateEvents).containsExactly("failure:1001:5:2026-06-20T02:30");
    }

    @Test
    void shouldDenyLockedAccountBeforeLockExpires() {
        GeihouAdminLoginAuthenticationService service = newService(
                Optional.of(lockedOwner(LocalDateTime.parse("2026-06-20T02:30:00"))), true, true, true);

        GeihouAdminLoginAuthenticationService.AuthenticationResult result =
                service.authenticate(command("owner", "password"));

        assertThat(result.status()).isEqualTo(AuthenticationStatus.DENIED);
        assertThat(result.denyReason()).isEqualTo(DenyReason.ACCOUNT_LOCKED);
        assertThat(stateEvents).isEmpty();
    }

    @Test
    void shouldAutoUnlockExpiredLockAndAuthenticate() {
        GeihouAdminLoginAuthenticationService service = newService(
                Optional.of(lockedOwner(LocalDateTime.parse("2026-06-20T01:59:59"))), true, false, true);

        GeihouAdminLoginAuthenticationService.AuthenticationResult result =
                service.authenticate(command("owner", "password"));

        assertThat(result.status()).isEqualTo(AuthenticationStatus.AUTHENTICATED);
        assertThat(stateEvents).containsExactly(
                "unlock:1001:2026-06-20T02:00",
                "success:1001:127.0.0.1:2026-06-20T02:00");
    }

    @Test
    void shouldDenyInvalidCaptchaBeforePasswordSuccess() {
        GeihouAdminLoginAuthenticationService service = newService(
                Optional.of(ownerUserWithFailures()), true, true, false);

        GeihouAdminLoginAuthenticationService.AuthenticationResult result =
                service.authenticate(command("owner", "password"));

        assertThat(result.status()).isEqualTo(AuthenticationStatus.DENIED);
        assertThat(result.denyReason()).isEqualTo(DenyReason.CAPTCHA_INVALID);
    }

    @Test
    void shouldAuthenticateTenantScopeOwnerWithoutStepUp() {
        GeihouAdminLoginAuthenticationService service = newService(
                Optional.of(ownerUser()), true, false, true);

        GeihouAdminLoginAuthenticationService.AuthenticationResult result =
                service.authenticate(command("owner", "password"));

        assertThat(result.status()).isEqualTo(AuthenticationStatus.AUTHENTICATED);
        assertThat(result.subject()).isEqualTo(new GeihouAdminLoginAuthenticatedSubject(
                1001L, 2L, GeihouAccessTokenUserRole.OWNER, false));
        assertThat(result.twoFactorSubject()).isNull();
        assertThat(result.denyReason()).isNull();
        assertThat(stateEvents).containsExactly("success:1001:127.0.0.1:2026-06-20T02:00");
    }

    @Test
    void shouldRequireTwoFactorForConsultant() {
        GeihouAdminLoginAuthenticationService service = newService(
                Optional.of(consultantUser()), true, false, true);

        GeihouAdminLoginAuthenticationService.AuthenticationResult result =
                service.authenticate(platformCommand("consultant", "password"));

        assertThat(result.status()).isEqualTo(AuthenticationStatus.TWO_FACTOR_REQUIRED);
        assertThat(result.subject()).isNull();
        assertThat(result.twoFactorSubject().userId()).isEqualTo(2001L);
        assertThat(result.twoFactorSubject().tenantId()).isZero();
        assertThat(result.twoFactorSubject().userRole()).isEqualTo(GeihouAccessTokenUserRole.CONSULTANT);
        assertThat(stateEvents).containsExactly("success:2001:127.0.0.1:2026-06-20T02:00");
    }

    @Test
    void shouldRequireTwoFactorForPlatformAdmin() {
        GeihouAdminLoginAuthenticationService service = newService(
                Optional.of(platformAdminUser()), true, false, true);

        GeihouAdminLoginAuthenticationService.AuthenticationResult result =
                service.authenticate(platformCommand("platform", "password"));

        assertThat(result.status()).isEqualTo(AuthenticationStatus.TWO_FACTOR_REQUIRED);
        assertThat(result.subject()).isNull();
        assertThat(result.twoFactorSubject().userRole()).isEqualTo(GeihouAccessTokenUserRole.PLATFORM_ADMIN);
    }

    @Test
    void shouldDenyInvalidRoleScope() {
        LoginUser badScope = new LoginUser(
                3001L, 0L, GeihouAccessTokenUserRole.OWNER.code(),
                "owner", "hash", "salt", false, "ACTIVE", 0, null);
        GeihouAdminLoginAuthenticationService service = newService(Optional.of(badScope), true, false, true);

        GeihouAdminLoginAuthenticationService.AuthenticationResult result =
                service.authenticate(platformCommand("owner", "password"));

        assertThat(result.status()).isEqualTo(AuthenticationStatus.DENIED);
        assertThat(result.denyReason()).isEqualTo(DenyReason.INVALID_ROLE_SCOPE);
    }

    private GeihouAdminLoginAuthenticationService newService(
            Optional<LoginUser> user,
            boolean passwordValid,
            boolean captchaRequired,
            boolean captchaValid) {
        return new GeihouAdminLoginAuthenticationService(
                (username, requestedTenantId) -> user,
                (rawPassword, passwordHash, passwordSalt) -> passwordValid,
                new GeihouAdminLoginAuthenticationService.CaptchaPort() {
                    @Override
                    public boolean captchaRequired(long userId, int loginFailCount) {
                        return captchaRequired;
                    }

                    @Override
                    public boolean verify(String captchaKey, String captcha) {
                        return captchaValid;
                    }
                },
                tenantCode -> "abao".equals(tenantCode) ? Optional.of(2L) : Optional.empty(),
                attempts::add,
                new GeihouAdminLoginAuthenticationService.LoginStatePort() {
                    @Override
                    public void recordPasswordFailure(
                            long userId, int loginFailCount, LocalDateTime lockUntil, LocalDateTime now) {
                        stateEvents.add("failure:" + userId + ":" + loginFailCount + ":" + trim(lockUntil));
                    }

                    @Override
                    public void resetAfterSuccessfulAuthentication(long userId, String clientIp, LocalDateTime now) {
                        stateEvents.add("success:" + userId + ":" + clientIp + ":" + trim(now));
                    }

                    @Override
                    public void resetAfterLockExpired(long userId, LocalDateTime now) {
                        stateEvents.add("unlock:" + userId + ":" + trim(now));
                    }
                },
                clock);
    }

    private static GeihouAdminLoginAuthenticationCommand command(String username, String password) {
        return new GeihouAdminLoginAuthenticationCommand(
                username, password, "abao", "1234", "captcha-key", "127.0.0.1", "JUnit", "h157b-test");
    }

    private static GeihouAdminLoginAuthenticationCommand platformCommand(String username, String password) {
        return new GeihouAdminLoginAuthenticationCommand(
                username, password, null, "1234", "captcha-key", "127.0.0.1", "JUnit", "h157b-test");
    }

    private static LoginUser ownerUser() {
        return new LoginUser(1001L, 2L, GeihouAccessTokenUserRole.OWNER.code(),
                "owner", "hash", "salt", false, "ACTIVE", 0, null);
    }

    private static LoginUser ownerUserWithFailures() {
        return ownerUserWithFailCount(3);
    }

    private static LoginUser ownerUserWithFailCount(int failCount) {
        return new LoginUser(1001L, 2L, GeihouAccessTokenUserRole.OWNER.code(),
                "owner", "hash", "salt", false, "ACTIVE", failCount, null);
    }

    private static LoginUser lockedOwner(LocalDateTime lockUntil) {
        return new LoginUser(1001L, 2L, GeihouAccessTokenUserRole.OWNER.code(),
                "owner", "hash", "salt", false, "LOCKED", 5, lockUntil);
    }

    private static LoginUser consultantUser() {
        return new LoginUser(2001L, 0L, GeihouAccessTokenUserRole.CONSULTANT.code(),
                "consultant", "hash", "salt", true, "ACTIVE", 0, null);
    }

    private static LoginUser platformAdminUser() {
        return new LoginUser(2002L, 0L, GeihouAccessTokenUserRole.PLATFORM_ADMIN.code(),
                "platform", "hash", "salt", true, "ACTIVE", 0, null);
    }

    private static String trim(LocalDateTime value) {
        return value == null ? "null" : value.toString().substring(0, 16);
    }
}
