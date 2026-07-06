package com.geihou.module.finance.order.controller;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.order.enums.OrderStatusEnum;
import com.geihou.module.finance.api.order.enums.RefundStatusEnum;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderRefundDO;
import com.geihou.module.finance.order.service.OrderService;
import com.geihou.module.finance.order.service.refund.RefundService;
import com.geihou.module.finance.order.service.statemachine.OrderStateMachineService;
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

/**
 * Admin refund controller test (G1-01C).
 *
 * <p>Tests admin/owner refund approve and reject endpoints via direct service calls.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:refund_admin_ctrl_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class RefundAdminControllerTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private RefundService refundService;
    @Autowired
    private OrderStateMachineService stateMachineService;
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
    void adminApproveRefund() {
        Long orderId = createCompletedOrderLargeAmount();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("600.00"),
                "CUSTOMER_REQUEST", "Need approval",
                null, null, "CUSTOMER");
        assertThat(refund.getStatus()).isEqualTo(RefundStatusEnum.PENDING_REVIEW.getCode());

        OrderRefundDO approved = refundService.approveRefund(refund.getId(), 999L, "Approved");

        assertThat(approved.getStatus()).isEqualTo(RefundStatusEnum.REFUNDING.getCode());
        assertThat(approved.getApproverUserId()).isEqualTo(999L);
        assertThat(approved.getApproveRemark()).isEqualTo("Approved");
    }

    @Test
    void adminRejectRefundRollsBackOrder() {
        Long orderId = createCompletedOrderLargeAmount();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("600.00"),
                "CUSTOMER_REQUEST", "Will be rejected",
                null, null, "CUSTOMER");

        OrderRefundDO rejected = refundService.rejectRefund(refund.getId(), 999L, "Invalid reason");

        assertThat(rejected.getStatus()).isEqualTo(RefundStatusEnum.REJECTED.getCode());

        // D-R2: Order should be back to COMPLETED
        OrderDO order = orderService.getOrder(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.COMPLETED.getCode());
    }

    @Test
    void adminRejectThenCanRefundAgain() {
        Long orderId = createCompletedOrderLargeAmount();
        OrderRefundDO refund1 = refundService.createRefund(
                orderId, "FULL", new BigDecimal("600.00"),
                "CUSTOMER_REQUEST", "First attempt",
                null, null, "CUSTOMER");

        refundService.rejectRefund(refund1.getId(), 999L, "Rejected");

        // Order is back to COMPLETED, can create another refund
        OrderDO order = orderService.getOrder(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.COMPLETED.getCode());

        OrderRefundDO refund2 = refundService.createRefund(
                orderId, "FULL", new BigDecimal("600.00"),
                "QUALITY_ISSUE", "Second attempt",
                null, null, "CUSTOMER");

        assertThat(refund2.getStatus()).isEqualTo(RefundStatusEnum.PENDING_REVIEW.getCode());
        assertThat(orderService.getOrder(orderId).getStatus()).isEqualTo(OrderStatusEnum.REFUNDING.getCode());
    }

    private Long createCompletedOrderLargeAmount() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item1 = new OrderItemReqVO();
        item1.setSkuId(1001L);
        item1.setQuantity(new BigDecimal("60")); // 60 × 12.00 = 720.00

        req.setItems(List.of(item1));
        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());
        Long orderId = resp.getOrderId();

        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("720.00"), null);
        orderService.acceptOrder(orderId);
        stateMachineService.transition(orderId, "READY", null, "STAFF", "{}");
        stateMachineService.transition(orderId, "DELIVERED", null, "STAFF", "{}");
        stateMachineService.transition(orderId, "COMPLETED", null, "SYSTEM", "{}");
        return orderId;
    }
}
