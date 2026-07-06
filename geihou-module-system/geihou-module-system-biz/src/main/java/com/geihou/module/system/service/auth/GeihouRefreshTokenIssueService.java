package com.geihou.module.system.service.auth;

import com.geihou.module.system.dal.dataobject.auth.AuthRefreshTokenDO;
import com.geihou.module.system.dal.mysql.auth.AuthRefreshTokenRepository;
import com.geihou.module.system.service.auth.GeihouRefreshTokenPort.RefreshTokenIssueResult;
import com.geihou.module.system.service.auth.GeihouRefreshTokenPort.RefreshTokenVerifyResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

public class GeihouRefreshTokenIssueService implements GeihouRefreshTokenPort {

    private static final Duration DEFAULT_TTL = Duration.ofDays(7);

    private final AuthRefreshTokenRepository repository;
    private final Clock clock;

    public GeihouRefreshTokenIssueService(AuthRefreshTokenRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    GeihouRefreshTokenIssueService(AuthRefreshTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public RefreshTokenIssueResult issue(long userId, long tenantId, String userRole) {
        if (userId <= 0 || tenantId < 0 || isBlank(userRole)) {
            throw new IllegalArgumentException("valid userId, tenantId, and userRole are required");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        String refreshToken = UUID.randomUUID().toString();
        AuthRefreshTokenDO entity = new AuthRefreshTokenDO();
        entity.setTokenKey(hashLookupKey(refreshToken));
        entity.setTokenHash(hash(refreshToken, userId));
        entity.setUserId(userId);
        entity.setTenantId(tenantId);
        entity.setUserRole(userRole.trim());
        entity.setExpireTime(now.plus(DEFAULT_TTL));
        entity.setCreateTime(now);
        repository.insert(entity);
        return new RefreshTokenIssueResult(refreshToken, entity.getExpireTime());
    }

    @Override
    public RefreshTokenVerifyResult verify(String refreshToken) {
        if (isBlank(refreshToken)) {
            return null;
        }
        String token = refreshToken.trim();
        String tokenKey = hashLookupKey(token);
        LocalDateTime now = LocalDateTime.now(clock);
        AuthRefreshTokenDO entity = repository.selectUsableByTokenKey(tokenKey, now);
        if (entity == null || entity.getUserId() == null || entity.getTenantId() == null
                || !hash(token, entity.getUserId()).equals(entity.getTokenHash())) {
            return null;
        }
        return new RefreshTokenVerifyResult(entity.getUserId(), entity.getTenantId(), entity.getUserRole());
    }

    @Override
    public boolean revoke(String refreshToken, String reason) {
        if (isBlank(refreshToken) || isBlank(reason)) {
            return false;
        }
        return repository.revoke(hashLookupKey(refreshToken.trim()), reason.trim(), LocalDateTime.now(clock));
    }

    private static String hashLookupKey(String refreshToken) {
        return hash("refresh-key:" + refreshToken);
    }

    private static String hash(String refreshToken, long userId) {
        return hash(refreshToken + ":" + userId);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
