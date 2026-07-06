package com.geihou.module.system.service.auth;

import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;

/**
 * Already-authenticated subject that can be passed to the token wiring layer.
 */
public record GeihouAdminLoginAuthenticatedSubject(
        long userId,
        long tenantId,
        GeihouAccessTokenUserRole userRole,
        boolean stepUpVerified) {

    public GeihouAdminLoginAuthenticatedSubject {
        if (userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (tenantId < 0L) {
            throw new IllegalArgumentException("tenantId must not be negative");
        }
        if (userRole == null) {
            throw new IllegalArgumentException("userRole must not be null");
        }
        if (!userRole.acceptsTenantId(tenantId)) {
            throw new IllegalArgumentException("userRole must accept tenantId");
        }
    }
}
