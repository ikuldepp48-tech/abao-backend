package com.geihou.module.system.service.auth;

import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import java.time.LocalDateTime;

public class GeihouAuthUserLoginStateAdapter
        implements GeihouAdminLoginAuthenticationService.LoginStatePort {

    private static final String ACTIVE = "ACTIVE";
    private static final String LOCKED = "LOCKED";
    private static final String LOCKED_REASON = "LOGIN_FAIL_LOCKED";

    private final AuthUserRepository repository;

    public GeihouAuthUserLoginStateAdapter(AuthUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public void recordPasswordFailure(long userId, int loginFailCount, LocalDateTime lockUntil, LocalDateTime now) {
        boolean locked = lockUntil != null;
        repository.updateLoginFailureState(
                userId,
                loginFailCount,
                locked ? LOCKED : ACTIVE,
                locked ? LOCKED_REASON : null,
                lockUntil,
                now);
    }

    @Override
    public void resetAfterSuccessfulAuthentication(long userId, String clientIp, LocalDateTime now) {
        repository.resetLoginState(userId, clientIp, now);
    }

    @Override
    public void resetAfterLockExpired(long userId, LocalDateTime now) {
        repository.resetLoginState(userId, null, now);
    }
}
