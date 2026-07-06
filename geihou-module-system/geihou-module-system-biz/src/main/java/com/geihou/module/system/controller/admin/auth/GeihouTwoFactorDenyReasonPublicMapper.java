package com.geihou.module.system.controller.admin.auth;

import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.DenyReason;
import java.util.Objects;

/**
 * Maps internal {@link DenyReason} values to stable public-safe reason strings returned in the
 * {@code /admin-api/auth/two-factor-verify} denied response. Internal enum names such as
 * {@code USER_NOT_FOUND}, {@code CROSS_CONTEXT_MISMATCH}, {@code TOKEN_ISSUANCE_FAILED} must never
 * reach the client because they leak internal state and user-existence signals. The internal
 * {@link DenyReason} is still preserved in {@code auth_login_attempt} audit rows by the orchestration
 * service; this mapper only affects the HTTP response {@code msg}.
 *
 * <p>The public-safe strings are intentionally distinct from any internal {@code DenyReason.name()}
 * so future internal refactors do not implicitly change the wire contract.
 */
public final class GeihouTwoFactorDenyReasonPublicMapper {

    public static final String TWO_FACTOR_TOKEN_INVALID = "TWO_FACTOR_TOKEN_INVALID";
    public static final String TWO_FACTOR_CODE_INVALID = "TWO_FACTOR_CODE_INVALID";
    public static final String TWO_FACTOR_ATTEMPTS_EXCEEDED = "TWO_FACTOR_ATTEMPTS_EXCEEDED";
    public static final String TWO_FACTOR_DENIED = "TWO_FACTOR_DENIED";

    private GeihouTwoFactorDenyReasonPublicMapper() {
        // utility class
    }

    /**
     * Returns a stable public-safe reason string for the given internal deny reason.
     *
     * @param denyReason internal deny reason; if {@code null}, returns {@link #TWO_FACTOR_TOKEN_INVALID}
     *     to match the controller fallback for missing deny reasons.
     */
    public static String toPublicReason(DenyReason denyReason) {
        DenyReason reason = denyReason == null ? DenyReason.TEMP_TOKEN_INVALID : denyReason;
        switch (reason) {
            case TOTP_MISMATCH:
                return TWO_FACTOR_CODE_INVALID;
            case TEMP_TOKEN_ATTEMPTS_EXCEEDED:
                return TWO_FACTOR_ATTEMPTS_EXCEEDED;
            case TEMP_TOKEN_INVALID:
            case TEMP_TOKEN_ALREADY_CONSUMED:
                return TWO_FACTOR_TOKEN_INVALID;
            // The following internal reasons must never be exposed verbatim because they leak
            // user-existence, configuration state, and infrastructure-failure details.
            case USER_NOT_FOUND:
            case USER_SOFT_DELETED:
            case USER_INACTIVE:
            case CROSS_CONTEXT_MISMATCH:
            case TWO_FACTOR_CONFIG_INCOMPLETE:
            case INVALID_ROLE_SCOPE:
            case REFRESH_TOKEN_ISSUE_FAILED:
            case TOKEN_ISSUANCE_FAILED:
                return TWO_FACTOR_DENIED;
            default:
                // Future-proofing: any newly added DenyReason that this mapper has not been
                // explicitly updated for must fall back to the generic denied reason rather
                // than leaking its name() to the client.
                Objects.requireNonNull(reason, "reason must not be null");
                return TWO_FACTOR_DENIED;
        }
    }
}
