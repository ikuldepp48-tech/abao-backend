package com.geihou.module.system.framework.jwt;

import java.util.List;
import java.util.Objects;

/**
 * Minimal data required to issue a PRD 0-05 access token.
 */
public final class GeihouAccessTokenRequest {

    private final long userId;
    private final long tenantId;
    private final GeihouAccessTokenAudience audience;
    private final String userRole;
    private final List<String> roles;

    public GeihouAccessTokenRequest(
            long userId,
            long tenantId,
            GeihouAccessTokenAudience audience,
            String userRole,
            List<String> roles) {
        if (userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }
        this.audience = Objects.requireNonNull(audience, "audience must not be null");
        GeihouAccessTokenUserRole canonicalUserRole = validateUserRole(userRole, this.audience);
        validateTenantId(tenantId, canonicalUserRole);
        this.userRole = canonicalUserRole.code();
        this.roles = validateRoles(roles);
        this.userId = userId;
        this.tenantId = tenantId;
    }

    public long userId() {
        return userId;
    }

    public long tenantId() {
        return tenantId;
    }

    public GeihouAccessTokenAudience audience() {
        return audience;
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

    private static GeihouAccessTokenUserRole validateUserRole(
            String userRole, GeihouAccessTokenAudience audience) {
        String roleCode = requireText(userRole, "userRole must not be blank");
        GeihouAccessTokenUserRole canonicalUserRole = GeihouAccessTokenUserRole.fromCode(roleCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "userRole must be a canonical ENUM_USER_ROLE code"));
        if (canonicalUserRole.audience() != audience) {
            throw new IllegalArgumentException("userRole and audience must match");
        }
        return canonicalUserRole;
    }

    private static void validateTenantId(long tenantId, GeihouAccessTokenUserRole userRole) {
        if (tenantId < 0L) {
            throw new IllegalArgumentException("tenantId must not be negative");
        }
        if (!userRole.acceptsTenantId(tenantId)) {
            throw new IllegalArgumentException("tenantId does not match userRole scope");
        }
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
