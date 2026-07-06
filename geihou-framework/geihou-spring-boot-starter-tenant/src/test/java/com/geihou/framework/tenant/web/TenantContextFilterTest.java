package com.geihou.framework.tenant.web;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantContextFilterTest {

    private TenantContextFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TenantContextFilter();
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldSetTenantIdFromValidHeader() throws Exception {
        HttpServletRequest request = mockRequestWithTenantId("123");
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            chainCalled.set(true);
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(123L);
        });

        assertThat(chainCalled).isTrue();
        verify(response, never()).sendError(anyInt(), anyString());
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void shouldPassThroughWhenHeaderMissing() throws Exception {
        HttpServletRequest request = mockRequestWithTenantId(null);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            chainCalled.set(true);
            assertThat(TenantContextHolder.getTenantId()).isNull();
        });

        assertThat(chainCalled).isTrue();
        verify(response, never()).sendError(anyInt(), anyString());
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void shouldPassThroughWhenHeaderBlank() throws Exception {
        HttpServletRequest request = mockRequestWithTenantId("   ");
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            chainCalled.set(true);
            assertThat(TenantContextHolder.getTenantId()).isNull();
        });

        assertThat(chainCalled).isTrue();
        verify(response, never()).sendError(anyInt(), anyString());
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void shouldReturnBadRequestWhenHeaderNonNumeric() throws Exception {
        HttpServletRequest request = mockRequestWithTenantId("abc");
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST, "tenant-id header must be numeric");
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void shouldClearContextWhenDownstreamThrows() {
        HttpServletRequest request = mockRequestWithTenantId("456");
        HttpServletResponse response = mock(HttpServletResponse.class);

        ServletException exception = assertThrows(ServletException.class, () ->
                filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                    assertThat(TenantContextHolder.getTenantId()).isEqualTo(456L);
                    throw new ServletException("downstream failure");
                }));

        assertThat(exception).hasMessage("downstream failure");
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void shouldNotLeakContextAcrossRepeatedRequests() throws Exception {
        HttpServletRequest firstRequest = mockRequestWithTenantId("789");
        HttpServletResponse firstResponse = mock(HttpServletResponse.class);

        filter.doFilter(firstRequest, firstResponse, (servletRequest, servletResponse) ->
                assertThat(TenantContextHolder.getTenantId()).isEqualTo(789L));

        HttpServletRequest secondRequest = mockRequestWithTenantId(null);
        HttpServletResponse secondResponse = mock(HttpServletResponse.class);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(secondRequest, secondResponse, (servletRequest, servletResponse) -> {
            chainCalled.set(true);
            assertThat(TenantContextHolder.getTenantId()).isNull();
        });

        assertThat(chainCalled).isTrue();
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    private HttpServletRequest mockRequestWithTenantId(String tenantId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(TenantContextFilter.HEADER_TENANT_ID)).thenReturn(tenantId);
        return request;
    }
}
