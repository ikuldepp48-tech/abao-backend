package com.geihou.module.system.service.auth;

import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.framework.security.core.spi.PermissionChecker;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import java.util.List;
import java.util.Objects;

/**
 * RBAC-backed permission checker for Geihou runtime authorization.
 */
public class GeihouRbacPermissionChecker implements PermissionChecker {

    private final GeihouAuthRolePermissionReadService rolePermissionReadService;

    public GeihouRbacPermissionChecker(GeihouAuthRolePermissionReadService rolePermissionReadService) {
        this.rolePermissionReadService = Objects.requireNonNull(rolePermissionReadService,
                "rolePermissionReadService must not be null");
    }

    @Override
    public boolean hasPermission(GeihouPrincipal principal, String permission) {
        if (principal == null || permission == null || permission.isBlank()) {
            return false;
        }
        if (principal.userId() == null || principal.tenantId() == null) {
            return false;
        }
        if (principal.tokenScopes() == null || principal.tokenScopes().isEmpty()) {
            return false;
        }

        for (String tokenScope : principal.tokenScopes()) {
            if (tokenScope == null || tokenScope.isBlank()) {
                continue;
            }
            GeihouAccessTokenUserRole userRole = GeihouAccessTokenUserRole.fromCode(tokenScope).orElse(null);
            if (userRole == null) {
                continue;
            }
            try {
                List<String> permissionCodes = rolePermissionReadService.resolveAssignedPermissionCodes(
                        principal.tenantId(), principal.userId(), userRole);
                if (permissionCodes.contains(permission)) {
                    return true;
                }
            } catch (RuntimeException ex) {
                return false;
            }
        }
        return false;
    }
}
