package com.geihou.framework.security.core.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geihou.framework.security.core.annotation.RequirePermission;
import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.framework.security.core.context.GeihouSecurityContextHolder;
import com.geihou.framework.security.core.handler.GeihouSecurityErrorHandler;
import com.geihou.framework.security.core.spi.PermissionChecker;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class RequirePermissionInterceptorTest {

    private final GeihouSecurityErrorHandler errorHandler = new GeihouSecurityErrorHandler(new ObjectMapper());

    @AfterEach
    void tearDown() {
        GeihouSecurityContextHolder.clear();
    }

    @Test
    void preHandleShouldAllowHandlersWithoutAnnotation() throws Exception {
        RequirePermissionInterceptor interceptor = new RequirePermissionInterceptor((principal, permission) -> false,
                errorHandler);

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void preHandleShouldDenyWhenPrincipalIsMissing() throws Exception {
        RequirePermissionInterceptor interceptor = new RequirePermissionInterceptor((principal, permission) -> true,
                errorHandler);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response, handler("guarded"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void preHandleShouldDenyWhenCheckerReturnsFalse() throws Exception {
        RequirePermissionInterceptor interceptor = new RequirePermissionInterceptor((principal, permission) -> false,
                errorHandler);
        GeihouSecurityContextHolder.set(principal(Set.of("STORE_STAFF")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response, handler("guarded"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void preHandleShouldDenyWhenCheckerThrows() throws Exception {
        PermissionChecker checker = (principal, permission) -> {
            throw new IllegalStateException("rbac unavailable");
        };
        RequirePermissionInterceptor interceptor = new RequirePermissionInterceptor(checker, errorHandler);
        GeihouSecurityContextHolder.set(principal(Set.of("OWNER")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response, handler("guarded"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void preHandleShouldAllowWhenCheckerReturnsTrue() throws Exception {
        RequirePermissionInterceptor interceptor = new RequirePermissionInterceptor(
                (principal, permission) -> "cart:staff-assisted".equals(permission), errorHandler);
        GeihouSecurityContextHolder.set(principal(Set.of("OWNER")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response, handler("guarded"));

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void preHandleShouldUseClassLevelAnnotation() throws Exception {
        RequirePermissionInterceptor interceptor = new RequirePermissionInterceptor(
                (principal, permission) -> "cart:staff-assisted".equals(permission), errorHandler);
        GeihouSecurityContextHolder.set(principal(Set.of("CASHIER")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response,
                classLevelHandler());

        assertThat(allowed).isTrue();
    }

    private static GeihouPrincipal principal(Set<String> roles) {
        return new GeihouPrincipal(1001L, "staff", 1L, roles, Set.of(), "token-1");
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = GuardedController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(new GuardedController(), method);
    }

    private static HandlerMethod classLevelHandler() throws NoSuchMethodException {
        Method method = ClassGuardedController.class.getDeclaredMethod("classGuarded");
        return new HandlerMethod(new ClassGuardedController(), method);
    }

    private static class GuardedController {
        @RequirePermission("cart:staff-assisted")
        void guarded() {
        }
    }

    @RequirePermission("cart:staff-assisted")
    private static class ClassGuardedController {
        void classGuarded() {
        }
    }
}
