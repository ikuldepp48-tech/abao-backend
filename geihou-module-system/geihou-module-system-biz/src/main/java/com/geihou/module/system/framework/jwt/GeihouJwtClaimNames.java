package com.geihou.module.system.framework.jwt;

/**
 * JWT header and claim names from PRD 0-05.
 */
public final class GeihouJwtClaimNames {

    public static final String TYPE = "typ";
    public static final String KEY_ID = "kid";

    public static final String ISSUER = "iss";
    public static final String SUBJECT = "sub";
    public static final String AUDIENCE = "aud";
    public static final String ISSUED_AT = "iat";
    public static final String EXPIRES_AT = "exp";
    public static final String JWT_ID = "jti";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ROLE = "userRole";
    public static final String ROLES = "roles";

    private GeihouJwtClaimNames() {
    }
}
