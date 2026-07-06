package com.geihou.framework.security.core.interceptor;

import com.geihou.framework.security.core.annotation.RequirePermission;
import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.framework.security.core.context.GeihouSecurityContextHolder;
import com.geihou.framework.security.core.handler.GeihouSecurityErrorHandler;
import com.geihou.framework.security.core.spi.PermissionChecker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Method-level permission guard for Geihou controller handlers.
 */
public class RequirePermissionInterceptor implements HandlerInterceptor {

    private final PermissionChecker permissionChecker;
    private final GeihouSecurityErrorHandler errorHandler;

    public RequirePermissionInterceptor(PermissionChecker permissionChecker,
                                        GeihouSecurityErrorHandler errorHandler) {
        this.permissionChecker = Objects.requireNonNull(permissionChecker, "permissionChecker must not be null");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler must not be null");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        RequirePermission annotation = findAnnotation(handler);
        if (annotation == null) {
            return true;
        }

        GeihouPrincipal principal = GeihouSecurityContextHolder.get();
        if (principal == null) {
            deny(response);
            return false;
        }

        boolean allowed;
        try {
            allowed = permissionChecker.hasPermission(principal, annotation.value());
        } catch (RuntimeException ex) {
            allowed = false;
        }
        if (!allowed) {
            deny(response);
            return false;
        }
        return true;
    }

    private static RequirePermission findAnnotation(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return null;
        }
        RequirePermission methodAnnotation =
                AnnotationUtils.findAnnotation(handlerMethod.getMethod(), RequirePermission.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        return AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), RequirePermission.class);
    }

    private void deny(HttpServletResponse response) throws IOException {
        if (!response.isCommitted()) {
            errorHandler.handle(response, GeihouAuthErrorCodes.FORBIDDEN, "Forbidden");
        }
    }
}
