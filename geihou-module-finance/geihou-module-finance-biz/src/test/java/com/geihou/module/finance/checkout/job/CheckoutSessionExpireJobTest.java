package com.geihou.module.finance.checkout.job;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.cart.enums.CartEventTypeEnum;
import com.geihou.module.finance.api.cart.enums.CartStatusEnum;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.dataobject.CartEventLogDO;
import com.geihou.module.finance.cart.dal.mapper.CartEventLogMapper;
import com.geihou.module.finance.cart.dal.mapper.CartMapper;
import com.geihou.module.finance.cart.service.CartService;
import com.geihou.module.finance.checkout.CheckoutToOrderTestConfig;
import com.geihou.module.finance.checkout.CheckoutToOrderTestSchemaInitializer;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutInitiateReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutSessionVO;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutSessionDO;
import com.geihou.module.finance.checkout.dal.mapper.CheckoutSessionMapper;
import com.geihou.module.finance.checkout.framework.CheckoutStatusEnum;
import com.geihou.module.finance.checkout.service.CheckoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkout session expire job test.
 *
 * <p>G1-04E: Tests that INITIATED checkout sessions past their expire_time are
 * proactively expired by the scheduled job (not just lazy expiry).
 *
 * <p>Tests call job methods directly (not via @Scheduled).
 */
@SpringBootTest(
        classes = CheckoutToOrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:checkout_expire_job_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CheckoutSessionExpireJobTest {

    @Autowired
    private CheckoutSessionExpireJob expireJob;
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

    // --- Helper ---

    private CheckoutSessionVO createInitiatedSession(Long customerUserId) {
        CartAddItemReqVO cartReq = new CartAddItemReqVO();
        cartReq.setSkuId(1001L);
        cartReq.setQuantity(2);
        cartService.addItem(customerUserId, 1L, "DINE_IN", cartReq);

        CheckoutInitiateReqVO req = new CheckoutInitiateReqVO();
        req.setCustomerUserId(customerUserId);
        req.setShopId(1L);
        req.setChannel("DINE_IN");
        req.setIdempotentKey("idem-expire-" + customerUserId);
        return checkoutService.initiateCheckout(req);
    }

    private void setExpireTimeToPast(Long sessionId) {
        CheckoutSessionDO session = checkoutSessionMapper.selectById(sessionId);
        session.setExpireTime(LocalDateTime.now().minusMinutes(1));
        checkoutSessionMapper.updateById(session);
    }

    // --- Tests ---

    @Test
    void expireOverdueSession_transitionsToExpired() {
        CheckoutSessionVO initiated = createInitiatedSession(5001L);
        setExpireTimeToPast(initiated.getId());

        expireJob.execute();

        CheckoutSessionDO session = checkoutSessionMapper.selectById(initiated.getId());
        assertThat(session.getStatus()).isEqualTo(CheckoutStatusEnum.EXPIRED.getCode());
    }

    @Test
    void expireOverdueSession_unlocksCart() {
        CheckoutSessionVO initiated = createInitiatedSession(5002L);
        setExpireTimeToPast(initiated.getId());

        // Cart should be CHECKOUT before job runs
        CartDO cartBefore = cartMapper.selectById(initiated.getCartId());
        assertThat(cartBefore.getStatus()).isEqualTo(CartStatusEnum.CHECKOUT.getCode());

        expireJob.execute();

        CartDO cartAfter = cartMapper.selectById(initiated.getCartId());
        assertThat(cartAfter.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());
    }

    @Test
    void expireOverdueSession_writesEventLogWithSystemOperator() {
        CheckoutSessionVO initiated = createInitiatedSession(5003L);
        setExpireTimeToPast(initiated.getId());

        expireJob.execute();

        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        CartEventLogDO abandonedLog = logs.stream()
                .filter(l -> CartEventTypeEnum.CHECKOUT_ABANDONED.getCode().equals(l.getEventType()))
                .filter(l -> "SYSTEM".equals(l.getOperatorRole()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No CHECKOUT_ABANDONED event log with SYSTEM operator found"));
        assertThat(abandonedLog.getOperatorUserId()).isEqualTo(0L);
        assertThat(abandonedLog.getOperatorRole()).isEqualTo("SYSTEM");
    }

    @Test
    void doNotExpireNonInitiatedSession() {
        // Create and pay a session (PAID status)
        CheckoutSessionVO initiated = createInitiatedSession(5004L);
        checkoutService.paySession(initiated.getSessionToken(), new com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutPayReqVO());

        // Set expire_time to past (but session is PAID, not INITIATED)
        setExpireTimeToPast(initiated.getId());

        expireJob.execute();

        CheckoutSessionDO session = checkoutSessionMapper.selectById(initiated.getId());
        assertThat(session.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
    }

    @Test
    void doNotExpireRecentSession() {
        CheckoutSessionVO initiated = createInitiatedSession(5005L);
        // expire_time is in the future (5 min from now per PRD)

        expireJob.execute();

        CheckoutSessionDO session = checkoutSessionMapper.selectById(initiated.getId());
        assertThat(session.getStatus()).isEqualTo(CheckoutStatusEnum.INITIATED.getCode());
    }

    @Test
    void crossTenantIsolation_jobScansAllTenants() {
        // Create expired session in tenant 1
        TenantContextHolder.setTenantId(1L);
        CheckoutSessionVO session1 = createInitiatedSession(5006L);
        setExpireTimeToPast(session1.getId());

        // Create expired session in tenant 2
        TenantContextHolder.setTenantId(2L);
        CheckoutSessionVO session2 = createInitiatedSession(5007L);
        setExpireTimeToPast(session2.getId());

        // Run job without tenant context (simulates scheduled job)
        TenantContextHolder.clear();

        expireJob.execute();

        // Both tenants' sessions should be expired
        TenantContextHolder.setTenantId(1L);
        CheckoutSessionDO result1 = checkoutSessionMapper.selectById(session1.getId());
        assertThat(result1.getStatus()).isEqualTo(CheckoutStatusEnum.EXPIRED.getCode());

        TenantContextHolder.setTenantId(2L);
        CheckoutSessionDO result2 = checkoutSessionMapper.selectById(session2.getId());
        assertThat(result2.getStatus()).isEqualTo(CheckoutStatusEnum.EXPIRED.getCode());
    }
}
