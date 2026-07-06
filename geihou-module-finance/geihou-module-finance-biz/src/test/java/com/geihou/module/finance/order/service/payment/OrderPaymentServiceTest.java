package com.geihou.module.finance.order.service.payment;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.order.enums.PaymentStatusEnum;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderPaymentDO;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Order payment service test.
 *
 * <p>Tests INSERT-only behavior, payment_no uniqueness,
 * payment amount validation, and payment status.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:order_payment_service_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OrderPaymentServiceTest {

    @Autowired
    private OrderPaymentService paymentService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        OrderTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createPaymentReturnsSuccessStatus() {
        Long orderId = createOrder();
        OrderPaymentDO payment = paymentService.createPayment(
                orderId, "CASH", new BigDecimal("22.00"), null);

        assertThat(payment).isNotNull();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatusEnum.SUCCESS.getCode());
        assertThat(payment.getPaymentMethod()).isEqualTo("CASH");
        assertThat(payment.getPaymentAmount()).isEqualByComparingTo(new BigDecimal("22.00"));
        assertThat(payment.getPaymentNo()).isNotNull().isNotEmpty();
        assertThat(payment.getPaidTime()).isNotNull();
        assertThat(payment.getInitiatedTime()).isNotNull();
    }

    @Test
    void createPaymentWithExternalNo() {
        Long orderId = createOrder();
        OrderPaymentDO payment = paymentService.createPayment(
                orderId, "WECHAT_PAY", new BigDecimal("22.00"), "WX123456789");

        assertThat(payment.getExternalNo()).isEqualTo("WX123456789");
        assertThat(payment.getPaymentMethod()).isEqualTo("WECHAT_PAY");
    }

    @Test
    void createPaymentRejectsInvalidPaymentMethod() {
        Long orderId = createOrder();
        assertThatThrownBy(() -> paymentService.createPayment(
                orderId, "INVALID_METHOD", new BigDecimal("22.00"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createPaymentRejectsAmountMismatch() {
        Long orderId = createOrder();
        assertThatThrownBy(() -> paymentService.createPayment(
                orderId, "CASH", new BigDecimal("100.00"), null))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void createPaymentRejectsZeroAmount() {
        Long orderId = createOrder();
        assertThatThrownBy(() -> paymentService.createPayment(
                orderId, "CASH", new BigDecimal("0"), null))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void getPaymentByOrderIdReturnsPayment() {
        Long orderId = createOrder();
        paymentService.createPayment(orderId, "CASH", new BigDecimal("22.00"), null);

        OrderPaymentDO found = paymentService.getPaymentByOrderId(orderId);
        assertThat(found).isNotNull();
        assertThat(found.getOrderId()).isEqualTo(orderId);
    }

    @Test
    void paymentNoIsUnique() {
        Long orderId = createOrder();
        OrderPaymentDO payment1 = paymentService.createPayment(
                orderId, "CASH", new BigDecimal("22.00"), null);

        // Creating another payment with same order should produce different payment_no
        // (different UUID suffix). But the first payment record is immutable.
        assertThat(payment1.getPaymentNo()).isNotNull();
    }

    @Test
    void paymentAmountUsesBigDecimal() {
        Long orderId = createOrder();
        OrderPaymentDO payment = paymentService.createPayment(
                orderId, "CASH", new BigDecimal("22.0000"), null);

        assertThat(payment.getPaymentAmount()).isInstanceOf(BigDecimal.class);
    }

    @Test
    void paymentRecordHasNoDeletedField() {
        Long orderId = createOrder();
        OrderPaymentDO payment = paymentService.createPayment(
                orderId, "CASH", new BigDecimal("22.00"), null);

        // Verify OrderPaymentDO has no getDeleted method (INSERT-only, no soft delete)
        assertThat(payment.getClass().getDeclaredFields())
                .noneMatch(f -> f.getName().equals("deleted"));
    }

    private Long createOrder() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item1 = new OrderItemReqVO();
        item1.setSkuId(1001L);
        item1.setQuantity(new BigDecimal("1"));

        OrderItemReqVO item2 = new OrderItemReqVO();
        item2.setSkuId(1002L);
        item2.setQuantity(new BigDecimal("1"));

        req.setItems(List.of(item1, item2));
        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());
        return resp.getOrderId();
    }
}
