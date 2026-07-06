package com.geihou.module.system.framework.jwt;

import java.util.Arrays;
import java.util.Optional;

/**
 * Canonical user-role codes from ENUM_USER_ROLE for access-token requests.
 */
public enum GeihouAccessTokenUserRole {

    CUSTOMER("CUSTOMER", GeihouAccessTokenAudience.CUSTOMER, TenantScope.TENANT),
    STORE_STAFF("STORE_STAFF", GeihouAccessTokenAudience.STAFF, TenantScope.TENANT),
    CK_WORKER("CK_WORKER", GeihouAccessTokenAudience.STAFF, TenantScope.TENANT),
    STORE_MANAGER("STORE_MANAGER", GeihouAccessTokenAudience.ADMIN, TenantScope.TENANT),
    CK_MANAGER("CK_MANAGER", GeihouAccessTokenAudience.ADMIN, TenantScope.TENANT),
    OWNER("OWNER", GeihouAccessTokenAudience.ADMIN, TenantScope.TENANT),
    CONSULTANT("CONSULTANT", GeihouAccessTokenAudience.CONSULTANT, TenantScope.PLATFORM),
    PLATFORM_ADMIN("PLATFORM_ADMIN", GeihouAccessTokenAudience.PLATFORM, TenantScope.PLATFORM);

    private final String code;
    private final GeihouAccessTokenAudience audience;
    private final TenantScope tenantScope;

    GeihouAccessTokenUserRole(String code, GeihouAccessTokenAudience audience, TenantScope tenantScope) {
        this.code = code;
        this.audience = audience;
        this.tenantScope = tenantScope;
    }

    public String code() {
        return code;
    }

    public GeihouAccessTokenAudience audience() {
        return audience;
    }

    public boolean acceptsTenantId(long tenantId) {
        return tenantScope.accepts(tenantId);
    }

    public static Optional<GeihouAccessTokenUserRole> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(role -> role.code.equals(code))
                .findFirst();
    }

    private enum TenantScope {
        TENANT {
            @Override
            boolean accepts(long tenantId) {
                return tenantId > 0L;
            }
        },
        PLATFORM {
            @Override
            boolean accepts(long tenantId) {
                return tenantId == 0L;
            }
        };

        abstract boolean accepts(long tenantId);
    }
}
