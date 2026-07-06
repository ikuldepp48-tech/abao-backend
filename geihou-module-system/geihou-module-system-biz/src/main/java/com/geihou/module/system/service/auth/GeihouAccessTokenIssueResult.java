package com.geihou.module.system.service.auth;

import com.geihou.module.system.framework.jwt.GeihouRbacRoleCodes;

import java.util.List;
import java.util.Objects;

/**
 * Access-token-only issue result.
 */
public final class GeihouAccessTokenIssueResult {

    private final String accessToken;
    private final long expiresInSeconds;
    private final long userId;
    private final long tenantId;
    private final String userRole;
    private final List<String> roles;

    public GeihouAccessTokenIssueResult(String accessToken,
                                        long expiresInSeconds,
                                        long userId,
                                        long tenantId,
                                        String userRole,
                                        List<String> roles) {
        this.accessToken = requireText(accessToken, "accessToken must not be blank");
        if (expiresInSeconds <= 0L) {
            throw new IllegalArgumentException("expiresInSeconds must be positive");
        }
        if (userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (tenantId < 0L) {
            throw new IllegalArgumentException("tenantId must not be negative");
        }
        this.userRole = requireText(userRole, "userRole must not be blank");
        this.roles = validateRoles(roles);
        this.expiresInSeconds = expiresInSeconds;
        this.userId = userId;
        this.tenantId = tenantId;
    }

    public String accessToken() {
        return accessToken;
    }

    public long expiresInSeconds() {
        return expiresInSeconds;
    }

    public long userId() {
        return userId;
    }

    public long tenantId() {
        return tenantId;
    }

    public String userRole() {
        return userRole;
    }

    public List<String> roles() {
        return roles;
    }

    private static List<String> validateRoles(List<String> roles) {
        return GeihouRbacRoleCodes.normalize(roles);
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
