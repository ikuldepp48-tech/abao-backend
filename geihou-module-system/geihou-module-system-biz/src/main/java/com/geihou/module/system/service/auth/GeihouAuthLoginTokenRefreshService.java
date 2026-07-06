package com.geihou.module.system.service.auth;

import com.geihou.module.system.api.auth.dto.AuthTokenVerifyRespDTO;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import com.geihou.module.system.framework.jwt.GeihouJwtTokenParser;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Narrow service-layer refresh caller that wires old-token verification,
 * H146 role-permission read model re-resolution, and the existing pure
 * {@link GeihouAccessTokenIssueService}.
 *
 * <p>H154: refresh service-layer code slice — NOT a public
 * controller/API/gateway/frontend. Does not implement login, logout,
 * {@code /auth/me}, B-to-A, no-literal, consultant finance runtime
 * conjunction, token revocation, or step-up verification mechanism.
 *
 * <p>Semantics (frozen by H153):
 * <ol>
 *   <li>Accept {@code oldToken}, {@code hmacSecretKey},
 *       {@code stepUpVerified}, and nonblank {@code operator}.</li>
 *   <li>Verify old token using existing {@link GeihouJwtTokenParser#verifyToken}.
 *       Invalid/expired → {@code Optional.empty()}.</li>
 *   <li>Extract {@code userId}, {@code tenantId}, {@code userRole} from
 *       verified token.</li>
 *   <li>Convert {@code userRole} string to {@link GeihouAccessTokenUserRole};
 *       unknown → {@code Optional.empty()}.</li>
 *   <li>Defensively verify {@code userRole.acceptsTenantId(tenantId)};
 *       mismatch → {@code Optional.empty()}.</li>
 *   <li>Ignore old token {@code roles} claim completely (H153 F-04).</li>
 *   <li>Call H146 {@link GeihouAuthRolePermissionReadService#resolveActiveRoleCodes}
 *       to obtain fresh RBAC role codes.</li>
 *   <li>If fresh roles are null/empty, deny token issuance — do NOT call
 *       {@link GeihouAccessTokenIssueService#issue} (H153 F-05).</li>
 *   <li>If fresh roles are non-empty, construct {@link GeihouAccessTokenIssueCommand}
 *       using fresh roles and caller-supplied {@code stepUpVerified}, then call
 *       {@link GeihouAccessTokenIssueService#issue} (H153 F-07).</li>
 * </ol>
 *
 * <p>JWT permissions prohibition (H153 F-06): this service never accepts,
 * resolves, or passes permissions. Permissions never enter JWT.
 *
 * <p>Old token is NOT invalidated (H153 F-08). Old and new tokens may
 * coexist until natural expiry.
 */
public class GeihouAuthLoginTokenRefreshService {

    private final GeihouAuthRolePermissionReadService rolePermissionReadService;
    private final GeihouAccessTokenIssueService accessTokenIssueService;
    private final GeihouJwtTokenParser jwtTokenParser;

    public GeihouAuthLoginTokenRefreshService(
            GeihouAuthRolePermissionReadService rolePermissionReadService,
            GeihouAccessTokenIssueService accessTokenIssueService,
            GeihouJwtTokenParser jwtTokenParser) {
        this.rolePermissionReadService = Objects.requireNonNull(
                rolePermissionReadService, "rolePermissionReadService must not be null");
        this.accessTokenIssueService = Objects.requireNonNull(
                accessTokenIssueService, "accessTokenIssueService must not be null");
        this.jwtTokenParser = Objects.requireNonNull(
                jwtTokenParser, "jwtTokenParser must not be null");
    }

    /**
     * Refresh a login access token by verifying the old token, re-resolving
     * fresh roles from the H146 read model, and issuing a new token.
     *
     * <p>Old token roles are completely ignored. Fresh roles come exclusively
     * from {@link GeihouAuthRolePermissionReadService#resolveActiveRoleCodes}.
     * Empty fresh roles deny token issuance before the issue service is called.
     *
     * <p>For CONSULTANT/PLATFORM_ADMIN, the caller must supply
     * {@code stepUpVerified=true}; the issue service enforces this internally.
     * For tenant-scope roles, {@code stepUpVerified=false} is acceptable.
     *
     * @param oldToken       the old access token to refresh (must not be blank)
     * @param hmacSecretKey  the HMAC secret key for token verification (must not be null/empty)
     * @param stepUpVerified caller-supplied fresh step-up proof; required for
     *                       CONSULTANT/PLATFORM_ADMIN, optional for tenant-scope roles
     * @param operator       operator string for audit (must not be blank — fail closed)
     * @return {@link Optional#empty()} if denied (invalid token, unknown role,
     *         tenant scope mismatch, empty fresh roles, or issue service failure);
     *         otherwise the issued token result
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public Optional<GeihouAccessTokenIssueResult> refreshLoginToken(
            String oldToken,
            byte[] hmacSecretKey,
            boolean stepUpVerified,
            String operator) {
        // ---- Parameter validation (fail closed) ----
        if (oldToken == null || oldToken.isBlank()) {
            throw new IllegalArgumentException("oldToken must not be blank");
        }
        if (hmacSecretKey == null || hmacSecretKey.length == 0) {
            throw new IllegalArgumentException("hmacSecretKey must not be null or empty");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("operator must not be blank");
        }

        // ---- Step 1: Verify old token using existing parser (H153 F-02) ----
        AuthTokenVerifyRespDTO verified = jwtTokenParser.verifyToken(oldToken, hmacSecretKey);
        if (!Boolean.TRUE.equals(verified.getValid())) {
            return Optional.empty();
        }

        // ---- Step 2: Extract identity from verified token ----
        Long userId = verified.getUserId();
        Long tenantId = verified.getTenantId();
        String userRoleString = verified.getUserRole();

        if (userId == null || userId <= 0L || tenantId == null || tenantId < 0L) {
            return Optional.empty();
        }

        // ---- Step 3: Convert userRole string to enum; fail closed if unknown (H153) ----
        GeihouAccessTokenUserRole userRole = GeihouAccessTokenUserRole.fromCode(userRoleString)
                .orElse(null);
        if (userRole == null) {
            return Optional.empty();
        }

        // ---- Step 4: Defensively verify tenant scope (H153) ----
        if (!userRole.acceptsTenantId(tenantId)) {
            return Optional.empty();
        }

        // ---- Step 5: Ignore old token roles completely (H153 F-04) ----
        // verified.getRoles() is intentionally NOT read or used.

        // ---- Step 6: Resolve fresh roles from H146 read model (H153 F-03) ----
        List<String> freshRoles = rolePermissionReadService.resolveActiveRoleCodes(
                tenantId, userId, userRole);

        // ---- Step 7: Empty fresh roles → deny, do NOT call issue service (H153 F-05) ----
        if (freshRoles == null || freshRoles.isEmpty()) {
            return Optional.empty();
        }

        // ---- Step 8: Construct command with fresh roles only (H153 F-07) ----
        GeihouAccessTokenIssueCommand command = new GeihouAccessTokenIssueCommand(
                userId,
                tenantId,
                userRole.code(),
                freshRoles,
                stepUpVerified);

        // ---- Step 9: Call existing pure issue service (H153 F-07) ----
        return accessTokenIssueService.issue(command);
    }
}
