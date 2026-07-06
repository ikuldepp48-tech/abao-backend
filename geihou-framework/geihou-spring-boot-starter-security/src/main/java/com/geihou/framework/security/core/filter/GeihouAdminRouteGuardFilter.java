package com.geihou.framework.security.core.filter;

import com.geihou.framework.security.config.GeihouSecurityProperties;
import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.framework.security.core.context.GeihouSecurityContextHolder;
import com.geihou.framework.security.core.handler.GeihouSecurityErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.filter.OrderedFilter;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;

/**
 * Authentication-only guard for protected Geihou admin routes.
 */
public class GeihouAdminRouteGuardFilter implements OrderedFilter {

    public static final int ORDER = GeihouSecurityContextFilter.ORDER + 1;

    private final GeihouSecurityProperties properties;

    private final GeihouSecurityErrorHandler errorHandler;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GeihouAdminRouteGuardFilter(GeihouSecurityProperties properties,
                                       GeihouSecurityErrorHandler errorHandler) {
        this.properties = properties;
        this.errorHandler = errorHandler;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String requestUri = httpRequest.getRequestURI();
        if (!isProtected(requestUri == null ? "" : requestUri)) {
            chain.doFilter(request, response);
            return;
        }

        if (GeihouSecurityContextHolder.get() != null) {
            chain.doFilter(request, response);
            return;
        }

        if (!httpResponse.isCommitted()) {
            errorHandler.handle(httpResponse, GeihouAuthErrorCodes.UNAUTHORIZED, "Unauthorized");
        }
    }

    private boolean isProtected(String requestUri) {
        if (matchesAny(properties.getPermitPathPatterns(), requestUri)) {
            return false;
        }
        return matchesAny(properties.getProtectedPathPatterns(), requestUri);
    }

    private boolean matchesAny(Iterable<String> patterns, String requestUri) {
        for (String pattern : patterns) {
            if (pattern != null && pathMatcher.match(pattern, requestUri)) {
                return true;
            }
        }
        return false;
    }
}
