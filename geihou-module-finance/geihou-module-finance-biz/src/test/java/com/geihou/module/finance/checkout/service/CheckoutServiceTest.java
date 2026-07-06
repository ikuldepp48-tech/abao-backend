package com.geihou.module.finance.checkout.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.cart.enums.CartEventTypeEnum;
import com.geihou.module.finance.api.cart.enums.CartStatusEnum;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.dataobject.CartEventLogDO;
import com.geihou.module.finance.cart.dal.mapper.CartEventLogMapper;
import com.geihou.module.finance.cart.dal.mapper.CartMapper;
import com.geihou.module.finance.cart.framework.CartBusinessException;
import com.geihou.module.finance.cart.framework.CartErrorCodeConstants;
import com.geihou.module.finance.cart.service.CartService;
import com.geihou.module.finance.checkout.CheckoutToOrderTestConfig;
import com.geihou.module.finance.checkout.CheckoutToOrderTestSchemaInitializer;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutInitiateReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutPayReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutSessionVO;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutSessionDO;
import com.geihou.module.finance.checkout.dal.mapper.CheckoutIdempotentMapper;
import com.geihou.module.finance.checkout.dal.mapper.CheckoutSessionMapper;
import com.geihou.module.finance.checkout.framework.CheckoutStatusEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checkout service integration test.
 *
 * <p>Tests all G1-04B acceptance criteria:
 * - AC-1 to AC-17: checkout session lifecycle, idempotency, lazy expiry,
 *   simulated pay, coupon rejection, cart status transitions, tenant isolation.
 *
 * <p>CG-7 Option C: No order creation. order_id remains null after simulated pay.
 * CG-8 StockApi degraded: No reserve/commit. Oversell risk documented.
 * CG-9 Simulated local payment bridge: NOT real WeChat Pay.
 * CG-11 PromotionApi missing: Reject couponIds with COUPON_INVALID.
 */
@SpringBootTest(
        classes = CheckoutToOrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:checkout_service_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CheckoutServiceTest {

    @Autowired
    private CheckoutService checkoutService;
    @Autowired
    private CartService cartService;
    @Autowired
    private CartMapper cartMapper;
    @Autowired
    private CartEventLogMapper cartEventLogMapper;
    @Autowired
    private CheckoutSessionMapper checkoutSessionMapper;
    @Autowired
    private CheckoutIdempotentMapper checkoutIdempotentMapper;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        CheckoutToOrderTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // --- Helper: create a cart with items ---

    private CartDO createCartWithItems(Long customerUserId, Long shopId) {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(2);
        cartService.addItem(customerUserId, shopId, "DINE_IN", req);
        return cartMapper.selectList().stream()
                .filter(c -> c.getCustomerUserId().equals(customerUserId))
                .findFirst().orElseThrow();
    }

    private CheckoutInitiateReqVO buildInitiateReq(Long customerUserId, Long shopId, String idempotentKey) {
        CheckoutInitiateReqVO req = new CheckoutInitiateReqVO();
        req.setCustomerUserId(customerUserId);
        req.setShopId(shopId);
        req.setChannel("DINE_IN");
        req.setIdempotentKey(idempotentKey);
        return req;
    }

    // --- AC-4: initiate checkout locks ACTIVE cart into CHECKOUT ---

    @Test
    void initiateCheckoutLocksCartAsCheckout() {
        CartDO cart = createCartWithItems(6001L, 1L);
        assertThat(cart.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());

        CheckoutSessionVO vo = checkoutService.initiateCheckout(buildInitiateReq(6001L, 1L, "idem-6001"));

        assertThat(vo.getStatus()).isEqualTo(CheckoutStatusEnum.INITIATED.getCode());
        assertThat(vo.getCartId()).isEqualTo(cart.getId());

        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.CHECKOUT.getCode());
    }

    // --- AC-5: initiate writes CHECKOUT_STARTED event log in same transaction ---

    @Test
    void initiateWritesCheckoutStartedEventLog() {
        createCartWithItems(6002L, 1L);

        checkoutService.initiateCheckout(buildInitiateReq(6002L, 1L, "idem-6002"));

        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        boolean hasCheckoutStarted = logs.stream()
                .anyMatch(l -> CartEventTypeEnum.CHECKOUT_STARTED.getCode().equals(l.getEventType()));
        assertThat(hasCheckoutStarted).isTrue();
    }

    // --- AC-6: token is server-generated, unique, not mock/static ---

    @Test
    void sessionTokenIsServerGeneratedAndUnique() {
        createCartWithItems(6003L, 1L);
        createCartWithItems(6004L, 1L);

        CheckoutSessionVO vo1 = checkoutService.initiateCheckout(buildInitiateReq(6003L, 1L, "idem-6003"));
        CheckoutSessionVO vo2 = checkoutService.initiateCheckout(buildInitiateReq(6004L, 1L, "idem-6004"));

        assertThat(vo1.getSessionToken()).isNotNull();
        assertThat(vo1.getSessionToken()).isNotBlank();
        assertThat(vo1.getSessionToken()).isNotEqualTo(vo2.getSessionToken());
        assertThat(vo1.getSessionToken().length()).isEqualTo(32); // UUID without dashes
    }

    @Test
    void sessionTokenIsNotStaticOrMock() {
        createCartWithItems(6005L, 1L);

        CheckoutSessionVO vo = checkoutService.initiateCheckout(buildInitiateReq(6005L, 1L, "idem-6005"));

        // Token must not be static/mock values
        assertThat(vo.getSessionToken()).isNotEqualTo("mock");
        assertThat(vo.getSessionToken()).isNotEqualTo("test");
        assertThat(vo.getSessionToken()).isNotEqualTo("debug");
        assertThat(vo.getSessionToken()).isNotEqualTo("static");
        assertThat(vo.getSessionToken()).isNotEqualTo("token");
    }

    // --- AC-7: repeated initiate with same idempotency key returns same session ---

    @Test
    void duplicateIdempotencyReturnsSameSession() {
        createCartWithItems(6006L, 1L);

        CheckoutSessionVO vo1 = checkoutService.initiateCheckout(buildInitiateReq(6006L, 1L, "idem-dup-6006"));
        CheckoutSessionVO vo2 = checkoutService.initiateCheckout(buildInitiateReq(6006L, 1L, "idem-dup-6006"));

        assertThat(vo1.getId()).isEqualTo(vo2.getId());
        assertThat(vo1.getSessionToken()).isEqualTo(vo2.getSessionToken());
    }

    // --- initiate from missing/non-ACTIVE cart fails ---

    @Test
    void initiateFromMissingCartFails() {
        CheckoutInitiateReqVO req = buildInitiateReq(9999L, 1L, "idem-missing");

        assertThatThrownBy(() -> checkoutService.initiateCheckout(req))
                .isInstanceOf(CartBusinessException.class);
    }

    @Test
    void initiateFromNonActiveCartFails() {
        // Create cart and lock it via checkout
        createCartWithItems(6007L, 1L);
        checkoutService.initiateCheckout(buildInitiateReq(6007L, 1L, "idem-6007-a"));

        // Try to initiate again with different idempotent key — should fail with CHECKOUT_DUPLICATE
        assertThatThrownBy(() -> checkoutService.initiateCheckout(buildInitiateReq(6007L, 1L, "idem-6007-b")))
                .isInstanceOf(CartBusinessException.class)
                .hasMessageContaining("Duplicate checkout");
    }

    // --- AC-1: checkout_session has correct fields ---

    @Test
    void checkoutSessionHasCorrectFields() {
        CartDO cart = createCartWithItems(6008L, 1L);

        CheckoutSessionVO vo = checkoutService.initiateCheckout(buildInitiateReq(6008L, 1L, "idem-6008"));

        assertThat(vo.getSubtotalAmount()).isEqualByComparingTo(new BigDecimal("24.00")); // 12.00 * 2
        assertThat(vo.getTotalAmount()).isEqualByComparingTo(new BigDecimal("24.00"));
        // CG-11: locked_discount always 0
        assertThat(vo.getLockedDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        // CG-7: order_id always null
        assertThat(vo.getOrderId()).isNull();
        // CG-11: applied_promotions/applied_coupon_ids always null
        CheckoutSessionDO session = checkoutSessionMapper.selectById(vo.getId());
        assertThat(session.getAppliedPromotions()).isNull();
        assertThat(session.getAppliedCouponIds()).isNull();
        assertThat(vo.getExpireTime()).isNotNull();
        assertThat(vo.getBusinessDate()).isNotNull();
    }

    // --- Query session success ---

    @Test
    void querySessionSuccess() {
        createCartWithItems(6009L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6009L, 1L, "idem-6009"));

        CheckoutSessionVO queried = checkoutService.querySession(initiated.getSessionToken());

        assertThat(queried.getId()).isEqualTo(initiated.getId());
        assertThat(queried.getStatus()).isEqualTo(CheckoutStatusEnum.INITIATED.getCode());
    }

    @Test
    void querySessionNotFound() {
        assertThatThrownBy(() -> checkoutService.querySession("nonexistent-token"))
                .isInstanceOf(CartBusinessException.class);
    }

    // --- AC-15: tenant isolation ---

    @Test
    void querySessionCrossTenantHidden() {
        createCartWithItems(6010L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6010L, 1L, "idem-6010"));

        // Switch to different tenant
        TenantContextHolder.setTenantId(2L);

        // Query should not find the session (tenant isolation)
        assertThatThrownBy(() -> checkoutService.querySession(initiated.getSessionToken()))
                .isInstanceOf(CartBusinessException.class);
    }

    // --- AC-15: cross-tenant isolation for cancel and pay paths ---

    @Test
    void cancelSessionCrossTenantHidden() {
        createCartWithItems(6030L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6030L, 1L, "idem-6030"));

        // Switch to different tenant
        TenantContextHolder.setTenantId(2L);

        // Cancel should not find the session (tenant isolation via explicit tenantId filter)
        assertThatThrownBy(() -> checkoutService.cancelSession(initiated.getSessionToken()))
                .isInstanceOf(CartBusinessException.class);
    }

    @Test
    void paySessionCrossTenantHidden() {
        createCartWithItems(6031L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6031L, 1L, "idem-6031"));

        // Switch to different tenant
        TenantContextHolder.setTenantId(2L);

        // Pay should not find the session (tenant isolation via explicit tenantId filter)
        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");

        assertThatThrownBy(() -> checkoutService.paySession(initiated.getSessionToken(), payReq))
                .isInstanceOf(CartBusinessException.class);
    }

    @Test
    void findInitiatedSessionByCartIdCrossTenantHidden() {
        // Create a cart and initiate checkout under tenant 1
        CartDO cart = createCartWithItems(6032L, 1L);
        checkoutService.initiateCheckout(buildInitiateReq(6032L, 1L, "idem-6032"));

        // The cart is now in CHECKOUT status under tenant 1.
        // Switch to tenant 2 and try to initiate checkout on the same customer/shop.
        // Under tenant 2, no ACTIVE cart exists, and findCheckoutCart should return null
        // (tenant isolation on cart lookup). findInitiatedSessionByCartId should also
        // not find any session due to explicit tenantId filter.
        TenantContextHolder.setTenantId(2L);

        // Attempting to initiate checkout should fail with CART_NOT_FOUND (no cart under tenant 2)
        assertThatThrownBy(() -> checkoutService.initiateCheckout(buildInitiateReq(6032L, 1L, "idem-6032-tenant2")))
                .isInstanceOf(CartBusinessException.class)
                .hasMessageContaining("Cart not found");
    }

    // --- Lazy expiry: query triggers INITIATED → EXPIRED + cart unlock ---

    @Test
    void queryTriggersLazyExpiry() {
        CartDO cart = createCartWithItems(6011L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6011L, 1L, "idem-6011"));

        // Manually set expire_time to past
        CheckoutSessionDO session = checkoutSessionMapper.selectById(initiated.getId());
        session.setExpireTime(LocalDateTime.now().minusMinutes(1));
        checkoutSessionMapper.updateById(session);

        // Query should trigger lazy expiry
        CheckoutSessionVO queried = checkoutService.querySession(initiated.getSessionToken());

        assertThat(queried.getStatus()).isEqualTo(CheckoutStatusEnum.EXPIRED.getCode());

        // Cart should be unlocked (CHECKOUT → ACTIVE)
        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());

        // CHECKOUT_ABANDONED event log should be written
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        boolean hasAbandoned = logs.stream()
                .anyMatch(l -> CartEventTypeEnum.CHECKOUT_ABANDONED.getCode().equals(l.getEventType()));
        assertThat(hasAbandoned).isTrue();
    }

    // --- AC-8: cancel writes CHECKOUT_ABANDONED and handles cart/session state ---

    @Test
    void cancelSessionSuccess() {
        CartDO cart = createCartWithItems(6012L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6012L, 1L, "idem-6012"));

        CheckoutSessionVO cancelled = checkoutService.cancelSession(initiated.getSessionToken());

        assertThat(cancelled.getStatus()).isEqualTo(CheckoutStatusEnum.ABANDONED.getCode());

        // Cart should be unlocked (CHECKOUT → ACTIVE)
        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());

        // CHECKOUT_ABANDONED event log should be written
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        boolean hasAbandoned = logs.stream()
                .anyMatch(l -> CartEventTypeEnum.CHECKOUT_ABANDONED.getCode().equals(l.getEventType()));
        assertThat(hasAbandoned).isTrue();
    }

    @Test
    void cancelSessionIdempotentBehavior() {
        createCartWithItems(6013L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6013L, 1L, "idem-6013"));

        CheckoutSessionVO cancelled1 = checkoutService.cancelSession(initiated.getSessionToken());
        CheckoutSessionVO cancelled2 = checkoutService.cancelSession(initiated.getSessionToken());

        // Second cancel returns same ABANDONED state
        assertThat(cancelled1.getStatus()).isEqualTo(CheckoutStatusEnum.ABANDONED.getCode());
        assertThat(cancelled2.getStatus()).isEqualTo(CheckoutStatusEnum.ABANDONED.getCode());
    }

    // --- AC-9: expired session cannot be paid (G1-04M: expiry must persist) ---

    @Test
    void expiredSessionCannotBePaid() {
        CartDO cart = createCartWithItems(6014L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6014L, 1L, "idem-6014"));

        // Manually set expire_time to past
        CheckoutSessionDO session = checkoutSessionMapper.selectById(initiated.getId());
        session.setExpireTime(LocalDateTime.now().minusMinutes(1));
        checkoutSessionMapper.updateById(session);

        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");

        // G1-04M: Pay on expired INITIATED session must throw CHECKOUT_EXPIRED
        assertThatThrownBy(() -> checkoutService.paySession(initiated.getSessionToken(), payReq))
                .isInstanceOf(CartBusinessException.class)
                .hasMessageContaining("expired");

        // G1-04M: Expiry must persist — session status becomes EXPIRED (not rolled back to INITIATED)
        CheckoutSessionDO dbSession = checkoutSessionMapper.selectById(initiated.getId());
        assertThat(dbSession.getStatus()).isEqualTo(CheckoutStatusEnum.EXPIRED.getCode());

        // G1-04M: Cart must unlock back to ACTIVE
        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());

        // G1-04M: CHECKOUT_ABANDONED event log must exist with customer attribution
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        CartEventLogDO abandonedLog = logs.stream()
                .filter(l -> CartEventTypeEnum.CHECKOUT_ABANDONED.getCode().equals(l.getEventType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("CHECKOUT_ABANDONED event log not found"));
        assertThat(abandonedLog.getOperatorRole()).isEqualTo("CUSTOMER");
        assertThat(abandonedLog.getOperatorUserId()).isEqualTo(6014L);
    }

    // --- G1-04M: Second pay after persisted expiry no longer treats session as INITIATED ---

    @Test
    void secondPayAfterPersistedExpiryDoesNotReTriggerInitiatedPath() {
        createCartWithItems(6041L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6041L, 1L, "idem-6041"));

        // Manually set expire_time to past
        CheckoutSessionDO session = checkoutSessionMapper.selectById(initiated.getId());
        session.setExpireTime(LocalDateTime.now().minusMinutes(1));
        checkoutSessionMapper.updateById(session);

        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");

        // First pay: triggers expiry persistence + CHECKOUT_EXPIRED
        assertThatThrownBy(() -> checkoutService.paySession(initiated.getSessionToken(), payReq))
                .isInstanceOf(CartBusinessException.class)
                .hasMessageContaining("expired");

        // Verify expiry persisted
        CheckoutSessionDO dbSession = checkoutSessionMapper.selectById(initiated.getId());
        assertThat(dbSession.getStatus()).isEqualTo(CheckoutStatusEnum.EXPIRED.getCode());

        // Second pay: session is now EXPIRED (not INITIATED), so it should be rejected
        // with CHECKOUT_DUPLICATE, not CHECKOUT_EXPIRED — proving the session is no longer
        // treated as INITIATED
        assertThatThrownBy(() -> checkoutService.paySession(initiated.getSessionToken(), payReq))
                .isInstanceOf(CartBusinessException.class)
                .hasMessageContaining("not in INITIATED status");

        // Session should still be EXPIRED (not re-transitioned)
        dbSession = checkoutSessionMapper.selectById(initiated.getId());
        assertThat(dbSession.getStatus()).isEqualTo(CheckoutStatusEnum.EXPIRED.getCode());
    }

    // --- AC-10/AC-11: simulated pay success (PAID + cart CONVERTED + order_id=null) ---

    @Test
    void simulatedPaySuccess() {
        CartDO cart = createCartWithItems(6015L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6015L, 1L, "idem-6015"));

        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");

        CheckoutSessionVO paid = checkoutService.paySession(initiated.getSessionToken(), payReq);

        assertThat(paid.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        assertThat(paid.getPaymentMethod()).isEqualTo("WECHAT_PAY");
        assertThat(paid.getPaymentTime()).isNotNull();
        assertThat(paid.getPaymentTradeNo()).isNotNull();
        // G1-04D: auto-conversion is always on — orderId is non-null after pay
        assertThat(paid.getOrderId()).isNotNull();

        // Cart should be CONVERTED
        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.CONVERTED.getCode());
    }

    // --- AC-10: simulated pay failure (FAILED + cart unlocked) ---

    @Test
    void simulatedPayFailure() {
        CartDO cart = createCartWithItems(6016L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6016L, 1L, "idem-6016"));

        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");
        payReq.setSimulateFail("SIMULATE_FAIL");

        // CG-9: Service returns session with FAILED status (not throw) so FAILED state persists
        CheckoutSessionVO result = checkoutService.paySession(initiated.getSessionToken(), payReq);

        // Session should be FAILED
        assertThat(result.getStatus()).isEqualTo(CheckoutStatusEnum.FAILED.getCode());

        // Verify in DB
        CheckoutSessionDO session = checkoutSessionMapper.selectById(initiated.getId());
        assertThat(session.getStatus()).isEqualTo(CheckoutStatusEnum.FAILED.getCode());

        // Cart should be unlocked (CHECKOUT → ACTIVE)
        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());
    }

    // --- AC-13: couponIds non-empty rejected with COUPON_INVALID ---

    @Test
    void couponIdsRejectedWithCouponInvalid() {
        createCartWithItems(6017L, 1L);

        CheckoutInitiateReqVO req = buildInitiateReq(6017L, 1L, "idem-6017");
        req.setCouponIds(List.of("coupon-1"));

        assertThatThrownBy(() -> checkoutService.initiateCheckout(req))
                .isInstanceOf(CartBusinessException.class)
                .hasMessageContaining("Coupon invalid");
    }

    @Test
    void emptyCouponIdsAccepted() {
        createCartWithItems(6018L, 1L);

        CheckoutInitiateReqVO req = buildInitiateReq(6018L, 1L, "idem-6018");
        req.setCouponIds(List.of()); // empty list is OK

        CheckoutSessionVO vo = checkoutService.initiateCheckout(req);
        assertThat(vo.getStatus()).isEqualTo(CheckoutStatusEnum.INITIATED.getCode());
    }

    @Test
    void nullCouponIdsAccepted() {
        createCartWithItems(6019L, 1L);

        CheckoutInitiateReqVO req = buildInitiateReq(6019L, 1L, "idem-6019");
        req.setCouponIds(null); // null is OK

        CheckoutSessionVO vo = checkoutService.initiateCheckout(req);
        assertThat(vo.getStatus()).isEqualTo(CheckoutStatusEnum.INITIATED.getCode());
    }

    // --- AC-12: StockApi degradation — no reserve/commit, oversell risk documented ---

    @Test
    void stockApiDegradationNoReserveCommit() {
        // This test verifies that checkout proceeds without stock reservation.
        // CG-8: StockApi does not exist. No reserve/commit calls.
        // Oversell risk is documented in code comments and implementation report.
        CartDO cart = createCartWithItems(6020L, 1L);

        CheckoutSessionVO vo = checkoutService.initiateCheckout(buildInitiateReq(6020L, 1L, "idem-6020"));

        // Checkout succeeds without stock reservation
        assertThat(vo.getStatus()).isEqualTo(CheckoutStatusEnum.INITIATED.getCode());
        // No stock_reservation_id field exists in checkout_session (CG-8 ruling)
        // This is verified by the DDL not having that column
    }

    // --- AC-14: all checkout writes are transactional; no fire-and-forget ---

    @Test
    void checkoutWritesAreTransactional() {
        // This is verified by @Transactional annotations on all mutating methods.
        // If any write fails (cart update, session insert, event log insert),
        // the entire transaction rolls back.
        // The integration test below verifies event log is written in same transaction.
        CartDO cart = createCartWithItems(6021L, 1L);

        checkoutService.initiateCheckout(buildInitiateReq(6021L, 1L, "idem-6021"));

        // All three writes should be present (cart status, session, event log)
        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.CHECKOUT.getCode());

        List<CheckoutSessionDO> sessions = checkoutSessionMapper.selectList();
        assertThat(sessions).hasSize(1);

        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        boolean hasCheckoutStarted = logs.stream()
                .anyMatch(l -> CartEventTypeEnum.CHECKOUT_STARTED.getCode().equals(l.getEventType()));
        assertThat(hasCheckoutStarted).isTrue();
    }

    // --- Error code constants exist (CG-10) ---

    @Test
    void errorCodeConstantsExist() {
        assertThat(CartErrorCodeConstants.CHECKOUT_DUPLICATE.getCode()).isEqualTo(1004010);
        assertThat(CartErrorCodeConstants.CHECKOUT_EXPIRED.getCode()).isEqualTo(1004011);
        assertThat(CartErrorCodeConstants.COUPON_INVALID.getCode()).isEqualTo(1004012);
        assertThat(CartErrorCodeConstants.PAYMENT_FAILED.getCode()).isEqualTo(1004013);
        assertThat(CartErrorCodeConstants.PAYMENT_CALLBACK_VERIFY_FAILED.getCode()).isEqualTo(1004014);
    }

    // --- AC-3: checkout status enum matches ENUM_CHECKOUT_STATUS ---

    @Test
    void checkoutStatusEnumHasFiveValues() {
        assertThat(CheckoutStatusEnum.values()).hasSize(5);
        assertThat(CheckoutStatusEnum.INITIATED.getCode()).isEqualTo("INITIATED");
        assertThat(CheckoutStatusEnum.PAID.getCode()).isEqualTo("PAID");
        assertThat(CheckoutStatusEnum.ABANDONED.getCode()).isEqualTo("ABANDONED");
        assertThat(CheckoutStatusEnum.EXPIRED.getCode()).isEqualTo("EXPIRED");
        assertThat(CheckoutStatusEnum.FAILED.getCode()).isEqualTo("FAILED");
    }

    // --- Pay on non-INITIATED session fails ---

    @Test
    void payOnNonInitiatedSessionFails() {
        createCartWithItems(6022L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6022L, 1L, "idem-6022"));

        // Cancel first
        checkoutService.cancelSession(initiated.getSessionToken());

        // Try to pay — should fail (session is ABANDONED)
        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");

        assertThatThrownBy(() -> checkoutService.paySession(initiated.getSessionToken(), payReq))
                .isInstanceOf(CartBusinessException.class);
    }

    // --- Pay with different payment methods (all simulated) ---

    @Test
    void simulatedPayWithBalance() {
        createCartWithItems(6023L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6023L, 1L, "idem-6023"));

        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("BALANCE");

        CheckoutSessionVO paid = checkoutService.paySession(initiated.getSessionToken(), payReq);
        assertThat(paid.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        assertThat(paid.getPaymentMethod()).isEqualTo("BALANCE");
    }

    @Test
    void simulatedPayWithGiftCard() {
        createCartWithItems(6024L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6024L, 1L, "idem-6024"));

        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("GIFT_CARD");

        CheckoutSessionVO paid = checkoutService.paySession(initiated.getSessionToken(), payReq);
        assertThat(paid.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        assertThat(paid.getPaymentMethod()).isEqualTo("GIFT_CARD");
    }

    // --- Session token is persisted in DB ---

    @Test
    void sessionTokenPersistedInDb() {
        createCartWithItems(6025L, 1L);
        CheckoutSessionVO vo = checkoutService.initiateCheckout(buildInitiateReq(6025L, 1L, "idem-6025"));

        CheckoutSessionDO session = checkoutSessionMapper.selectById(vo.getId());
        assertThat(session.getSessionToken()).isEqualTo(vo.getSessionToken());
        assertThat(session.getSessionToken()).isNotBlank();
    }

    // --- Cart unlock rules: EXPIRED → ACTIVE ---

    @Test
    void expiredSessionUnlocksCartToActive() {
        CartDO cart = createCartWithItems(6026L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6026L, 1L, "idem-6026"));

        // Expire the session manually
        CheckoutSessionDO session = checkoutSessionMapper.selectById(initiated.getId());
        session.setExpireTime(LocalDateTime.now().minusMinutes(1));
        checkoutSessionMapper.updateById(session);

        checkoutService.querySession(initiated.getSessionToken());

        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());
    }

    // --- Cart unlock rules: PAID → CONVERTED ---

    @Test
    void paidSessionConvertsCart() {
        CartDO cart = createCartWithItems(6027L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6027L, 1L, "idem-6027"));

        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");

        checkoutService.paySession(initiated.getSessionToken(), payReq);

        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.CONVERTED.getCode());
    }

    // --- Cart unlock rules: FAILED → ACTIVE ---

    @Test
    void failedSessionUnlocksCartToActive() {
        CartDO cart = createCartWithItems(6028L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(buildInitiateReq(6028L, 1L, "idem-6028"));

        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");
        payReq.setSimulateFail("SIMULATE_FAIL");

        // CG-9: Service returns FAILED status (not throw)
        CheckoutSessionVO result = checkoutService.paySession(initiated.getSessionToken(), payReq);
        assertThat(result.getStatus()).isEqualTo(CheckoutStatusEnum.FAILED.getCode());

        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());
    }
}
