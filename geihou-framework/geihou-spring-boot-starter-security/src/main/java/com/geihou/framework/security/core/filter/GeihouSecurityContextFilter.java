package com.geihou.framework.security.core.filter;

import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.framework.security.core.context.GeihouSecurityContextHolder;
import com.geihou.framework.security.core.exception.GeihouAuthException;
import com.geihou.framework.security.core.handler.GeihouSecurityErrorHandler;
import com.geihou.framework.security.core.spi.TokenValidationResult;
import com.geihou.framework.security.core.spi.TokenValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.filter.OrderedFilter;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.util.Locale;

/**
 * Request filter that binds a validated Bearer principal to the current thread.
 */
public class GeihouSecurityContextFilter implements OrderedFilter {

    public static final String HEADER_AUTHORIZATION = "Authorization";

    public static final String BEARER_PREFIX = "Bearer ";

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    private final TokenValidator tokenValidator;

    private final GeihouSecurityErrorHandler errorHandler;

    public GeihouSecurityContextFilter(TokenValidator tokenValidator, GeihouSecurityErrorHandler errorHandler) {
        this.tokenValidator = tokenValidator;
        this.errorHandler = errorHandler;
    }

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

            String authorization = httpRequest.getHeader(HEADER_AUTHORIZATION);
            if (authorization == null || !authorization.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
                chain.doFilter(request, response);
                return;
            }

            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            if (token.isBlank()) {
                errorHandler.handle(httpResponse,
                        new GeihouAuthException(GeihouAuthErrorCodes.UNAUTHORIZED, "Missing bearer token"));
                return;
            }

            TokenValidationResult result = tokenValidator.validate(token);
            if (!result.valid()) {
                errorHandler.handle(httpResponse,
                        new GeihouAuthException(result.errorCode(), result.errorMessage()));
                return;
            }

            GeihouSecurityContextHolder.set(result.principal());
            chain.doFilter(request, response);
        } catch (GeihouAuthException ex) {
            if (response instanceof HttpServletResponse httpResponse) {
                errorHandler.handle(httpResponse, ex);
                return;
            }
            throw ex;
        } finally {
            GeihouSecurityContextHolder.clear();
        }
    }
}
