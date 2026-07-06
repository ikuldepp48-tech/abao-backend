package com.geihou.module.system.service.auth;

import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Narrow service-layer login caller that wires the H146 role-permission read
 * model to the existing pure {@link GeihouAccessTokenIssueService}.
 *
 * <p>H152: first code slice — only a service-layer caller, NOT a public
 * controller/API/gateway/frontend. Does not implement refresh, logout,
 * {@code /auth/me}, B-to-A, no-literal, consultant finance runtime
 * conjunction, or token revocation.
 *
 * <p>Semantics (frozen by H151):
 * <ol>
 *   <li>Accept already-authenticated subject inputs: {@code userId},
 *       {@code tenantId}, {@code userRole}, {@code stepUpVerified},
 *       {@code operator}.</li>
 *   <li>Verify {@code userRole.acceptsTenantId(tenantId)} (H151 step 2).</li>
 *   <li>Call H146 {@link GeihouAuthRolePermissionReadService#resolveActiveRoleCodes}
 *       to obtain the user's active RBAC role codes.</li>
 *   <li>If roles are empty, deny token issuance — do NOT call
 *       {@link GeihouAccessTokenIssueService#issue} (H151 F-02).</li>
 *   <li>If roles are non-empty, construct {@link GeihouAccessTokenIssueCommand}
 *       and call {@link GeihouAccessTokenIssueService#issue} (H151 F-05).</li>
 * </ol>
 *
 * <p>JWT permissions prohibition (H151 F-03): this service never passes
 * permissions to the issue command. The issue command only carries
 * {@code userId}, {@code tenantId}, {@code userRole}, {@code roles}, and
 * {@code stepUpVerified}. Permissions never enter JWT.
 */
public class GeihouAuthLoginTokenWiringService {

    private final GeihouAuthRolePermissionReadService rolePermissionReadService;
    private final GeihouAccessTokenIssueService accessTokenIssueService;

    public GeihouAuthLoginTokenWiringService(
            GeihouAuthRolePermissionReadService rolePermissionReadService,
            GeihouAccessTokenIssueService accessTokenIssueService) {
        this.rolePermissionReadService = Objects.requireNonNull(
                rolePermissionReadService, "rolePermissionReadService must not be null");
        this.accessTokenIssueService = Objects.requireNonNull(
                accessTokenIssueService, "accessTokenIssueService must not be null");
    }

    /**
     * Issue a login access token for an already-authenticated subject.
     *
     * <p>Roles are resolved from the H146 read model, not from caller input.
     * Empty roles deny token issuance before the issue service is called.
     *
     * @param userId         authenticated user id (must be positive)
     * @param tenantId       tenant id (must not be negative; 0 for platform scope)
     * @param userRole       canonical identity user role (must not be null)
     * @param stepUpVerified whether the subject completed step-up verification
     * @param operator       operator string for audit (must not be blank — fail closed)
     * @return {@link Optional#empty()} if denied (tenant scope mismatch, empty
     *         roles, or issue service failure); otherwise the issued token result
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public Optional<GeihouAccessTokenIssueResult> issueLoginToken(
            long userId,
            long tenantId,
            GeihouAccessTokenUserRole userRole,
            boolean stepUpVerified,
            String operator) {
        // ---- Parameter validation (fail closed) ----
        if (userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (tenantId < 0L) {
            throw new IllegalArgumentException("tenantId must not be negative");
        }
        if (userRole == null) {
            throw new IllegalArgumentException("userRole must not be null");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("operator must not be blank");
        }

        // ---- Step 2: tenant scope validation (H151) ----
        if (!userRole.acceptsTenantId(tenantId)) {
            return Optional.empty();
        }

        // ---- Step 3: resolve active role codes from H146 read model ----
        List<String> roles = rolePermissionReadService.resolveActiveRoleCodes(
                tenantId, userId, userRole);

        // ---- Step 4: empty roles → deny, do NOT call issue service (H151 F-02) ----
        if (roles == null || roles.isEmpty()) {
            return Optional.empty();
        }

        // ---- Step 5: non-empty roles → construct command and call issue service (H151 F-05) ----
        GeihouAccessTokenIssueCommand command = new GeihouAccessTokenIssueCommand(
                userId,
                tenantId,
                userRole.code(),
                roles,
                stepUpVerified);

        return accessTokenIssueService.issue(command);
    }
}
