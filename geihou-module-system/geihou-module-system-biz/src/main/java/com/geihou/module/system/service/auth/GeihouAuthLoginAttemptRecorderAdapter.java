package com.geihou.module.system.service.auth;

import com.geihou.module.system.dal.dataobject.auth.AuthLoginAttemptDO;
import com.geihou.module.system.dal.mysql.auth.AuthLoginAttemptRepository;
import java.time.Clock;
import java.time.LocalDateTime;

public class GeihouAuthLoginAttemptRecorderAdapter
        implements GeihouAdminLoginAuthenticationService.LoginAttemptRecorderPort {

    private final AuthLoginAttemptRepository repository;
    private final Clock clock;

    public GeihouAuthLoginAttemptRecorderAdapter(AuthLoginAttemptRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    GeihouAuthLoginAttemptRecorderAdapter(AuthLoginAttemptRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void record(GeihouAdminLoginAuthenticationService.LoginAttempt attempt) {
        LocalDateTime now = LocalDateTime.now(clock);
        AuthLoginAttemptDO entity = new AuthLoginAttemptDO();
        entity.setUsername(trimToNull(attempt.username()));
        entity.setAuthStatus(attempt.status().name());
        entity.setDenyReason(attempt.denyReason() == null ? null : attempt.denyReason().name());
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
