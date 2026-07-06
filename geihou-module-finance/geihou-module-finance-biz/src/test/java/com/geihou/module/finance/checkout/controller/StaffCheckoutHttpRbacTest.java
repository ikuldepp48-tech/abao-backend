package com.geihou.module.finance.checkout.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.framework.security.core.context.GeihouSecurityContextHolder;
import com.geihou.framework.security.core.handler.GeihouSecurityErrorHandler;
import com.geihou.framework.security.core.interceptor.RequirePermissionInterceptor;
import com.geihou.framework.security.core.spi.PermissionChecker;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutPayReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutSessionVO;
import com.geihou.module.finance.checkout.controller.app.staff.StaffCheckoutController;
import com.geihou.module.finance.checkout.service.CheckoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level RBAC integration test for StaffCheckoutController (TASK-G1-04K).
 *
 * <p>Proves that {@code @RequirePermission("cart:staff-assisted")} is enforced
 * through the real {@link RequirePermissionInterceptor} on the MockMvc request
 * path, not only by reflection or direct controller calls.
 *
 * <p>Uses {@link MockMvcBuilders#standaloneSetup} with the interceptor added
 * explicitly, a mocked {@link CheckoutService}, and a test {@link PermissionChecker}
 * that delegates to the principal's permission set.
 */
class StaffCheckoutHttpRbacTest {

    private static final Long AUTHENTICATED_STAFF_ID = 8001L;
    private static final String PERMISSION = "cart:staff-assisted";

    private CheckoutService checkoutService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        checkoutService = mock(CheckoutService.class);
        StaffCheckoutController controller = new StaffCheckoutController(checkoutService);

        PermissionChecker checker = (principal, permission) ->
                principal.permissions().contains(permission);

        GeihouSecurityErrorHandler errorHandler =
                new GeihouSecurityErrorHandler(new ObjectMapper());
        RequirePermissionInterceptor interceptor =
                new RequirePermissionInterceptor(checker, errorHandler);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(interceptor)
                .build();
    }

    @AfterEach
    void tearDown() {
        GeihouSecurityContextHolder.clear();
    }

    // ==================================================================
    // POST /app-api/staff/checkout/initiate
    // ==================================================================

    // ------------------------------------------------------------------
    // Required Case 1: initiate without principal -> 403, service not called
    // ------------------------------------------------------------------

    @Test
    void initiate_withoutPrincipal_returns403_andServiceNotCalled() throws Exception {
        GeihouSecurityContextHolder.clear();

        String jsonBody = "{\"customerUserId\":7001,\"shopId\":1,\"channel\":\"DINE_IN\",\"idempotentKey\":\"idem-1\"}";

        mockMvc.perform(post("/app-api/staff/checkout/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isForbidden());

        verifyNoInteractions(checkoutService);
    }

    // ------------------------------------------------------------------
    // Required Case 2: initiate with principal but no permission -> 403
    // ------------------------------------------------------------------

    @Test
    void initiate_withPrincipalButNoPermission_returns403_andServiceNotCalled() throws Exception {
        setupPrincipalWithoutPermission();

        String jsonBody = "{\"customerUserId\":7001,\"shopId\":1,\"channel\":\"DINE_IN\",\"idempotentKey\":\"idem-2\"}";

        mockMvc.perform(post("/app-api/staff/checkout/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isForbidden());

        verifyNoInteractions(checkoutService);
    }

    // ------------------------------------------------------------------
    // Required Case 3: initiate with permission -> 200, service called
    //                  with authenticated staff id
    // ------------------------------------------------------------------

    @Test
    void initiate_withPermission_returns200_andServiceCalledWithAuthenticatedStaffId() throws Exception {
        setupPrincipalWithPermission();

        when(checkoutService.staffInitiateCheckout(eq(AUTHENTICATED_STAFF_ID), any()))
                .thenReturn(new CheckoutSessionVO());

        String jsonBody = "{\"customerUserId\":7001,\"shopId\":1,\"channel\":\"DINE_IN\",\"idempotentKey\":\"idem-3\"}";

        mockMvc.perform(post("/app-api/staff/checkout/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk());

        verify(checkoutService).staffInitiateCheckout(eq(AUTHENTICATED_STAFF_ID), any());
    }

    // ------------------------------------------------------------------
    // Required Case 4: initiate with ?staffUserId=9999 and permission ->
    //                  service receives authenticated staff id, never 9999
    // ------------------------------------------------------------------

    @Test
    void initiate_withStaffUserIdInQuery_serviceReceivesAuthenticatedStaffId() throws Exception {
        setupPrincipalWithPermission();

        when(checkoutService.staffInitiateCheckout(eq(AUTHENTICATED_STAFF_ID), any()))
                .thenReturn(new CheckoutSessionVO());

        String jsonBody = "{\"customerUserId\":7001,\"shopId\":1,\"channel\":\"DINE_IN\",\"idempotentKey\":\"idem-4\"}";

        mockMvc.perform(post("/app-api/staff/checkout/initiate")
                        .param("staffUserId", "9999") // malicious attempt — must be ignored
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk());

        // Service must be called with the authenticated staff id (8001), NOT 9999
        verify(checkoutService).staffInitiateCheckout(eq(AUTHENTICATED_STAFF_ID), any());
        // Service must NEVER be called with 9999 as the staff user id
        verify(checkoutService, never()).staffInitiateCheckout(eq(9999L), any());
    }

    // ------------------------------------------------------------------
    // Required Case 5: PermissionChecker throws -> 403, service not called
    // ------------------------------------------------------------------

    @Test
    void initiate_whenPermissionCheckerThrows_returns403_andServiceNotCalled() throws Exception {
        setupPrincipalWithPermission();

        // Rebuild MockMvc with a PermissionChecker that always throws
        PermissionChecker throwingChecker = (principal, permission) -> {
            throw new RuntimeException("Checker explosion");
        };
        GeihouSecurityErrorHandler errorHandler =
                new GeihouSecurityErrorHandler(new ObjectMapper());
        RequirePermissionInterceptor throwingInterceptor =
                new RequirePermissionInterceptor(throwingChecker, errorHandler);

        StaffCheckoutController controller = new StaffCheckoutController(checkoutService);
        MockMvc throwingMockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(throwingInterceptor)
                .build();

        String jsonBody = "{\"customerUserId\":7001,\"shopId\":1,\"channel\":\"DINE_IN\",\"idempotentKey\":\"idem-5\"}";

        throwingMockMvc.perform(post("/app-api/staff/checkout/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isForbidden());

        verifyNoInteractions(checkoutService);
    }

    // ==================================================================
    // GET /app-api/staff/checkout/{sessionToken}
    // ==================================================================

    // ------------------------------------------------------------------
    // Required Case 6: query without principal -> 403, service not called
    // ------------------------------------------------------------------

    @Test
    void query_withoutPrincipal_returns403_andServiceNotCalled() throws Exception {
        GeihouSecurityContextHolder.clear();

        mockMvc.perform(get("/app-api/staff/checkout/token-abc"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(checkoutService);
    }

    // ------------------------------------------------------------------
    // Required Case 7: query with principal but no permission -> 403
    // ------------------------------------------------------------------

    @Test
    void query_withPrincipalButNoPermission_returns403_andServiceNotCalled() throws Exception {
        setupPrincipalWithoutPermission();

        mockMvc.perform(get("/app-api/staff/checkout/token-abc"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(checkoutService);
    }

    // ------------------------------------------------------------------
    // Required Case 8: query with permission -> 200, service called
    //                  with authenticated staff id
    // ------------------------------------------------------------------

    @Test
    void query_withPermission_returns200_andServiceCalledWithAuthenticatedStaffId() throws Exception {
        setupPrincipalWithPermission();

        when(checkoutService.staffQuerySession(eq(AUTHENTICATED_STAFF_ID), anyString()))
                .thenReturn(new CheckoutSessionVO());

        mockMvc.perform(get("/app-api/staff/checkout/token-abc"))
                .andExpect(status().isOk());

        verify(checkoutService).staffQuerySession(eq(AUTHENTICATED_STAFF_ID), eq("token-abc"));
    }

    // ==================================================================
    // POST /app-api/staff/checkout/{sessionToken}/cancel
    // ==================================================================

    // ------------------------------------------------------------------
    // Required Case 9: cancel without principal -> 403, service not called
    // ------------------------------------------------------------------

    @Test
    void cancel_withoutPrincipal_returns403_andServiceNotCalled() throws Exception {
        GeihouSecurityContextHolder.clear();

        mockMvc.perform(post("/app-api/staff/checkout/token-abc/cancel"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(checkoutService);
    }

    // ------------------------------------------------------------------
    // Required Case 10: cancel with principal but no permission -> 403
    // ------------------------------------------------------------------

    @Test
    void cancel_withPrincipalButNoPermission_returns403_andServiceNotCalled() throws Exception {
        setupPrincipalWithoutPermission();

        mockMvc.perform(post("/app-api/staff/checkout/token-abc/cancel"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(checkoutService);
    }

    // ------------------------------------------------------------------
    // Required Case 11: cancel with permission -> 200, service called
    //                   with authenticated staff id
    // ------------------------------------------------------------------

    @Test
    void cancel_withPermission_returns200_andServiceCalledWithAuthenticatedStaffId() throws Exception {
        setupPrincipalWithPermission();

        when(checkoutService.staffCancelSession(eq(AUTHENTICATED_STAFF_ID), anyString()))
                .thenReturn(new CheckoutSessionVO());

        mockMvc.perform(post("/app-api/staff/checkout/token-abc/cancel"))
                .andExpect(status().isOk());

        verify(checkoutService).staffCancelSession(eq(AUTHENTICATED_STAFF_ID), eq("token-abc"));
    }

    // ------------------------------------------------------------------
    // Required Case 12: cancel with ?staffUserId=9999 and permission ->
    //                   service receives authenticated staff id, never 9999
    // ------------------------------------------------------------------

    @Test
    void cancel_withStaffUserIdInQuery_serviceReceivesAuthenticatedStaffId() throws Exception {
        setupPrincipalWithPermission();

        when(checkoutService.staffCancelSession(eq(AUTHENTICATED_STAFF_ID), anyString()))
                .thenReturn(new CheckoutSessionVO());

        mockMvc.perform(post("/app-api/staff/checkout/token-abc/cancel")
                        .param("staffUserId", "9999")) // malicious attempt — must be ignored
                .andExpect(status().isOk());

        // Service must be called with the authenticated staff id (8001), NOT 9999
        verify(checkoutService).staffCancelSession(eq(AUTHENTICATED_STAFF_ID), eq("token-abc"));
        // Service must NEVER be called with 9999 as the staff user id
        verify(checkoutService, never()).staffCancelSession(eq(9999L), anyString());
    }

    // ==================================================================
    // POST /app-api/staff/checkout/{sessionToken}/pay
    // ==================================================================

    // ------------------------------------------------------------------
    // Required Case 13: pay without principal -> 403, service not called
    // ------------------------------------------------------------------

    @Test
    void pay_withoutPrincipal_returns403_andServiceNotCalled() throws Exception {
        GeihouSecurityContextHolder.clear();

        String jsonBody = "{\"paymentMethod\":\"WECHAT_PAY\"}";

        mockMvc.perform(post("/app-api/staff/checkout/token-pay/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isForbidden());

        verifyNoInteractions(checkoutService);
    }

    // ------------------------------------------------------------------
    // Required Case 14: pay with principal but no permission -> 403
    // ------------------------------------------------------------------

    @Test
    void pay_withPrincipalButNoPermission_returns403_andServiceNotCalled() throws Exception {
        setupPrincipalWithoutPermission();

        String jsonBody = "{\"paymentMethod\":\"WECHAT_PAY\"}";

        mockMvc.perform(post("/app-api/staff/checkout/token-pay/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isForbidden());

        verifyNoInteractions(checkoutService);
    }

    // ------------------------------------------------------------------
    // Required Case 15: pay with permission -> 200, service called
    //                   with authenticated staff id
    // ------------------------------------------------------------------

    @Test
    void pay_withPermission_returns200_andServiceCalledWithAuthenticatedStaffId() throws Exception {
        setupPrincipalWithPermission();

        when(checkoutService.staffPaySession(eq(AUTHENTICATED_STAFF_ID), anyString(), any()))
                .thenReturn(new CheckoutSessionVO());

        String jsonBody = "{\"paymentMethod\":\"WECHAT_PAY\"}";

        mockMvc.perform(post("/app-api/staff/checkout/token-pay/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk());

        verify(checkoutService).staffPaySession(eq(AUTHENTICATED_STAFF_ID), eq("token-pay"), any());
    }

    // ------------------------------------------------------------------
    // Required Case 16: pay with ?staffUserId=9999 -> service receives
    //                   authenticated staff id, never 9999
    // ------------------------------------------------------------------

    @Test
    void pay_withStaffUserIdInQuery_serviceReceivesAuthenticatedStaffId() throws Exception {
        setupPrincipalWithPermission();

        when(checkoutService.staffPaySession(eq(AUTHENTICATED_STAFF_ID), anyString(), any()))
                .thenReturn(new CheckoutSessionVO());

        String jsonBody = "{\"paymentMethod\":\"WECHAT_PAY\"}";

        mockMvc.perform(post("/app-api/staff/checkout/token-pay/pay")
                        .param("staffUserId", "9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk());

        verify(checkoutService).staffPaySession(eq(AUTHENTICATED_STAFF_ID), eq("token-pay"), any());
        verify(checkoutService, never()).staffPaySession(eq(9999L), anyString(), any());
    }

    // ==================================================================
    // POST /app-api/staff/checkout/{sessionToken}/convert
    // ==================================================================

    // ------------------------------------------------------------------
    // Required Case 17: convert without principal -> 403, service not called
    // ------------------------------------------------------------------

    @Test
    void convert_withoutPrincipal_returns403_andServiceNotCalled() throws Exception {
        GeihouSecurityContextHolder.clear();

        mockMvc.perform(post("/app-api/staff/checkout/token-conv/convert"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(checkoutService);
    }

    // ------------------------------------------------------------------
    // Required Case 18: convert with principal but no permission -> 403
    // ------------------------------------------------------------------

    @Test
    void convert_withPrincipalButNoPermission_returns403_andServiceNotCalled() throws Exception {
        setupPrincipalWithoutPermission();

        mockMvc.perform(post("/app-api/staff/checkout/token-conv/convert"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(checkoutService);
    }

    // ------------------------------------------------------------------
    // Required Case 19: convert with permission -> 200, service called
    //                   with authenticated staff id
    // ------------------------------------------------------------------

    @Test
    void convert_withPermission_returns200_andServiceCalledWithAuthenticatedStaffId() throws Exception {
        setupPrincipalWithPermission();

        when(checkoutService.staffConvertToOrder(eq(AUTHENTICATED_STAFF_ID), anyString()))
                .thenReturn(new CheckoutSessionVO());

        mockMvc.perform(post("/app-api/staff/checkout/token-conv/convert"))
                .andExpect(status().isOk());

        verify(checkoutService).staffConvertToOrder(eq(AUTHENTICATED_STAFF_ID), eq("token-conv"));
    }

    // ------------------------------------------------------------------
    // Required Case 20: convert with ?staffUserId=9999 -> service receives
    //                   authenticated staff id, never 9999
    // ------------------------------------------------------------------

    @Test
    void convert_withStaffUserIdInQuery_serviceReceivesAuthenticatedStaffId() throws Exception {
        setupPrincipalWithPermission();

        when(checkoutService.staffConvertToOrder(eq(AUTHENTICATED_STAFF_ID), anyString()))
                .thenReturn(new CheckoutSessionVO());

        mockMvc.perform(post("/app-api/staff/checkout/token-conv/convert")
                        .param("staffUserId", "9999"))
                .andExpect(status().isOk());

        verify(checkoutService).staffConvertToOrder(eq(AUTHENTICATED_STAFF_ID), eq("token-conv"));
        verify(checkoutService, never()).staffConvertToOrder(eq(9999L), anyString());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void setupPrincipalWithPermission() {
        GeihouSecurityContextHolder.set(new GeihouPrincipal(
                AUTHENTICATED_STAFF_ID, "staff1", 1L,
                Set.of(), Set.of(PERMISSION), "token-rbac-checkout-1"));
    }

    private void setupPrincipalWithoutPermission() {
        GeihouSecurityContextHolder.set(new GeihouPrincipal(
                AUTHENTICATED_STAFF_ID, "staff1", 1L,
                Set.of(), Set.of(), "token-rbac-checkout-2"));
    }
}
