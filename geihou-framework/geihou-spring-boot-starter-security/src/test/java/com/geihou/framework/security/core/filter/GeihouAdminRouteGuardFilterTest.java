package com.geihou.framework.security.core.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geihou.framework.security.config.GeihouSecurityProperties;
import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.framework.security.core.context.GeihouSecurityContextHolder;
import com.geihou.framework.security.core.handler.GeihouSecurityErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeihouAdminRouteGuardFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final GeihouSecurityErrorHandler errorHandler = new GeihouSecurityErrorHandler(objectMapper);

    @AfterEach
    void tearDown() {
        GeihouSecurityContextHolder.clear();
    }

    @Test
    void shouldRejectProtectedPathWithoutPrincipal() throws Exception {
        GeihouAdminRouteGuardFilter filter = new GeihouAdminRouteGuardFilter(
                new GeihouSecurityProperties(), errorHandler);
        HttpServletRequest request = request("/admin-api/infra/microservices");
        ResponseCapture response = responseCapture();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response.response(), chain);

        verify(chain, never()).doFilter(request, response.response());
        verify(response.response()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("code").asInt()).isEqualTo(GeihouAuthErrorCodes.UNAUTHORIZED);
        assertThat(body.get("msg").asText()).isEqualTo("Unauthorized");
    }

    @Test
    void shouldPassProtectedPathWithPrincipal() throws Exception {
        GeihouAdminRouteGuardFilter filter = new GeihouAdminRouteGuardFilter(
                new GeihouSecurityProperties(), errorHandler);
        HttpServletRequest request = request("/admin-api/infra/microservices");
        ResponseCapture response = responseCapture();
        AtomicBoolean called = new AtomicBoolean(false);
        GeihouSecurityContextHolder.set(principal());

        filter.doFilter(request, response.response(), (req, res) -> called.set(true));

        assertThat(called).isTrue();
        verify(response.response(), never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void shouldPassDefaultPermitPathWithoutPrincipal() throws Exception {
        GeihouAdminRouteGuardFilter filter = new GeihouAdminRouteGuardFilter(
                new GeihouSecurityProperties(), errorHandler);
        HttpServletRequest request = request("/admin-api/auth/login");
        ResponseCapture response = responseCapture();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(request, response.response(), (req, res) -> called.set(true));

        assertThat(called).isTrue();
        verify(response.response(), never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void shouldPassNonProtectedPathWithoutPrincipal() throws Exception {
        GeihouAdminRouteGuardFilter filter = new GeihouAdminRouteGuardFilter(
                new GeihouSecurityProperties(), errorHandler);
        HttpServletRequest request = request("/public/ping");
        ResponseCapture response = responseCapture();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(request, response.response(), (req, res) -> called.set(true));

        assertThat(called).isTrue();
        verify(response.response(), never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void shouldNotTreatPartialPrefixAsProtected() throws Exception {
        GeihouAdminRouteGuardFilter filter = new GeihouAdminRouteGuardFilter(
                new GeihouSecurityProperties(), errorHandler);
        HttpServletRequest request = request("/admin-apix/infra/microservices");
        ResponseCapture response = responseCapture();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(request, response.response(), (req, res) -> called.set(true));

        assertThat(called).isTrue();
        verify(response.response(), never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void shouldRespectCustomProtectedAndPermitPatterns() throws Exception {
        GeihouSecurityProperties properties = new GeihouSecurityProperties();
        properties.setProtectedPathPatterns(List.of("/internal/**"));
        properties.setPermitPathPatterns(List.of("/internal/auth/**"));
        GeihouAdminRouteGuardFilter filter = new GeihouAdminRouteGuardFilter(properties, errorHandler);
        ResponseCapture protectedResponse = responseCapture();
        ResponseCapture permitResponse = responseCapture();
        AtomicBoolean permitCalled = new AtomicBoolean(false);

        filter.doFilter(request("/internal/secret"), protectedResponse.response(), mock(FilterChain.class));
        filter.doFilter(request("/internal/auth/login"), permitResponse.response(), (req, res) -> permitCalled.set(true));

        verify(protectedResponse.response()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(permitCalled).isTrue();
        verify(permitResponse.response(), never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void shouldReturnWithoutWritingWhenResponseAlreadyCommitted() throws Exception {
        GeihouAdminRouteGuardFilter filter = new GeihouAdminRouteGuardFilter(
                new GeihouSecurityProperties(), errorHandler);
        HttpServletRequest request = request("/admin-api/infra/microservices");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.isCommitted()).thenReturn(true);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response, never()).getWriter();
    }

    @Test
    void shouldPassThroughNonHttpRequests() throws Exception {
        GeihouAdminRouteGuardFilter filter = new GeihouAdminRouteGuardFilter(
                new GeihouSecurityProperties(), errorHandler);
        FilterChain chain = mock(FilterChain.class);
        jakarta.servlet.ServletRequest request = mock(jakarta.servlet.ServletRequest.class);
        jakarta.servlet.ServletResponse response = mock(jakarta.servlet.ServletResponse.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldExposeOrderImmediatelyAfterContextFilter() {
        GeihouAdminRouteGuardFilter filter = new GeihouAdminRouteGuardFilter(
                new GeihouSecurityProperties(), errorHandler);

        assertThat(filter.getOrder()).isEqualTo(GeihouSecurityContextFilter.ORDER + 1);
    }

    private HttpServletRequest request(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
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
