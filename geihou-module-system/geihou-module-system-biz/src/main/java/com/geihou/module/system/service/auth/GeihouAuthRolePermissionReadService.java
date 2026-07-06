package com.geihou.module.system.service.auth;

import com.geihou.module.system.dal.dataobject.auth.AuthPermissionDO;
import com.geihou.module.system.dal.dataobject.auth.AuthRoleDO;
import com.geihou.module.system.dal.dataobject.auth.AuthRolePermissionDO;
import com.geihou.module.system.dal.dataobject.auth.AuthUserRoleDO;
import com.geihou.module.system.dal.mysql.auth.AuthPermissionRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRolePermissionRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRoleRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRoleRepository;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only role/permission resolution service for the Geihou auth read model.
 *
 * <p>Permissions never enter JWT. This service provides static assignment resolution,
 * not a runtime authorization verdict.
 */
public class GeihouAuthRolePermissionReadService {

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

    private final AuthUserRoleRepository userRoleRepository;
    private final AuthRoleRepository roleRepository;
    private final AuthRolePermissionRepository rolePermissionRepository;
    private final AuthPermissionRepository permissionRepository;

    public GeihouAuthRolePermissionReadService(
            AuthUserRoleRepository userRoleRepository,
            AuthRoleRepository roleRepository,
            AuthRolePermissionRepository rolePermissionRepository,
            AuthPermissionRepository permissionRepository) {
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
    }

    public List<String> resolveActiveRoleCodes(Long tenantId, Long userId, GeihouAccessTokenUserRole userRole) {
        if (userRole == null || tenantId == null || userId == null || userId <= 0L) {
            return List.of();
        }
        if (!userRole.acceptsTenantId(tenantId)) {
            return List.of();
        }
        Set<String> allowedCodes = ROLE_FAMILY_ALLOWLIST.get(userRole);
        if (allowedCodes == null) {
            return List.of();
        }
        List<AuthRoleDO> activeRoles = resolveActiveRoles(tenantId, userId);
        return activeRoles.stream()
                .map(AuthRoleDO::getRoleCode)
                .filter(code -> code != null && !code.isBlank())
                .filter(allowedCodes::contains)
                .distinct()
                .sorted()
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        List::copyOf
                ));
    }

    public List<String> resolveAssignedPermissionCodes(Long tenantId, Long userId,
                                                        GeihouAccessTokenUserRole userRole) {
        if (userRole == null || tenantId == null || userId == null || userId <= 0L) {
            return List.of();
        }
        if (!userRole.acceptsTenantId(tenantId)) {
            return List.of();
        }
        Set<String> allowedCodes = ROLE_FAMILY_ALLOWLIST.get(userRole);
        if (allowedCodes == null) {
            return List.of();
        }
        List<AuthRoleDO> activeRoles = resolveActiveRoles(tenantId, userId);
        List<Long> allowedRoleIds = activeRoles.stream()
                .filter(role -> role.getRoleCode() != null && !role.getRoleCode().isBlank())
                .filter(role -> allowedCodes.contains(role.getRoleCode()))
                .map(AuthRoleDO::getId)
                .collect(Collectors.toList());
        if (allowedRoleIds.isEmpty()) {
            return List.of();
        }
        List<AuthRolePermissionDO> rolePermissions = rolePermissionRepository
                .selectActiveByTenantIdAndRoleIds(tenantId, allowedRoleIds);
        if (rolePermissions.isEmpty()) {
            return List.of();
        }
        List<Long> permissionIds = rolePermissions.stream()
                .map(AuthRolePermissionDO::getPermissionId)
                .distinct()
                .collect(Collectors.toList());
        List<AuthPermissionDO> permissions = permissionRepository.selectActiveByIds(permissionIds);
        return permissions.stream()
                .map(AuthPermissionDO::getPermissionCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        List::copyOf
                ));
    }

    private List<AuthRoleDO> resolveActiveRoles(Long tenantId, Long userId) {
        List<AuthUserRoleDO> userRoles = userRoleRepository.selectActiveByTenantIdAndUserId(tenantId, userId);
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = userRoles.stream()
                .map(AuthUserRoleDO::getRoleId)
                .distinct()
                .collect(Collectors.toList());
        return roleRepository.selectActiveByTenantIdAndIds(tenantId, roleIds);
    }
}
