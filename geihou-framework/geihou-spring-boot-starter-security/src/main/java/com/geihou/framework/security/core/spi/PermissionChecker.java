package com.geihou.framework.security.core.spi;

import com.geihou.framework.security.core.context.GeihouPrincipal;

/**
 * Checks whether a principal has a named permission.
 */
@FunctionalInterface
public interface PermissionChecker {

    boolean hasPermission(GeihouPrincipal principal, String permission);
}
