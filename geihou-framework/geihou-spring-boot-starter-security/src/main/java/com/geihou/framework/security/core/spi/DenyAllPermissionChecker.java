package com.geihou.framework.security.core.spi;

import com.geihou.framework.security.core.context.GeihouPrincipal;

/**
 * Fail-closed default permission checker.
 */
public class DenyAllPermissionChecker implements PermissionChecker {

    @Override
    public boolean hasPermission(GeihouPrincipal principal, String permission) {
        return false;
    }
}
