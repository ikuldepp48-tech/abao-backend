package com.geihou.module.finance.checkout.controller;

import com.geihou.common.error.ErrorCode;
import com.geihou.common.pojo.CommonResult;
import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.framework.security.core.context.GeihouSecurityContextHolder;
import com.geihou.framework.security.core.spi.PermissionChecker;
import com.geihou.module.finance.cart.framework.CartBusinessException;
import com.geihou.module.finance.cart.framework.CartErrorCodeConstants;
import com.geihou.module.finance.checkout.CheckoutTestConfig;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutSessionVO;
import com.geihou.module.finance.checkout.service.CheckoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP status code integration test for checkout /initiate endpoints (Slice 2C-2C-A2).
 *
 * <p>Proves the real HTTP status chain through Web Application Context (WAC):
 * <ul>
 *   <li>1004080 (CHECKOUT_STOCK_CLASSIFICATION_UNMAPPED) re-thrown by controller,
 *       caught by {@link com.geihou.module.finance.checkout.framework.CheckoutExceptionHandler},
 *       mapped to HTTP 409 Conflict.</li>
 *   <li>Non-1004080 CartBusinessException caught by controller, returned as HTTP 200 OK
 *       with body code/msg (preserves existing behavior).</li>
 *   <li>Success returns HTTP 200 OK with body code=0.</li>
 * </ul>
 *
 * <p>Covers both customer and staff /initiate endpoints. Staff cases set up
 * a {@link GeihouPrincipal} with {@code cart:staff-assisted} permission
 * (staffUserId=8001L) and mock {@code staffInitiateCheckout(eq(8001L), any())}.
 *
 * <p>Overrides the default {@code DenyAllPermissionChecker} from
 * {@link com.geihou.framework.security.config.GeihouSecurityAutoConfiguration}
 * with a test {@link PermissionChecker} that delegates to principal.permissions().
 *
 * <p>{@link CheckoutService} is replaced with a {@code @MockBean} so the real
 * service/DB/mapper layer is never invoked - this test asserts HTTP status
 * mapping only, not business logic.
 *
 * <p>G0-04H185 FIN-CONSISTENCY slice 2C-2C-A2.
 */
@SpringBootTest(
        classes = {CheckoutTestConfig.class, CheckoutHttpStatusTest.TestPermissionConfig.class},
        properties = {
                "spring.datasource.url=jdbc:h2:mem:checkout_http_status_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
@AutoConfigureMockMvc
class CheckoutHttpStatusTest {

    private static final Long AUTHENTICATED_STAFF_ID = 8001L;
    private static final String PERMISSION = "cart:staff-assisted";

    private static final String FROZEN_MSG_1004080 = "库存分类映射不完整，请修复后重新结算";

    private static final String CUSTOMER_INITIATE_URL = "/app-api/customer/checkout/initiate";
    private static final String STAFF_INITIATE_URL = "/app-api/staff/checkout/initiate";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CheckoutService checkoutService;

    /**
     * Test-only {@link PermissionChecker} that overrides the default
     * {@code DenyAllPermissionChecker} and delegates to principal.permissions().
     * Marked {@code @Primary} to ensure it wins over any auto-configured checker.
     */
    @TestConfiguration
    static class TestPermissionConfig {
        @Bean
        @Primary
        public PermissionChecker permissionChecker() {
            return (principal, permission) -> principal.permissions().contains(permission);
        }
    }

    @AfterEach
    void tearDown() {
        GeihouSecurityContextHolder.clear();
    }

    // ==================================================================
    // Customer /initiate - HTTP status chain
    // ==================================================================

    @Test
    void customerInitiate_whenStockClassificationUnmapped_returnsHttp409() throws Exception {
        // Uses production constant - proves re-throw on same-reference ErrorCode
        when(checkoutService.initiateCheckout(any()))
                .thenThrow(new CartBusinessException(
                        CartErrorCodeConstants.CHECKOUT_STOCK_CLASSIFICATION_UNMAPPED));

        mockMvc.perform(post(CUSTOMER_INITIATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody("cust-409")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1004080))
                .andExpect(jsonPath("$.msg").value(FROZEN_MSG_1004080));
    }

    @Test
    void customerInitiate_whenNon1004080Error_returnsHttp200() throws Exception {
        when(checkoutService.initiateCheckout(any()))
                .thenThrow(new CartBusinessException(CartErrorCodeConstants.CHECKOUT_DUPLICATE));

        mockMvc.perform(post(CUSTOMER_INITIATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody("cust-200")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004010))
                .andExpect(jsonPath("$.msg").value("Duplicate checkout"));
    }

    @Test
    void customerInitiate_whenSuccess_returnsHttp200() throws Exception {
        when(checkoutService.initiateCheckout(any()))
                .thenReturn(new CheckoutSessionVO());

        mockMvc.perform(post(CUSTOMER_INITIATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody("cust-success")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CommonResult.SUCCESS_CODE))
                .andExpect(jsonPath("$.msg").value("success"));
    }

    // ==================================================================
    // Staff /initiate - HTTP status chain (with @RequirePermission)
    // ==================================================================

    @Test
    void staffInitiate_whenStockClassificationUnmapped_returnsHttp409() throws Exception {
        setupStaffPrincipalWithPermission();
        // Uses independently constructed ErrorCode (same code 1004080, different Integer reference)
        // - proves controller re-throw maps by VALUE via .equals(), not by reference via ==
        when(checkoutService.staffInitiateCheckout(eq(AUTHENTICATED_STAFF_ID), any()))
                .thenThrow(new CartBusinessException(
                        new ErrorCode(1004080, FROZEN_MSG_1004080)));

        mockMvc.perform(post(STAFF_INITIATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody("staff-409")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1004080))
                .andExpect(jsonPath("$.msg").value(FROZEN_MSG_1004080));
    }

    @Test
    void staffInitiate_whenNon1004080Error_returnsHttp200() throws Exception {
        setupStaffPrincipalWithPermission();
        when(checkoutService.staffInitiateCheckout(eq(AUTHENTICATED_STAFF_ID), any()))
                .thenThrow(new CartBusinessException(CartErrorCodeConstants.CHECKOUT_EXPIRED));

        mockMvc.perform(post(STAFF_INITIATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody("staff-200")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004011))
                .andExpect(jsonPath("$.msg").value("Checkout session expired"));
    }

    @Test
    void staffInitiate_whenSuccess_returnsHttp200() throws Exception {
        setupStaffPrincipalWithPermission();
        when(checkoutService.staffInitiateCheckout(eq(AUTHENTICATED_STAFF_ID), any()))
                .thenReturn(new CheckoutSessionVO());

        mockMvc.perform(post(STAFF_INITIATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody("staff-success")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CommonResult.SUCCESS_CODE))
                .andExpect(jsonPath("$.msg").value("success"));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private void setupStaffPrincipalWithPermission() {
        GeihouSecurityContextHolder.set(new GeihouPrincipal(
                AUTHENTICATED_STAFF_ID, "staff1", 1L,
                Set.of(), Set.of(PERMISSION), "token-http-status-test"));
    }

    private static String initiateBody(String idempotentKeySuffix) {
        return "{\"customerUserId\":7001,\"shopId\":1,\"channel\":\"DINE_IN\","
                + "\"idempotentKey\":\"idem-http-" + idempotentKeySuffix + "\"}";
    }
}
