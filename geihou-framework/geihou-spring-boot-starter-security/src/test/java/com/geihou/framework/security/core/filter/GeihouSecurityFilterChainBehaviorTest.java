package com.geihou.framework.security.core.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geihou.framework.security.config.GeihouSecurityProperties;
import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.framework.security.core.context.GeihouSecurityContextHolder;
import com.geihou.framework.security.core.handler.GeihouSecurityErrorHandler;
import com.geihou.framework.security.core.spi.TokenValidationResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeihouSecurityFilterChainBehaviorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final GeihouSecurityErrorHandler errorHandler = new GeihouSecurityErrorHandler(objectMapper);

    @AfterEach
    void tearDown() {
        GeihouSecurityContextHolder.clear();
    }

    @Test
    void shouldRejectMissingAuthorizationOnProtectedPathAfterContextFilterPassThrough() throws Exception {
        GeihouSecurityContextFilter contextFilter = new GeihouSecurityContextFilter(
                token -> TokenValidationResult.invalid(GeihouAuthErrorCodes.INVALID_TOKEN, "Token is invalid"),
                errorHandler);
        GeihouAdminRouteGuardFilter routeGuard = routeGuard();
        HttpServletRequest request = request("/admin-api/infra/microservices", null);
        ResponseCapture response = responseCapture();
        FilterChain finalChain = mock(FilterChain.class);

        contextFilter.doFilter(request, response.response(),
                (req, res) -> routeGuard.doFilter(req, res, finalChain));

        verify(finalChain, never()).doFilter(request, response.response());
        verify(response.response()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("code").asInt()).isEqualTo(GeihouAuthErrorCodes.UNAUTHORIZED);
    }

    @Test
    void shouldRejectNonBearerAuthorizationOnProtectedPathAfterContextFilterPassThrough() throws Exception {
        GeihouSecurityContextFilter contextFilter = new GeihouSecurityContextFilter(
                token -> TokenValidationResult.invalid(GeihouAuthErrorCodes.INVALID_TOKEN, "Token is invalid"),
                errorHandler);
        GeihouAdminRouteGuardFilter routeGuard = routeGuard();
        HttpServletRequest request = request("/admin-api/infra/microservices", "Basic abc");
        ResponseCapture response = responseCapture();
        FilterChain finalChain = mock(FilterChain.class);

        contextFilter.doFilter(request, response.response(),
                (req, res) -> routeGuard.doFilter(req, res, finalChain));

        verify(finalChain, never()).doFilter(request, response.response());
        verify(response.response()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("code").asInt()).isEqualTo(GeihouAuthErrorCodes.UNAUTHORIZED);
    }

    @Test
    void shouldLetContextFilterHandleInvalidBearerBeforeRouteGuard() throws Exception {
        GeihouSecurityContextFilter contextFilter = new GeihouSecurityContextFilter(
                token -> TokenValidationResult.invalid(GeihouAuthErrorCodes.INVALID_TOKEN, "Token is invalid"),
                errorHandler);
        GeihouAdminRouteGuardFilter routeGuard = routeGuard();
        HttpServletRequest request = request("/admin-api/infra/microservices", "Bearer bad-token");
        ResponseCapture response = responseCapture();
        FilterChain finalChain = mock(FilterChain.class);

        contextFilter.doFilter(request, response.response(),
                (req, res) -> routeGuard.doFilter(req, res, finalChain));

        verify(finalChain, never()).doFilter(request, response.response());
        verify(response.response()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("code").asInt()).isEqualTo(GeihouAuthErrorCodes.INVALID_TOKEN);
    }

    @Test
    void shouldPassProtectedPathWhenContextFilterBindsPrincipal() throws Exception {
        GeihouPrincipal principal = principal();
        GeihouSecurityContextFilter contextFilter = new GeihouSecurityContextFilter(
                token -> TokenValidationResult.valid(principal), errorHandler);
        GeihouAdminRouteGuardFilter routeGuard = routeGuard();
        HttpServletRequest request = request("/admin-api/infra/microservices", "Bearer valid-token");
        ResponseCapture response = responseCapture();
        AtomicBoolean finalCalled = new AtomicBoolean(false);

        contextFilter.doFilter(request, response.response(),
                (req, res) -> routeGuard.doFilter(req, res, (innerReq, innerRes) -> finalCalled.set(true)));

        assertThat(finalCalled).isTrue();
        verify(response.response(), never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(GeihouSecurityContextHolder.get()).isNull();
    }

    private GeihouAdminRouteGuardFilter routeGuard() {
        return new GeihouAdminRouteGuardFilter(new GeihouSecurityProperties(), errorHandler);
    }

    private HttpServletRequest request(String uri, String authorization) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getHeader(GeihouSecurityContextFilter.HEADER_AUTHORIZATION)).thenReturn(authorization);
        return request;
    }

    private ResponseCapture responseCapture() throws IOException {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));
        return new ResponseCapture(response, writer);
    }

    private GeihouPrincipal principal() {
        return new GeihouPrincipal(1L, "admin", 10L, Set.of("read"), Set.of("microservice:read"), "token-1");
    }

    private record ResponseCapture(HttpServletResponse response, StringWriter writer) {

        String body() {
            return writer.toString();
        }
    }
}
