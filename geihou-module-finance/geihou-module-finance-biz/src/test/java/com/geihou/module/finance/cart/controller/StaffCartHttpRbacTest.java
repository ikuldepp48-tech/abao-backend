package com.geihou.module.finance.cart.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.framework.security.core.context.GeihouSecurityContextHolder;
import com.geihou.framework.security.core.handler.GeihouSecurityErrorHandler;
import com.geihou.framework.security.core.interceptor.RequirePermissionInterceptor;
import com.geihou.framework.security.core.spi.PermissionChecker;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartVO;
import com.geihou.module.finance.cart.controller.app.staff.StaffCartController;
import com.geihou.module.finance.cart.service.CartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level RBAC integration test for StaffCartController (TASK-G1-04H).
 *
 * <p>Proves that {@code @RequirePermission("cart:staff-assisted")} is enforced
 * through the real {@link RequirePermissionInterceptor} on the MockMvc request
 * path, not only by reflection.
 *
 * <p>Uses {@link MockMvcBuilders#standaloneSetup} with the interceptor added
 * explicitly, a mocked {@link CartService}, and a test {@link PermissionChecker}
 * that delegates to the principal's permission set.
 */
class StaffCartHttpRbacTest {

    private static final Long AUTHENTICATED_STAFF_ID = 8001L;
    private static final String PERMISSION = "cart:staff-assisted";

    private CartService cartService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cartService = mock(CartService.class);
        StaffCartController controller = new StaffCartController(cartService);

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

    // ------------------------------------------------------------------
    // Required Case 1: GET /current without principal → 403, service not called
    // ------------------------------------------------------------------

    @Test
    void getCurrentCart_withoutPrincipal_returns403_andServiceNotCalled() throws Exception {
        GeihouSecurityContextHolder.clear();

        mockMvc.perform(get("/app-api/staff/cart/current")
                        .param("customerUserId", "7001")
                        .header("shop-id", "1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }

    // ------------------------------------------------------------------
    // Required Case 2: GET /current with principal but permission false → 403
    // ------------------------------------------------------------------

    @Test
    void getCurrentCart_withPrincipalButNoPermission_returns403_andServiceNotCalled() throws Exception {
        setupPrincipalWithoutPermission();

        mockMvc.perform(get("/app-api/staff/cart/current")
                        .param("customerUserId", "7001")
                        .header("shop-id", "1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }

    // ------------------------------------------------------------------
    // Required Case 3: GET /current with permission true → 200,
    //                  service called with authenticated staff id
    // ------------------------------------------------------------------

    @Test
    void getCurrentCart_withPermission_returns200_andServiceCalledWithAuthenticatedStaffId() throws Exception {
        setupPrincipalWithPermission();

        when(cartService.staffGetCurrentCart(
                eq(AUTHENTICATED_STAFF_ID), eq(7001L), eq(1L), eq("DINE_IN")))
                .thenReturn(new CartVO());

        mockMvc.perform(get("/app-api/staff/cart/current")
                        .param("customerUserId", "7001")
                        .header("shop-id", "1"))
                .andExpect(status().isOk());

        verify(cartService).staffGetCurrentCart(
                AUTHENTICATED_STAFF_ID, 7001L, 1L, "DINE_IN");
    }

    // ------------------------------------------------------------------
    // Required Case 4: POST /items with permission true and
    //                  HTTP staffUserId=9999 → service receives
    //                  authenticated staff id, not 9999
    // ------------------------------------------------------------------

    @Test
    void addItem_withStaffUserIdInQuery_serviceReceivesAuthenticatedStaffId() throws Exception {
        setupPrincipalWithPermission();

        when(cartService.staffAddItem(
                eq(AUTHENTICATED_STAFF_ID), anyLong(), anyLong(), anyString(), any()))
                .thenReturn(new CartVO());

        String jsonBody = "{\"skuId\":1001,\"quantity\":2}";

        mockMvc.perform(post("/app-api/staff/cart/items")
                        .param("customerUserId", "7001")
                        .param("staffUserId", "9999") // malicious attempt — must be ignored
                        .header("shop-id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk());

        // Service must be called with the authenticated staff id (8001), NOT 9999
        verify(cartService).staffAddItem(
                eq(AUTHENTICATED_STAFF_ID), eq(7001L), eq(1L), eq("DINE_IN"), any());
        // Service must NEVER be called with 9999 as the staff user id
        verify(cartService, never()).staffAddItem(
                eq(9999L), anyLong(), anyLong(), anyString(), any());
    }

    // ------------------------------------------------------------------
    // Required Case 5: PermissionChecker throws RuntimeException → 403,
    //                  service not called
    // ------------------------------------------------------------------

    @Test
    void getCurrentCart_whenPermissionCheckerThrows_returns403_andServiceNotCalled() throws Exception {
        setupPrincipalWithPermission();

        // Rebuild MockMvc with a PermissionChecker that always throws
        PermissionChecker throwingChecker = (principal, permission) -> {
            throw new RuntimeException("Checker explosion");
        };
        GeihouSecurityErrorHandler errorHandler =
                new GeihouSecurityErrorHandler(new ObjectMapper());
        RequirePermissionInterceptor throwingInterceptor =
                new RequirePermissionInterceptor(throwingChecker, errorHandler);

        StaffCartController controller = new StaffCartController(cartService);
        MockMvc throwingMockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(throwingInterceptor)
                .build();

        throwingMockMvc.perform(get("/app-api/staff/cart/current")
                        .param("customerUserId", "7001")
                        .header("shop-id", "1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }

    // ------------------------------------------------------------------
    // Required Case 6a: PUT quantity with permission false → 403
    // ------------------------------------------------------------------

    @Test
    void updateQuantity_withoutPermission_returns403_andServiceNotCalled() throws Exception {
        setupPrincipalWithoutPermission();

        mockMvc.perform(put("/app-api/staff/cart/items/500/quantity")
                        .param("customerUserId", "7001")
                        .header("shop-id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }

    // ------------------------------------------------------------------
    // Required Case 6b: DELETE item with permission false → 403
    // ------------------------------------------------------------------

    @Test
    void removeItem_withoutPermission_returns403_andServiceNotCalled() throws Exception {
        setupPrincipalWithoutPermission();

        mockMvc.perform(delete("/app-api/staff/cart/items/500")
                        .param("customerUserId", "7001")
                        .header("shop-id", "1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }

    // ------------------------------------------------------------------
    // Required Case 6c: DELETE clear with permission false → 403
    // ------------------------------------------------------------------

    @Test
    void clearCart_withoutPermission_returns403_andServiceNotCalled() throws Exception {
        setupPrincipalWithoutPermission();

        mockMvc.perform(delete("/app-api/staff/cart/clear")
                        .param("customerUserId", "7001")
                        .header("shop-id", "1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }

    // ------------------------------------------------------------------
    // Bonus: Verify all 5 endpoints succeed with permission and reach service
    // ------------------------------------------------------------------

    @Test
    void allEndpoints_withPermission_reachService() throws Exception {
        setupPrincipalWithPermission();

        when(cartService.staffGetCurrentCart(anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(new CartVO());
        when(cartService.staffAddItem(anyLong(), anyLong(), anyLong(), anyString(), any()))
                .thenReturn(new CartVO());
        when(cartService.staffUpdateQuantity(anyLong(), anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(new CartVO());
        when(cartService.staffRemoveItem(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(new CartVO());
        when(cartService.staffClearCart(anyLong(), anyLong(), anyLong()))
                .thenReturn(new CartVO());

        // GET /current
        mockMvc.perform(get("/app-api/staff/cart/current")
                        .param("customerUserId", "7001")
                        .header("shop-id", "1"))
                .andExpect(status().isOk());

        // POST /items
        mockMvc.perform(post("/app-api/staff/cart/items")
                        .param("customerUserId", "7001")
                        .header("shop-id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":1001,\"quantity\":1}"))
                .andExpect(status().isOk());

        // PUT /items/{itemId}/quantity
        mockMvc.perform(put("/app-api/staff/cart/items/500/quantity")
                        .param("customerUserId", "7001")
                        .header("shop-id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk());

        // DELETE /items/{itemId}
        mockMvc.perform(delete("/app-api/staff/cart/items/500")
                        .param("customerUserId", "7001")
                        .header("shop-id", "1"))
                .andExpect(status().isOk());

        // DELETE /clear
        mockMvc.perform(delete("/app-api/staff/cart/clear")
                        .param("customerUserId", "7001")
                        .header("shop-id", "1"))
                .andExpect(status().isOk());

        // Verify each service method was invoked with the authenticated staff id
        verify(cartService).staffGetCurrentCart(
                eq(AUTHENTICATED_STAFF_ID), anyLong(), anyLong(), anyString());
        verify(cartService).staffAddItem(
                eq(AUTHENTICATED_STAFF_ID), anyLong(), anyLong(), anyString(), any());
        verify(cartService).staffUpdateQuantity(
                eq(AUTHENTICATED_STAFF_ID), anyLong(), anyLong(), anyLong(), any());
        verify(cartService).staffRemoveItem(
                eq(AUTHENTICATED_STAFF_ID), anyLong(), anyLong(), anyLong());
        verify(cartService).staffClearCart(
                eq(AUTHENTICATED_STAFF_ID), anyLong(), anyLong());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void setupPrincipalWithPermission() {
        GeihouSecurityContextHolder.set(new GeihouPrincipal(
                AUTHENTICATED_STAFF_ID, "staff1", 1L,
                Set.of(), Set.of(PERMISSION), "token-rbac-1"));
    }

    private void setupPrincipalWithoutPermission() {
        GeihouSecurityContextHolder.set(new GeihouPrincipal(
                AUTHENTICATED_STAFF_ID, "staff1", 1L,
                Set.of(), Set.of(), "token-rbac-2"));
    }
}
