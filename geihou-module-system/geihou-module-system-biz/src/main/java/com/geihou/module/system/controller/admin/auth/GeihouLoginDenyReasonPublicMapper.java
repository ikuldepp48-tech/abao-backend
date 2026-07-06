package com.geihou.module.system.controller.admin.auth;

import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService.OrchestrationDenyReason;
import java.util.Objects;

/**
 * Maps internal {@link OrchestrationDenyReason} values to stable public-safe reason strings
 * returned in the {@code /admin-api/auth/login} denied response. Internal enum names such as
 * {@code UNKNOWN_USER}, {@code UNKNOWN_TENANT}, {@code INVALID_PASSWORD}, {@code ROLE_RESOLUTION_FAILED},
 * {@code REFRESH_TOKEN_ISSUE_FAILED}, {@code INTERNAL_ERROR} must never reach the client because they
 * leak user/tenant existence, account state, and infrastructure-failure details. The internal
 * {@link OrchestrationDenyReason} is still preserved in {@code auth_login_attempt} audit rows by the
 * orchestration service; this mapper only affects the HTTP response {@code msg}.
 *
 * <p>The public-safe strings are intentionally distinct from any internal
 * {@code OrchestrationDenyReason.name()} so future internal refactors do not implicitly change the
 * wire contract. They are also distinct from any {@code DenyReason.name()} of the 2FA verify path
 * to avoid cross-endpoint contract confusion.
 *
 * <p>Security note: {@code UNKNOWN_TENANT}, {@code UNKNOWN_USER}, and {@code INVALID_PASSWORD} all
 * collapse to {@link #LOGIN_CREDENTIALS_INVALID} so the response cannot be used to enumerate valid
 * tenants, valid usernames, or distinguish wrong-password from wrong-user. {@code ACCOUNT_LOCKED} is
 * surfaced as {@link #LOGIN_ACCOUNT_LOCKED} because lockout is an actionable state for the account
 * holder and is only reachable after the account was already identified by the authentication flow.
 */
public final class GeihouLoginDenyReasonPublicMapper {

    public static final String LOGIN_BAD_REQUEST = "LOGIN_BAD_REQUEST";
    public static final String LOGIN_CREDENTIALS_INVALID = "LOGIN_CREDENTIALS_INVALID";
    public static final String LOGIN_CAPTCHA_INVALID = "LOGIN_CAPTCHA_INVALID";
    public static final String LOGIN_ACCOUNT_LOCKED = "LOGIN_ACCOUNT_LOCKED";
    public static final String LOGIN_DENIED = "LOGIN_DENIED";

    private GeihouLoginDenyReasonPublicMapper() {
        // utility class
    }

    /**
     * Returns a stable public-safe reason string for the given internal login deny reason.
     *
     * @param denyReason internal deny reason; if {@code null}, returns {@link #LOGIN_DENIED} to
     *     match the controller fallback for missing deny reasons.
     */
    public static String toPublicReason(OrchestrationDenyReason denyReason) {
        OrchestrationDenyReason reason =
                denyReason == null ? OrchestrationDenyReason.INTERNAL_ERROR : denyReason;
        switch (reason) {
            case INVALID_REQUEST:
                return LOGIN_BAD_REQUEST;
            case CAPTCHA_INVALID:
                return LOGIN_CAPTCHA_INVALID;
            case ACCOUNT_LOCKED:
                return LOGIN_ACCOUNT_LOCKED;
            // UNKNOWN_TENANT, UNKNOWN_USER, and INVALID_PASSWORD all collapse to the same
            // public reason so the response cannot be used to enumerate valid tenants/users
            // or distinguish wrong-password from wrong-user.
            case UNKNOWN_TENANT:
            case UNKNOWN_USER:
            case INVALID_PASSWORD:
                return LOGIN_CREDENTIALS_INVALID;
            // The following internal reasons must never be exposed verbatim because they leak
            // account state, role/config state, and infrastructure-failure details.
            case USER_NOT_ACTIVE:
            case INVALID_ROLE_SCOPE:
            case ROLE_RESOLUTION_FAILED:
            case TEMP_TOKEN_ISSUE_FAILED:
            case REFRESH_TOKEN_ISSUE_FAILED:
            case INTERNAL_ERROR:
                return LOGIN_DENIED;
            default:
                // Future-proofing: any newly added OrchestrationDenyReason that this mapper has
                // not been explicitly updated for must fall back to the generic denied reason
                // rather than leaking its name() to the client.
                Objects.requireNonNull(reason, "reason must not be null");
                return LOGIN_DENIED;
        }
    }
}
