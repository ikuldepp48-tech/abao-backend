package com.geihou.module.system.service.auth;

import com.geihou.module.system.dal.dataobject.auth.AuthLoginAttemptDO;
import com.geihou.module.system.dal.mysql.auth.AuthLoginAttemptRepository;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.TwoFactorAttempt;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.TwoFactorAttemptRecorderPort;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Persists two-factor denials for audit visibility only.
 *
 * <p>Rows written by this adapter must not be used to derive password login
 * lockout; lockout remains owned by {@code auth_user.login_fail_count/status}.
 */
public class GeihouTwoFactorAttemptRecorderAdapter implements TwoFactorAttemptRecorderPort {

    private final AuthLoginAttemptRepository repository;
    private final Clock clock;

    public GeihouTwoFactorAttemptRecorderAdapter(AuthLoginAttemptRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    GeihouTwoFactorAttemptRecorderAdapter(AuthLoginAttemptRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void record(TwoFactorAttempt attempt) {
        LocalDateTime now = LocalDateTime.now(clock);
        AuthLoginAttemptDO entity = new AuthLoginAttemptDO();
        entity.setUsername(trimToNull(attempt.username()));
        entity.setAuthStatus(attempt.status().name());
        entity.setDenyReason(attempt.denyReason().name());
        entity.setClientIp(trimToNull(attempt.clientIp()));
        entity.setUserAgent(trimToNull(attempt.userAgent()));
        entity.setAttemptTime(now);
        entity.setCreateTime(now);
        repository.insert(entity);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
