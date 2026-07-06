package com.geihou.framework.security.core.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.framework.security.core.context.GeihouSecurityContextHolder;
import com.geihou.framework.security.core.handler.GeihouSecurityErrorHandler;
import com.geihou.framework.security.core.spi.TokenValidationResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeihouSecurityContextFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final GeihouSecurityErrorHandler errorHandler = new GeihouSecurityErrorHandler(objectMapper);

    @AfterEach
    void tearDown() {
        GeihouSecurityContextHolder.clear();
    }

    @Test
    void shouldPassThroughWhenHeaderMissing() throws Exception {
        GeihouSecurityContextFilter filter = new GeihouSecurityContextFilter(
                token -> TokenValidationResult.invalid(GeihouAuthErrorCodes.INVALID_TOKEN, "bad"), errorHandler);
        HttpServletRequest request = requestWithAuthorization(null);
        ResponseCapture response = responseCapture();
        AtomicReference<GeihouPrincipal> duringChain = new AtomicReference<>();

        filter.doFilter(request, response.response(), (req, res) -> duringChain.set(GeihouSecurityContextHolder.get()));

        verify(response.response(), never()).setStatus(401);
        assertThat(duringChain.get()).isNull();
        assertThat(GeihouSecurityContextHolder.get()).isNull();
    }

    @Test
    void shouldPassThroughWhenHeaderIsNotBearer() throws Exception {
        GeihouSecurityContextFilter filter = new GeihouSecurityContextFilter(
                token -> TokenValidationResult.invalid(GeihouAuthErrorCodes.INVALID_TOKEN, "bad"), errorHandler);
        HttpServletRequest request = requestWithAuthorization("Basic abc");
        ResponseCapture response = responseCapture();
        AtomicReference<GeihouPrincipal> duringChain = new AtomicReference<>();

        filter.doFilter(request, response.response(), (req, res) -> duringChain.set(GeihouSecurityContextHolder.get()));

        verify(response.response(), never()).setStatus(401);
        assertThat(duringChain.get()).isNull();
        assertThat(GeihouSecurityContextHolder.get()).isNull();
    }

    @Test
    void shouldSetPrincipalForValidBearerTokenAndClearAfterwards() throws Exception {
        GeihouPrincipal principal = principal();
        GeihouSecurityContextFilter filter = new GeihouSecurityContextFilter(
                token -> TokenValidationResult.valid(principal), errorHandler);
        HttpServletRequest request = bearerRequest("valid-token");
        ResponseCapture response = responseCapture();
        AtomicReference<GeihouPrincipal> duringChain = new AtomicReference<>();

        filter.doFilter(request, response.response(), (req, res) -> duringChain.set(GeihouSecurityContextHolder.get()));

        verify(response.response(), never()).setStatus(401);
        assertThat(duringChain.get()).isEqualTo(principal);
        assertThat(GeihouSecurityContextHolder.get()).isNull();
    }

    @Test
    void shouldWriteUnauthorizedForInvalidBearerToken() throws Exception {
        GeihouSecurityContextFilter filter = new GeihouSecurityContextFilter(
                token -> TokenValidationResult.invalid(GeihouAuthErrorCodes.INVALID_TOKEN, "Token is invalid"),
                errorHandler);
        HttpServletRequest request = bearerRequest("bad-token");
        ResponseCapture response = responseCapture();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response.response(), chain);

        verify(chain, never()).doFilter(request, response.response());
        verify(response.response()).setStatus(401);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("code").asInt()).isEqualTo(GeihouAuthErrorCodes.INVALID_TOKEN);
        assertThat(GeihouSecurityContextHolder.get()).isNull();
    }

    @Test
    void shouldWriteUnauthorizedForBlankBearerToken() throws Exception {
        GeihouSecurityContextFilter filter = new GeihouSecurityContextFilter(
                token -> TokenValidationResult.valid(principal()), errorHandler);
        HttpServletRequest request = bearerRequest("   ");
        ResponseCapture response = responseCapture();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response.response(), chain);

        verify(chain, never()).doFilter(request, response.response());
        verify(response.response()).setStatus(401);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("code").asInt()).isEqualTo(GeihouAuthErrorCodes.UNAUTHORIZED);
    }

    @Test
    void shouldClearContextWhenChainThrows() throws Exception {
        GeihouPrincipal principal = principal();
        GeihouSecurityContextFilter filter = new GeihouSecurityContextFilter(
                token -> TokenValidationResult.valid(principal), errorHandler);
        HttpServletRequest request = bearerRequest("valid-token");
        ResponseCapture response = responseCapture();

        assertThatThrownBy(() -> filter.doFilter(request, response.response(), throwingChain()))
                .isInstanceOf(ServletException.class);
        assertThat(GeihouSecurityContextHolder.get()).isNull();
    }

    @Test
    void shouldExposeOrderAfterTenantFilterRange() {
        GeihouSecurityContextFilter filter = new GeihouSecurityContextFilter(
                token -> TokenValidationResult.valid(principal()), errorHandler);

        assertThat(filter.getOrder()).isEqualTo(GeihouSecurityContextFilter.ORDER);
    }

    private HttpServletRequest bearerRequest(String token) {
        return requestWithAuthorization(GeihouSecurityContextFilter.BEARER_PREFIX + token);
    }

    private HttpServletRequest requestWithAuthorization(String authorization) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(GeihouSecurityContextFilter.HEADER_AUTHORIZATION)).thenReturn(authorization);
        return request;
    }

    private ResponseCapture responseCapture() throws IOException {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));
        return new ResponseCapture(response, writer);
    }

    private FilterChain throwingChain() {
        return (request, response) -> {
            throw new ServletException("boom");
        };
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
