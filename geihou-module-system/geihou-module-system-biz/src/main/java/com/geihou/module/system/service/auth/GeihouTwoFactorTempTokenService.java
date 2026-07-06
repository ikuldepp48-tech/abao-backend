package com.geihou.module.system.service.auth;

import com.geihou.module.system.dal.dataobject.auth.AuthTwoFactorTempTokenDO;
import com.geihou.module.system.dal.mysql.auth.AuthTwoFactorTempTokenRepository;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TempTokenConsumeResult;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TempTokenFailureResult;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationService.TempTokenIssue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

public class GeihouTwoFactorTempTokenService
        implements GeihouAdminLoginAuthenticationService.TwoFactorTempTokenPort {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final int MAX_2FA_ATTEMPTS = 5;

    private final AuthTwoFactorTempTokenRepository repository;
    private final Clock clock;

    public GeihouTwoFactorTempTokenService(AuthTwoFactorTempTokenRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    GeihouTwoFactorTempTokenService(AuthTwoFactorTempTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public TempTokenIssue issue(long userId, long tenantId, String userRole) {
        if (userId <= 0 || tenantId < 0 || isBlank(userRole)) {
            throw new IllegalArgumentException("valid userId, tenantId, and userRole are required");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        String tempToken = UUID.randomUUID().toString();
        AuthTwoFactorTempTokenDO entity = new AuthTwoFactorTempTokenDO();
        entity.setTempTokenKey(hashLookupKey(tempToken));
        entity.setTokenHash(hash(tempToken, userId));
        entity.setUserId(userId);
        entity.setTenantId(tenantId);
        entity.setUserRole(userRole.trim());
        entity.setExpireTime(now.plus(DEFAULT_TTL));
        entity.setCreateTime(now);
        repository.insert(entity);
        return new TempTokenIssue(tempToken, entity.getExpireTime());
    }

    @Override
    public TempTokenConsumeResult inspect(String tempToken) {
        TokenLookup lookup = lookup(tempToken);
        if (lookup == null) {
            return null;
        }
        return lookup.subject();
    }

    @Override
    public TempTokenConsumeResult consume(String tempToken) {
        TokenLookup lookup = lookup(tempToken);
        if (lookup == null) {
            return null;
        }
        if (!repository.consume(lookup.tokenKey(), lookup.now())) {
            return null;
        }
        return lookup.subject();
    }

    @Override
    public TempTokenFailureResult recordFailure(String tempToken) {
        TokenLookup lookup = lookup(tempToken);
        if (lookup == null) {
            return null;
        }
        if (!repository.recordFailure(lookup.tokenKey(), MAX_2FA_ATTEMPTS, lookup.now())) {
            return null;
        }
        AuthTwoFactorTempTokenDO updated = repository.selectByTokenKey(lookup.tokenKey());
        int failCount = updated == null || updated.getTwoFactorFailCount() == null
                ? 0
                : updated.getTwoFactorFailCount();
        return new TempTokenFailureResult(
                lookup.subject(),
                failCount,
                failCount >= MAX_2FA_ATTEMPTS);
    }

    private TokenLookup lookup(String tempToken) {
        if (isBlank(tempToken)) {
            return null;
        }
        String token = tempToken.trim();
        String tokenKey = hashLookupKey(token);
        LocalDateTime now = LocalDateTime.now(clock);
        AuthTwoFactorTempTokenDO entity = repository.selectUsableByTokenKey(tokenKey, now);
        if (entity == null || entity.getUserId() == null || entity.getTenantId() == null
                || !hash(token, entity.getUserId()).equals(entity.getTokenHash())) {
            return null;
        }
        return new TokenLookup(
                tokenKey,
                now,
                new TempTokenConsumeResult(entity.getUserId(), entity.getTenantId(), entity.getUserRole()));
    }

    private static String hashLookupKey(String tempToken) {
        return hash("2fa-key:" + tempToken);
    }

    private static String hash(String tempToken, long userId) {
        return hash(tempToken + ":" + userId);
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

    private record TokenLookup(String tokenKey, LocalDateTime now, TempTokenConsumeResult subject) {
    }
}
