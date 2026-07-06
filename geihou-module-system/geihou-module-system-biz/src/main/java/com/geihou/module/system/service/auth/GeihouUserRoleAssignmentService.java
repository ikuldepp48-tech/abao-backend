package com.geihou.module.system.service.auth;

import com.geihou.module.system.dal.dataobject.auth.AuthRoleDO;
import com.geihou.module.system.dal.dataobject.auth.AuthUserRoleDO;
import com.geihou.module.system.dal.mysql.auth.AuthRoleRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRoleWriteRepository;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * User-role assignment write service for the Geihou auth model.
 *
 * <p>H150: narrow code slice — only {@code auth_user_role} additive + idempotent
 * insert. Does not implement login/token wiring, {@code /auth/me},
 * controller/API/gateway/frontend, B-to-A, no-literal, consultant finance
 * runtime conjunction, or token revocation.
 *
 * <p>Assignment may only use tenant-owned {@code auth_role.id} (tenant_id &gt; 0).
 * Tenant_id=0 template role ids are forbidden. Cross-tenant role ids are
 * forbidden.
 *
 * <p>Idempotent: if an active {@code auth_user_role} row already exists for
 * the given {@code (tenantId, userId, roleId)} triple, the call returns
 * {@code skipped} (no-op success). No update, no delete, no soft-delete.
 */
public class GeihouUserRoleAssignmentService {

    /**
     * Identity-to-RBAC allowlist (H146 RECOVERY DESIGN DECISION based on H139).
     *
     * <p>Duplicated here because H146 {@link GeihouAuthRolePermissionReadService}
     * is read-only and cannot be modified by H150. The allowlist is a frozen
     * constant; any future change requires a boundary review.
     */
    private static final Map<GeihouAccessTokenUserRole, Set<String>> ROLE_FAMILY_ALLOWLIST = Map.of(
            GeihouAccessTokenUserRole.CUSTOMER, Set.of("CUSTOMER"),
            GeihouAccessTokenUserRole.STORE_STAFF, Set.of("CASHIER", "WAITER", "KITCHEN_COOK"),
            GeihouAccessTokenUserRole.CK_WORKER, Set.of("CK_WORKER"),
            GeihouAccessTokenUserRole.STORE_MANAGER, Set.of("SHOP_MANAGER"),
            GeihouAccessTokenUserRole.CK_MANAGER, Set.of("CK_MANAGER"),
            GeihouAccessTokenUserRole.OWNER, Set.of("OWNER"),
            GeihouAccessTokenUserRole.CONSULTANT, Set.of("CONSULTANT"),
            GeihouAccessTokenUserRole.PLATFORM_ADMIN, Set.of("PLATFORM_OPERATOR", "PLATFORM_DEVELOPER")
    );

    private final AuthRoleRepository roleRepository;
    private final AuthUserRoleWriteRepository userRoleWriteRepository;

    /**
     * Immutable result of an assignment attempt.
     *
     * @param outcome  {@code "inserted"} if a new row was created,
     *                 {@code "skipped"} if an active row already existed
     * @param tenantId the tenant id
     * @param userId   the user id
     * @param roleId   the tenant-owned role id
     * @param roleCode the resolved role code
     */
    public record AssignmentResult(
            String outcome,
            Long tenantId,
            Long userId,
            Long roleId,
            String roleCode) {}

    public GeihouUserRoleAssignmentService(
            AuthRoleRepository roleRepository,
            AuthUserRoleWriteRepository userRoleWriteRepository) {
        this.roleRepository = roleRepository;
        this.userRoleWriteRepository = userRoleWriteRepository;
    }

    /**
     * Assign a tenant-owned role to a user.
     *
     * <p>Validates all parameters, checks the role exists / is ACTIVE /
     * belongs to the requesting tenant / is in the H146 identity-to-RBAC
     * allowlist for the given {@code userRole}, then performs an additive +
     * idempotent insert into {@code auth_user_role}.
     *
     * @param tenantId target tenant id (must be &gt; 0)
     * @param userId   target user id (must be &gt; 0)
     * @param roleId   target role id (must be &gt; 0, must be tenant-owned)
     * @param userRole identity user role for allowlist validation (must not be null)
     * @param operator operator string for audit (must not be blank — fail closed)
     * @return immutable {@link AssignmentResult}
     * @throws IllegalArgumentException if any parameter is invalid
     * @throws IllegalStateException    if the role does not exist, has wrong
     *                                  tenant_id, is not ACTIVE, is deleted,
     *                                  or its role_code is not in the allowlist
     */
    public AssignmentResult assignRole(Long tenantId, Long userId, Long roleId,
                                       GeihouAccessTokenUserRole userRole,
                                       String operator) {
        // ---- Parameter validation (fail closed) ----
        if (tenantId == null || tenantId <= 0L) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        if (userId == null || userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (roleId == null || roleId <= 0L) {
            throw new IllegalArgumentException("roleId must be positive");
        }
        if (userRole == null) {
            throw new IllegalArgumentException("userRole must not be null");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("operator must not be blank");
        }

        // ---- Tenant scope validation ----
        if (!userRole.acceptsTenantId(tenantId)) {
            throw new IllegalStateException(
                    "userRole " + userRole + " does not accept tenantId " + tenantId);
        }

        // ---- Allowlist lookup ----
        Set<String> allowedCodes = ROLE_FAMILY_ALLOWLIST.get(userRole);
        if (allowedCodes == null) {
            throw new IllegalStateException(
                    "No allowlist for userRole " + userRole);
        }

        // ---- Role existence / tenant / status / deleted validation ----
        // AuthRoleRepository.selectActiveByTenantIdAndIds already filters:
        //   tenant_id = tenantId, deleted = false, status = 'ACTIVE'
        List<AuthRoleDO> roles = roleRepository.selectActiveByTenantIdAndIds(
                tenantId, Set.of(roleId));
        if (roles.isEmpty()) {
            throw new IllegalStateException(
                    "Role not found or not ACTIVE for tenantId=" + tenantId
                            + " roleId=" + roleId);
        }
        AuthRoleDO role = roles.get(0);

        // Defensive: verify role tenant_id matches (should be guaranteed by query)
        if (!Objects.equals(role.getTenantId(), tenantId)) {
            throw new IllegalStateException(
                    "Role tenant_id mismatch: expected " + tenantId
                            + " got " + role.getTenantId());
        }

        // ---- Role code allowlist validation ----
        String roleCode = role.getRoleCode();
        if (roleCode == null || roleCode.isBlank()) {
            throw new IllegalStateException(
                    "Role has null/blank role_code for roleId=" + roleId);
        }
        if (!allowedCodes.contains(roleCode)) {
            throw new IllegalStateException(
                    "Role code " + roleCode + " is not allowed for userRole "
                            + userRole + " (allowlist: " + allowedCodes + ")");
        }

        // ---- Idempotency check ----
        if (userRoleWriteRepository.existsActive(tenantId, userId, roleId)) {
            return new AssignmentResult("skipped", tenantId, userId, roleId, roleCode);
        }

        // ---- Additive insert ----
        AuthUserRoleDO entity = new AuthUserRoleDO();
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setRoleId(roleId);
        entity.setCreator(operator);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdater(operator);
        entity.setUpdateTime(LocalDateTime.now());
        entity.setDeleted(false);
        userRoleWriteRepository.insert(entity);

        return new AssignmentResult("inserted", tenantId, userId, roleId, roleCode);
    }
}
