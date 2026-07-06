package com.geihou.module.finance.checkout.controller;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import com.geihou.module.finance.cart.service.CartService;
import com.geihou.module.finance.checkout.CheckoutToOrderTestConfig;
import com.geihou.module.finance.checkout.CheckoutToOrderTestSchemaInitializer;
import com.geihou.module.finance.checkout.controller.app.customer.CustomerCheckoutController;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutInitiateReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutPayReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutSessionVO;
import com.geihou.common.pojo.CommonResult;
import com.geihou.module.finance.checkout.framework.CheckoutStatusEnum;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkout controller integration test.
 *
 * <p>Tests the 5 customer checkout endpoints through the controller layer, including
 * G1-04D auto-conversion and explicit /convert endpoint.
 */
@SpringBootTest(
        classes = CheckoutToOrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:checkout_controller_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CheckoutControllerTest {

    @Autowired
    private CustomerCheckoutController checkoutController;
    @Autowired
    private CartService cartService;
    @Autowired
    private OrderMapper orderMapper;
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

    @Test
    void initiateEndpointReturnsSession() {
        // Create cart with items
        CartAddItemReqVO cartReq = new CartAddItemReqVO();
        cartReq.setSkuId(1001L);
        cartReq.setQuantity(2);
        cartService.addItem(7001L, 1L, "DINE_IN", cartReq);

        CheckoutInitiateReqVO req = new CheckoutInitiateReqVO();
        req.setCustomerUserId(7001L);
        req.setShopId(1L);
        req.setChannel("DINE_IN");
        req.setIdempotentKey("idem-ctrl-7001");

        CommonResult<CheckoutSessionVO> result = checkoutController.initiateCheckout(req);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getStatus()).isEqualTo(CheckoutStatusEnum.INITIATED.getCode());
        assertThat(result.getData().getSessionToken()).isNotBlank();
    }

    @Test
    void queryEndpointReturnsSession() {
        CartAddItemReqVO cartReq = new CartAddItemReqVO();
        cartReq.setSkuId(1001L);
        cartReq.setQuantity(1);
        cartService.addItem(7002L, 1L, "DINE_IN", cartReq);

        CheckoutInitiateReqVO req = new CheckoutInitiateReqVO();
        req.setCustomerUserId(7002L);
        req.setShopId(1L);
        req.setChannel("DINE_IN");
        req.setIdempotentKey("idem-ctrl-7002");

        CommonResult<CheckoutSessionVO> initiated = checkoutController.initiateCheckout(req);
        CommonResult<CheckoutSessionVO> queried = checkoutController.querySession(initiated.getData().getSessionToken());

        assertThat(queried.getCode()).isEqualTo(0);
        assertThat(queried.getData().getId()).isEqualTo(initiated.getData().getId());
    }

    @Test
    void cancelEndpointReturnsAbandoned() {
        CartAddItemReqVO cartReq = new CartAddItemReqVO();
        cartReq.setSkuId(1001L);
        cartReq.setQuantity(1);
        cartService.addItem(7003L, 1L, "DINE_IN", cartReq);

        CheckoutInitiateReqVO req = new CheckoutInitiateReqVO();
        req.setCustomerUserId(7003L);
        req.setShopId(1L);
        req.setChannel("DINE_IN");
        req.setIdempotentKey("idem-ctrl-7003");

        CommonResult<CheckoutSessionVO> initiated = checkoutController.initiateCheckout(req);
        CommonResult<CheckoutSessionVO> cancelled = checkoutController.cancelSession(initiated.getData().getSessionToken());

        assertThat(cancelled.getCode()).isEqualTo(0);
        assertThat(cancelled.getData().getStatus()).isEqualTo(CheckoutStatusEnum.ABANDONED.getCode());
    }

    @Test
    void payEndpointReturnsPaid() {
        CartAddItemReqVO cartReq = new CartAddItemReqVO();
        cartReq.setSkuId(1001L);
        cartReq.setQuantity(1);
        cartService.addItem(7004L, 1L, "DINE_IN", cartReq);

        CheckoutInitiateReqVO req = new CheckoutInitiateReqVO();
        req.setCustomerUserId(7004L);
        req.setShopId(1L);
        req.setChannel("DINE_IN");
        req.setIdempotentKey("idem-ctrl-7004");

        CommonResult<CheckoutSessionVO> initiated = checkoutController.initiateCheckout(req);

        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");

        CommonResult<CheckoutSessionVO> paid = checkoutController.paySession(initiated.getData().getSessionToken(), payReq);

        assertThat(paid.getCode()).isEqualTo(0);
        assertThat(paid.getData().getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        // G1-04D: auto-conversion is always on — orderId should be non-null after pay
        assertThat(paid.getData().getOrderId()).isNotNull();
        // Verify order exists in DB
        OrderDO order = orderMapper.selectById(paid.getData().getOrderId());
        assertThat(order).isNotNull();
    }

    @Test
    void payAutoConvertsToOrder_andOrderIdInResponse() {
        CartAddItemReqVO cartReq = new CartAddItemReqVO();
        cartReq.setSkuId(1001L);
        cartReq.setQuantity(2);
        cartService.addItem(7010L, 1L, "DINE_IN", cartReq);

        CheckoutInitiateReqVO req = new CheckoutInitiateReqVO();
        req.setCustomerUserId(7010L);
        req.setShopId(1L);
        req.setChannel("DINE_IN");
        req.setIdempotentKey("idem-ctrl-7010");

        CommonResult<CheckoutSessionVO> initiated = checkoutController.initiateCheckout(req);

        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");

        CommonResult<CheckoutSessionVO> paid = checkoutController.paySession(
                initiated.getData().getSessionToken(), payReq);

        // Pay returns PAID + orderId non-null (auto-conversion succeeded)
        assertThat(paid.getCode()).isEqualTo(0);
        assertThat(paid.getData().getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        assertThat(paid.getData().getOrderId()).isNotNull();

        // Verify order exists in DB
        OrderDO order = orderMapper.selectById(paid.getData().getOrderId());
        assertThat(order).isNotNull();
        assertThat(order.getCustomerUserId()).isEqualTo(7010L);
        assertThat(order.getStatus()).isEqualTo("PAID");
    }

    @Test
    void payTwiceOnSameSession_returnsExistingOrderId() {
        CartAddItemReqVO cartReq = new CartAddItemReqVO();
        cartReq.setSkuId(1001L);
        cartReq.setQuantity(1);
        cartService.addItem(7011L, 1L, "DINE_IN", cartReq);

        CheckoutInitiateReqVO req = new CheckoutInitiateReqVO();
        req.setCustomerUserId(7011L);
        req.setShopId(1L);
        req.setChannel("DINE_IN");
        req.setIdempotentKey("idem-ctrl-7011");

        CommonResult<CheckoutSessionVO> initiated = checkoutController.initiateCheckout(req);

        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");

        CommonResult<CheckoutSessionVO> firstPay = checkoutController.paySession(
                initiated.getData().getSessionToken(), payReq);
        assertThat(firstPay.getCode()).isEqualTo(0);
        assertThat(firstPay.getData().getOrderId()).isNotNull();
        Long firstOrderId = firstPay.getData().getOrderId();

        // Re-pay on already PAID + converted session — should be idempotent
        CommonResult<CheckoutSessionVO> secondPay = checkoutController.paySession(
                initiated.getData().getSessionToken(), payReq);
        assertThat(secondPay.getCode()).isEqualTo(0);
        assertThat(secondPay.getData().getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        assertThat(secondPay.getData().getOrderId()).isEqualTo(firstOrderId);

        // No duplicate order
        Long orderCount = orderMapper.selectCount(
                com.geihou.module.finance.order.dal.dataobject.OrderDO::getCustomerUserId, 7011L);
        assertThat(orderCount).isEqualTo(1);
    }

    @Test
    void convertEndpoint_convertsPaidSession() {
        CartAddItemReqVO cartReq = new CartAddItemReqVO();
        cartReq.setSkuId(1001L);
        cartReq.setQuantity(1);
        cartService.addItem(7012L, 1L, "DINE_IN", cartReq);

        CheckoutInitiateReqVO req = new CheckoutInitiateReqVO();
        req.setCustomerUserId(7012L);
        req.setShopId(1L);
        req.setChannel("DINE_IN");
        req.setIdempotentKey("idem-ctrl-7012");

        CommonResult<CheckoutSessionVO> initiated = checkoutController.initiateCheckout(req);

        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");
        checkoutController.paySession(initiated.getData().getSessionToken(), payReq);

        // Pay already auto-converted, so orderId is set. Convert should be idempotent.
        CommonResult<CheckoutSessionVO> result = checkoutController.convertToOrder(
                initiated.getData().getSessionToken());
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        assertThat(result.getData().getOrderId()).isNotNull();
    }

    @Test
    void convertEndpoint_idempotent_repeatReturnsSameOrder() {
        CartAddItemReqVO cartReq = new CartAddItemReqVO();
        cartReq.setSkuId(1001L);
        cartReq.setQuantity(1);
        cartService.addItem(7013L, 1L, "DINE_IN", cartReq);

        CheckoutInitiateReqVO req = new CheckoutInitiateReqVO();
        req.setCustomerUserId(7013L);
        req.setShopId(1L);
        req.setChannel("DINE_IN");
        req.setIdempotentKey("idem-ctrl-7013");

        CommonResult<CheckoutSessionVO> initiated = checkoutController.initiateCheckout(req);

        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");
        checkoutController.paySession(initiated.getData().getSessionToken(), payReq);

        CommonResult<CheckoutSessionVO> first = checkoutController.convertToOrder(
                initiated.getData().getSessionToken());
        CommonResult<CheckoutSessionVO> second = checkoutController.convertToOrder(
                initiated.getData().getSessionToken());

        assertThat(first.getCode()).isEqualTo(0);
        assertThat(second.getCode()).isEqualTo(0);
        assertThat(first.getData().getOrderId()).isEqualTo(second.getData().getOrderId());
    }

    @Test
    void convertEndpoint_rejectsNonPaidSession() {
        CartAddItemReqVO cartReq = new CartAddItemReqVO();
        cartReq.setSkuId(1001L);
        cartReq.setQuantity(1);
        cartService.addItem(7014L, 1L, "DINE_IN", cartReq);

        CheckoutInitiateReqVO req = new CheckoutInitiateReqVO();
        req.setCustomerUserId(7014L);
        req.setShopId(1L);
        req.setChannel("DINE_IN");
        req.setIdempotentKey("idem-ctrl-7014");

        CommonResult<CheckoutSessionVO> initiated = checkoutController.initiateCheckout(req);

        // Convert INITIATED session — should fail
        CommonResult<CheckoutSessionVO> result = checkoutController.convertToOrder(
                initiated.getData().getSessionToken());
        assertThat(result.getCode()).isEqualTo(1004010); // CHECKOUT_DUPLICATE
    }

    @Test
    void convertEndpoint_rejectsCrossTenant() {
        CartAddItemReqVO cartReq = new CartAddItemReqVO();
        cartReq.setSkuId(1001L);
        cartReq.setQuantity(1);
        cartService.addItem(7015L, 1L, "DINE_IN", cartReq);

        CheckoutInitiateReqVO req = new CheckoutInitiateReqVO();
        req.setCustomerUserId(7015L);
        req.setShopId(1L);
        req.setChannel("DINE_IN");
        req.setIdempotentKey("idem-ctrl-7015");

        CommonResult<CheckoutSessionVO> initiated = checkoutController.initiateCheckout(req);

        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");
        checkoutController.paySession(initiated.getData().getSessionToken(), payReq);

        // Switch to different tenant
        TenantContextHolder.setTenantId(2L);

        CommonResult<CheckoutSessionVO> result = checkoutController.convertToOrder(
                initiated.getData().getSessionToken());
        assertThat(result.getCode()).isEqualTo(1004020); // CART_NOT_FOUND
    }

    @Test
    void convertEndpoint_rejectsInvalidToken() {
        CommonResult<CheckoutSessionVO> result = checkoutController.convertToOrder("nonexistent-token");
        assertThat(result.getCode()).isEqualTo(1004020); // CART_NOT_FOUND
    }

    @Test
    void couponRejectionReturnsError() {
        CartAddItemReqVO cartReq = new CartAddItemReqVO();
        cartReq.setSkuId(1001L);
        cartReq.setQuantity(1);
        cartService.addItem(7005L, 1L, "DINE_IN", cartReq);

        CheckoutInitiateReqVO req = new CheckoutInitiateReqVO();
        req.setCustomerUserId(7005L);
        req.setShopId(1L);
        req.setChannel("DINE_IN");
        req.setIdempotentKey("idem-ctrl-7005");
        req.setCouponIds(List.of("coupon-1"));

        CommonResult<CheckoutSessionVO> result = checkoutController.initiateCheckout(req);

        assertThat(result.getCode()).isEqualTo(1004012); // COUPON_INVALID
    }

    @Test
    void payFailureReturnsError() {
        CartAddItemReqVO cartReq = new CartAddItemReqVO();
        cartReq.setSkuId(1001L);
        cartReq.setQuantity(1);
        cartService.addItem(7006L, 1L, "DINE_IN", cartReq);

        CheckoutInitiateReqVO req = new CheckoutInitiateReqVO();
        req.setCustomerUserId(7006L);
        req.setShopId(1L);
        req.setChannel("DINE_IN");
        req.setIdempotentKey("idem-ctrl-7006");

        CommonResult<CheckoutSessionVO> initiated = checkoutController.initiateCheckout(req);

        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");
        payReq.setSimulateFail("SIMULATE_FAIL");

        CommonResult<CheckoutSessionVO> result = checkoutController.paySession(initiated.getData().getSessionToken(), payReq);

        assertThat(result.getCode()).isEqualTo(1004013); // PAYMENT_FAILED
    }
}
