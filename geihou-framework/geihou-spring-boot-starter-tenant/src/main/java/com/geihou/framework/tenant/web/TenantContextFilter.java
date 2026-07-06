package com.geihou.framework.tenant.web;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.filter.OrderedFilter;
import org.springframework.core.Ordered;

import java.io.IOException;

/**
 * Request filter that binds the tenant ID header to the current thread.
 */
public class TenantContextFilter implements OrderedFilter {

    public static final String HEADER_TENANT_ID = "tenant-id";

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 1;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (!(request instanceof HttpServletRequest httpRequest)
                    || !(response instanceof HttpServletResponse httpResponse)) {
                chain.doFilter(request, response);
                return;
            }

            String headerValue = httpRequest.getHeader(HEADER_TENANT_ID);
            if (headerValue == null || headerValue.isBlank()) {
                chain.doFilter(request, response);
                return;
            }

            Long tenantId;
            try {
                tenantId = Long.valueOf(headerValue.trim());
            } catch (NumberFormatException ex) {
                httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "tenant-id header must be numeric");
                return;
            }

            TenantContextHolder.setTenantId(tenantId);
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
