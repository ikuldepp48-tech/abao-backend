package com.geihou.module.system.service.auth;

import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import com.geihou.module.system.framework.jwt.GeihouRbacRoleCodes;

import java.util.List;
import java.util.Objects;

/**
 * Already-authenticated identity data required to issue an access token.
 */
public final class GeihouAccessTokenIssueCommand {

    private final long userId;
    private final long tenantId;
    private final String userRole;
    private final List<String> roles;
    private final boolean stepUpVerified;

    public GeihouAccessTokenIssueCommand(long userId,
                                         long tenantId,
                                         String userRole,
                                         List<String> roles,
                                         boolean stepUpVerified) {
        if (userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (tenantId < 0L) {
            throw new IllegalArgumentException("tenantId must not be negative");
        }
        this.userRole = GeihouAccessTokenUserRole.fromCode(requireText(userRole, "userRole must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("userRole must be a canonical ENUM_USER_ROLE code"))
                .code();
        this.roles = validateRoles(roles);
        this.stepUpVerified = stepUpVerified;
        this.userId = userId;
        this.tenantId = tenantId;
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

    public boolean stepUpVerified() {
        return stepUpVerified;
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
