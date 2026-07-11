package com.geihou.framework.security.core.constant;

/**
 * Geihou auth error codes from PRD 0-05.
 *
 * <p>The Java values are decimal integers because leading-zero integer
 * literals are octal in Java. Javadoc records the seven-digit PRD display
 * format.</p>
 */
public final class GeihouAuthErrorCodes {

    /** PRD 0-05 display code: 0000002. */
    public static final int UNAUTHORIZED = 2;

    /** PRD 0-05 display code: 0000003. */
    public static final int FORBIDDEN = 3;

    /** PRD 0-05 display code: 0001001. */
    public static final int INVALID_TOKEN = 1001;

    /** PRD 0-05 display code: 0001002. */
    public static final int TOKEN_EXPIRED = 1002;

    /** PRD 0-05 display code: 0001003. */
    public static final int TOKEN_REVOKED = 1003;

    /** PRD 0-05 display code: 0001004. */
    public static final int LOGIN_FAILED = 1004;

    /** PRD 0-05 display code: 0001005. */
    public static final int ACCOUNT_LOCKED = 1005;

    /** PRD 0-05 display code: 0001006. */
    public static final int ACCOUNT_DISABLED = 1006;

    /** PRD 0-05 display code: 0001007. */
    public static final int TWO_FACTOR_REQUIRED = 1007;

    /** PRD 0-05 display code: 0001008. Auth subsystem entirely unavailable (HTTP 503). */
    public static final int AUTH_SERVICE_UNAVAILABLE = 1008;

    private GeihouAuthErrorCodes() {
    }
}
