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
import com.geihou.module.finance.cart.service.CartService;
import com.geihou.module.finance.checkout.CheckoutToOrderTestConfig;
import com.geihou.module.finance.checkout.CheckoutToOrderTestSchemaInitializer;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutInitiateReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutPayReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutSessionVO;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutSessionDO;
import com.geihou.module.finance.checkout.dal.mapper.CheckoutSessionMapper;
import com.geihou.module.finance.checkout.framework.CheckoutStatusEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Staff checkout service integration test (G1-04J).
 *
 * <p>Verifies that staff checkout operations:
 * <ul>
 *   <li>Lock active cart to CHECKOUT on initiate.</li>
 *   <li>Write CHECKOUT_STARTED with operatorRole = "STAFF" and operatorUserId = staffUserId.</li>
 *   <li>Set checkout session creator/updater to staff id.</li>
 *   <li>Idempotent key returns same session.</li>
 *   <li>Reject coupon ids.</li>
 *   <li>Fail on missing active cart.</li>
 *   <li>Query is readonly on non-expired sessions (no event logs).</li>
 *   <li>Query lazy expiry writes staff-attributed expiry log.</li>
 *   <li>Cancel transitions session to ABANDONED, unlocks cart, writes CHECKOUT_ABANDONED with staff attribution.</li>
 *   <li>Cancel is idempotent on terminal state.</li>
 *   <li>Tenant isolation holds.</li>
 * </ul>
 */
@SpringBootTest(
        classes = CheckoutToOrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:staff_checkout_service_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StaffCheckoutServiceTest {

    private static final Long STAFF_USER_ID = 8001L;

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

    // --- Staff initiate locks active cart to CHECKOUT ---

    @Test
    void staffInitiateLocksCartAsCheckout() {
        CartDO cart = createCartWithItems(5001L, 1L);
        assertThat(cart.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());

        CheckoutSessionVO vo = checkoutService.staffInitiateCheckout(STAFF_USER_ID, buildInitiateReq(5001L, 1L, "staff-idem-5001"));

        assertThat(vo.getStatus()).isEqualTo(CheckoutStatusEnum.INITIATED.getCode());
        assertThat(vo.getCartId()).isEqualTo(cart.getId());

        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.CHECKOUT.getCode());
    }

    // --- Staff initiate creates session and writes CHECKOUT_STARTED with STAFF attribution ---

    @Test
    void staffInitiateWritesCheckoutStartedWithStaffAttribution() {
        createCartWithItems(5002L, 1L);

        checkoutService.staffInitiateCheckout(STAFF_USER_ID, buildInitiateReq(5002L, 1L, "staff-idem-5002"));

        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        boolean hasStaffCheckoutStarted = logs.stream()
                .anyMatch(l -> CartEventTypeEnum.CHECKOUT_STARTED.getCode().equals(l.getEventType())
                        && "STAFF".equals(l.getOperatorRole())
                        && STAFF_USER_ID.equals(l.getOperatorUserId()));
        assertThat(hasStaffCheckoutStarted).isTrue();
    }

    // --- Staff initiate sets checkout session creator/updater to staff id ---

    @Test
    void staffInitiateSetsCreatorAndUpdaterToStaffId() {
        createCartWithItems(5003L, 1L);

        CheckoutSessionVO vo = checkoutService.staffInitiateCheckout(STAFF_USER_ID, buildInitiateReq(5003L, 1L, "staff-idem-5003"));

        CheckoutSessionDO session = checkoutSessionMapper.selectById(vo.getId());
        assertThat(session.getCreator()).isEqualTo(String.valueOf(STAFF_USER_ID));
        assertThat(session.getUpdater()).isEqualTo(String.valueOf(STAFF_USER_ID));
    }

    // --- Staff initiate idempotent key returns same session ---

    @Test
    void staffInitiateIdempotentKeyReturnsSameSession() {
        createCartWithItems(5004L, 1L);

        CheckoutSessionVO vo1 = checkoutService.staffInitiateCheckout(STAFF_USER_ID, buildInitiateReq(5004L, 1L, "staff-idem-dup-5004"));
        CheckoutSessionVO vo2 = checkoutService.staffInitiateCheckout(STAFF_USER_ID, buildInitiateReq(5004L, 1L, "staff-idem-dup-5004"));

        assertThat(vo1.getId()).isEqualTo(vo2.getId());
        assertThat(vo1.getSessionToken()).isEqualTo(vo2.getSessionToken());
    }

    // --- Staff initiate rejects coupon ids ---

    @Test
    void staffInitiateRejectsCouponIds() {
        createCartWithItems(5005L, 1L);

        CheckoutInitiateReqVO req = buildInitiateReq(5005L, 1L, "staff-idem-5005");
        req.setCouponIds(List.of("coupon-1"));

        assertThatThrownBy(() -> checkoutService.staffInitiateCheckout(STAFF_USER_ID, req))
                .isInstanceOf(CartBusinessException.class)
                .hasMessageContaining("Coupon invalid");
    }

    // --- Staff initiate missing active cart fails ---

    @Test
    void staffInitiateMissingActiveCartFails() {
        CheckoutInitiateReqVO req = buildInitiateReq(9999L, 1L, "staff-idem-missing");

        assertThatThrownBy(() -> checkoutService.staffInitiateCheckout(STAFF_USER_ID, req))
                .isInstanceOf(CartBusinessException.class);
    }

    // --- Staff query returns session and does not write logs for non-expired session ---

    @Test
    void staffQueryReturnsSessionAndDoesNotWriteLogsForNonExpiredSession() {
        createCartWithItems(5006L, 1L);
        CheckoutSessionVO initiated = checkoutService.staffInitiateCheckout(STAFF_USER_ID, buildInitiateReq(5006L, 1L, "staff-idem-5006"));

        int logCountBefore = cartEventLogMapper.selectList().size();

        CheckoutSessionVO queried = checkoutService.staffQuerySession(STAFF_USER_ID, initiated.getSessionToken());

        assertThat(queried.getId()).isEqualTo(initiated.getId());
        assertThat(queried.getStatus()).isEqualTo(CheckoutStatusEnum.INITIATED.getCode());

        // No new event logs should be written for a non-expired session query
        List<CartEventLogDO> logsAfter = cartEventLogMapper.selectList();
        assertThat(logsAfter).hasSize(logCountBefore);
    }

    // --- Staff query lazy expiry writes staff-attributed expiry log ---

    @Test
    void staffQueryLazyExpiryWritesStaffAttributedExpiryLog() {
        CartDO cart = createCartWithItems(5007L, 1L);
        CheckoutSessionVO initiated = checkoutService.staffInitiateCheckout(STAFF_USER_ID, buildInitiateReq(5007L, 1L, "staff-idem-5007"));

        // Manually set expire_time to past
        CheckoutSessionDO session = checkoutSessionMapper.selectById(initiated.getId());
        session.setExpireTime(LocalDateTime.now().minusMinutes(1));
        checkoutSessionMapper.updateById(session);

        // Query should trigger lazy expiry with staff attribution
        CheckoutSessionVO queried = checkoutService.staffQuerySession(STAFF_USER_ID, initiated.getSessionToken());

        assertThat(queried.getStatus()).isEqualTo(CheckoutStatusEnum.EXPIRED.getCode());

        // Cart should be unlocked
        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());

        // Verify staff-attributed expiry log
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        boolean hasStaffExpiry = logs.stream()
                .anyMatch(l -> CartEventTypeEnum.CHECKOUT_ABANDONED.getCode().equals(l.getEventType())
                        && "STAFF".equals(l.getOperatorRole())
                        && STAFF_USER_ID.equals(l.getOperatorUserId()));
        assertThat(hasStaffExpiry).isTrue();
    }

    // --- Staff cancel transitions session to ABANDONED, unlocks cart, writes CHECKOUT_ABANDONED with staff attribution ---

    @Test
    void staffCancelTransitionsToAbandonedAndWritesStaffEventLog() {
        CartDO cart = createCartWithItems(5008L, 1L);
        CheckoutSessionVO initiated = checkoutService.staffInitiateCheckout(STAFF_USER_ID, buildInitiateReq(5008L, 1L, "staff-idem-5008"));

        CheckoutSessionVO cancelled = checkoutService.staffCancelSession(STAFF_USER_ID, initiated.getSessionToken());

        assertThat(cancelled.getStatus()).isEqualTo(CheckoutStatusEnum.ABANDONED.getCode());

        // Cart should be unlocked
        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());

        // Verify staff-attributed CHECKOUT_ABANDONED event log
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        boolean hasStaffAbandoned = logs.stream()
                .anyMatch(l -> CartEventTypeEnum.CHECKOUT_ABANDONED.getCode().equals(l.getEventType())
                        && "STAFF".equals(l.getOperatorRole())
                        && STAFF_USER_ID.equals(l.getOperatorUserId()));
        assertThat(hasStaffAbandoned).isTrue();

        // Verify session updater is staff
        CheckoutSessionDO session = checkoutSessionMapper.selectById(initiated.getId());
        assertThat(session.getUpdater()).isEqualTo(String.valueOf(STAFF_USER_ID));
    }

    // --- Staff cancel is idempotent on terminal state ---

    @Test
    void staffCancelIsIdempotentOnTerminalState() {
        createCartWithItems(5009L, 1L);
        CheckoutSessionVO initiated = checkoutService.staffInitiateCheckout(STAFF_USER_ID, buildInitiateReq(5009L, 1L, "staff-idem-5009"));

        CheckoutSessionVO cancelled1 = checkoutService.staffCancelSession(STAFF_USER_ID, initiated.getSessionToken());
        CheckoutSessionVO cancelled2 = checkoutService.staffCancelSession(STAFF_USER_ID, initiated.getSessionToken());

        assertThat(cancelled1.getStatus()).isEqualTo(CheckoutStatusEnum.ABANDONED.getCode());
        assertThat(cancelled2.getStatus()).isEqualTo(CheckoutStatusEnum.ABANDONED.getCode());
    }

    // --- Staff checkout tenant isolation holds ---

    @Test
    void staffCheckoutTenantIsolationHolds() {
        createCartWithItems(5010L, 1L);
        CheckoutSessionVO initiated = checkoutService.staffInitiateCheckout(STAFF_USER_ID, buildInitiateReq(5010L, 1L, "staff-idem-5010"));

        // Switch to different tenant
        TenantContextHolder.setTenantId(2L);

        // Query should not find the session (tenant isolation)
        assertThatThrownBy(() -> checkoutService.staffQuerySession(STAFF_USER_ID, initiated.getSessionToken()))
                .isInstanceOf(CartBusinessException.class);

        // Cancel should not find the session (tenant isolation)
        assertThatThrownBy(() -> checkoutService.staffCancelSession(STAFF_USER_ID, initiated.getSessionToken()))
                .isInstanceOf(CartBusinessException.class);
    }

    // --- G1-04L: Staff pay and convert tests ---

    private CheckoutPayReqVO buildPayReq(String paymentMethod) {
        CheckoutPayReqVO req = new CheckoutPayReqVO();
        req.setPaymentMethod(paymentMethod);
        return req;
    }

    private CheckoutPayReqVO buildSimulateFailPayReq() {
        CheckoutPayReqVO req = new CheckoutPayReqVO();
        req.setPaymentMethod("WECHAT_PAY");
        req.setSimulateFail("SIMULATE_FAIL");
        return req;
    }

    // --- Staff pay success -> session PAID, cart CONVERTED, payment fields set, orderId populated ---

    @Test
    void staffPaySuccess_sessionPaid_cartConverted_orderIdPopulated() {
        CartDO cart = createCartWithItems(5101L, 1L);
        CheckoutSessionVO initiated = checkoutService.staffInitiateCheckout(STAFF_USER_ID,
                buildInitiateReq(5101L, 1L, "staff-pay-idem-5101"));

        CheckoutSessionVO paid = checkoutService.staffPaySession(STAFF_USER_ID,
                initiated.getSessionToken(), buildPayReq("WECHAT_PAY"));

        assertThat(paid.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        assertThat(paid.getPaymentMethod()).isEqualTo("WECHAT_PAY");
        assertThat(paid.getPaymentTime()).isNotNull();
        assertThat(paid.getPaymentTradeNo()).isNotNull();
        assertThat(paid.getOrderId()).isNotNull();

        // Cart should be CONVERTED
        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.CONVERTED.getCode());

        // Note: session.updater may be modified by orderService.createFromCheckout
        // during auto-convert, so we don't assert it here. The staff attribution
        // is verified via event logs in other tests.
    }

    // --- Staff pay simulated failure -> session FAILED, cart ACTIVE, CHECKOUT_ABANDONED with STAFF ---

    @Test
    void staffPaySimulatedFailure_sessionFailed_cartActive_staffEventLog() {
        CartDO cart = createCartWithItems(5102L, 1L);
        CheckoutSessionVO initiated = checkoutService.staffInitiateCheckout(STAFF_USER_ID,
                buildInitiateReq(5102L, 1L, "staff-pay-idem-5102"));

        CheckoutSessionVO failed = checkoutService.staffPaySession(STAFF_USER_ID,
                initiated.getSessionToken(), buildSimulateFailPayReq());

        assertThat(failed.getStatus()).isEqualTo(CheckoutStatusEnum.FAILED.getCode());

        // Cart should be back to ACTIVE
        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());

        // Verify staff-attributed CHECKOUT_ABANDONED event log
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        boolean hasStaffAbandoned = logs.stream()
                .anyMatch(l -> CartEventTypeEnum.CHECKOUT_ABANDONED.getCode().equals(l.getEventType())
                        && "STAFF".equals(l.getOperatorRole())
                        && STAFF_USER_ID.equals(l.getOperatorUserId()));
        assertThat(hasStaffAbandoned).isTrue();
    }

    // --- Staff pay expired session -> expiry attributed to staff and rejected ---

    @Test
    void staffPayExpiredSession_expiryAttributedToStaffAndRejected() {
        createCartWithItems(5103L, 1L);
        CheckoutSessionVO initiated = checkoutService.staffInitiateCheckout(STAFF_USER_ID,
                buildInitiateReq(5103L, 1L, "staff-pay-idem-5103"));

        // Manually expire the session
        CheckoutSessionDO session = checkoutSessionMapper.selectById(initiated.getId());
        session.setExpireTime(LocalDateTime.now().minusMinutes(1));
        checkoutSessionMapper.updateById(session);

        assertThatThrownBy(() -> checkoutService.staffPaySession(STAFF_USER_ID,
                initiated.getSessionToken(), buildPayReq("WECHAT_PAY")))
                .isInstanceOf(CartBusinessException.class);

        // Verify session is EXPIRED
        CheckoutSessionDO expiredSession = checkoutSessionMapper.selectById(initiated.getId());
        assertThat(expiredSession.getStatus()).isEqualTo(CheckoutStatusEnum.EXPIRED.getCode());

        // Verify staff-attributed expiry log
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        boolean hasStaffExpiry = logs.stream()
                .anyMatch(l -> CartEventTypeEnum.CHECKOUT_ABANDONED.getCode().equals(l.getEventType())
                        && "STAFF".equals(l.getOperatorRole())
                        && STAFF_USER_ID.equals(l.getOperatorUserId()));
        assertThat(hasStaffExpiry).isTrue();
    }

    // --- Staff pay already PAID with orderId -> idempotent return ---

    @Test
    void staffPayAlreadyPaidWithOrderId_idempotentReturn() {
        createCartWithItems(5104L, 1L);
        CheckoutSessionVO initiated = checkoutService.staffInitiateCheckout(STAFF_USER_ID,
                buildInitiateReq(5104L, 1L, "staff-pay-idem-5104"));

        CheckoutPayReqVO payReq = buildPayReq("WECHAT_PAY");
        CheckoutSessionVO firstPay = checkoutService.staffPaySession(STAFF_USER_ID,
                initiated.getSessionToken(), payReq);
        assertThat(firstPay.getOrderId()).isNotNull();

        // Re-pay should return same session idempotently
        CheckoutSessionVO secondPay = checkoutService.staffPaySession(STAFF_USER_ID,
                initiated.getSessionToken(), payReq);
        assertThat(secondPay.getId()).isEqualTo(firstPay.getId());
        assertThat(secondPay.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        assertThat(secondPay.getOrderId()).isEqualTo(firstPay.getOrderId());
    }

    // --- Staff pay already PAID with null orderId -> retries staff convert ---

    @Test
    void staffPayAlreadyPaidWithNullOrderId_retriesStaffConvert() {
        createCartWithItems(5105L, 1L);
        CheckoutSessionVO initiated = checkoutService.staffInitiateCheckout(STAFF_USER_ID,
                buildInitiateReq(5105L, 1L, "staff-pay-idem-5105"));

        CheckoutSessionVO paid = checkoutService.staffPaySession(STAFF_USER_ID,
                initiated.getSessionToken(), buildPayReq("WECHAT_PAY"));
        assertThat(paid.getOrderId()).isNotNull();

        // Reset orderId to null to simulate previous auto-convert failure
        checkoutSessionMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CheckoutSessionDO>()
                        .eq(CheckoutSessionDO::getId, paid.getId())
                        .set(CheckoutSessionDO::getOrderId, null));

        // Re-pay should retry conversion and populate orderId
        CheckoutSessionVO result = checkoutService.staffPaySession(STAFF_USER_ID,
                initiated.getSessionToken(), buildPayReq("WECHAT_PAY"));
        assertThat(result.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        assertThat(result.getOrderId()).isNotNull();
    }

    // --- Staff convert PAID/null orderId -> orderId populated ---

    @Test
    void staffConvertPaidNullOrderId_orderIdPopulated() {
        createCartWithItems(5106L, 1L);
        CheckoutSessionVO initiated = checkoutService.staffInitiateCheckout(STAFF_USER_ID,
                buildInitiateReq(5106L, 1L, "staff-pay-idem-5106"));

        CheckoutSessionVO paid = checkoutService.staffPaySession(STAFF_USER_ID,
                initiated.getSessionToken(), buildPayReq("WECHAT_PAY"));
        assertThat(paid.getOrderId()).isNotNull();

        // Reset orderId to null to simulate unconverted PAID state
        checkoutSessionMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CheckoutSessionDO>()
                        .eq(CheckoutSessionDO::getId, paid.getId())
                        .set(CheckoutSessionDO::getOrderId, null));

        CheckoutSessionVO converted = checkoutService.staffConvertToOrder(STAFF_USER_ID,
                initiated.getSessionToken());
        assertThat(converted.getOrderId()).isNotNull();
        assertThat(converted.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
    }

    // --- Staff convert existing orderId -> idempotent return ---

    @Test
    void staffConvertExistingOrderId_idempotentReturn() {
        createCartWithItems(5107L, 1L);
        CheckoutSessionVO initiated = checkoutService.staffInitiateCheckout(STAFF_USER_ID,
                buildInitiateReq(5107L, 1L, "staff-pay-idem-5107"));

        CheckoutSessionVO paid = checkoutService.staffPaySession(STAFF_USER_ID,
                initiated.getSessionToken(), buildPayReq("WECHAT_PAY"));
        Long originalOrderId = paid.getOrderId();
        assertThat(originalOrderId).isNotNull();

        // Convert again — should be idempotent
        CheckoutSessionVO result = checkoutService.staffConvertToOrder(STAFF_USER_ID,
                initiated.getSessionToken());
        assertThat(result.getOrderId()).isEqualTo(originalOrderId);
    }

    // --- Staff convert non-PAID -> rejected ---

    @Test
    void staffConvertNonPaid_rejected() {
        createCartWithItems(5108L, 1L);
        CheckoutSessionVO initiated = checkoutService.staffInitiateCheckout(STAFF_USER_ID,
                buildInitiateReq(5108L, 1L, "staff-pay-idem-5108"));

        // Session is INITIATED, not PAID — convert should fail
        assertThatThrownBy(() -> checkoutService.staffConvertToOrder(STAFF_USER_ID,
                initiated.getSessionToken()))
                .isInstanceOf(CartBusinessException.class)
                .hasMessageContaining("not PAID");
    }

    // --- Staff pay/convert tenant isolation ---

    @Test
    void staffPayConvertTenantIsolationHolds() {
        createCartWithItems(5109L, 1L);
        CheckoutSessionVO initiated = checkoutService.staffInitiateCheckout(STAFF_USER_ID,
                buildInitiateReq(5109L, 1L, "staff-pay-idem-5109"));

        // Switch to different tenant
        TenantContextHolder.setTenantId(2L);

        // Pay should not find the session (tenant isolation)
        assertThatThrownBy(() -> checkoutService.staffPaySession(STAFF_USER_ID,
                initiated.getSessionToken(), buildPayReq("WECHAT_PAY")))
                .isInstanceOf(CartBusinessException.class);

        // Convert should not find the session (tenant isolation)
        assertThatThrownBy(() -> checkoutService.staffConvertToOrder(STAFF_USER_ID,
                initiated.getSessionToken()))
                .isInstanceOf(CartBusinessException.class);
    }
}
