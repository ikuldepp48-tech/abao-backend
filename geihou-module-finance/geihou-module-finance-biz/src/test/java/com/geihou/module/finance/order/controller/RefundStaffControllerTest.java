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
 * Staff refund controller test (G1-01C).
 *
 * <p>Tests staff execute-refund bridge endpoint via direct service calls.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:refund_staff_ctrl_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class RefundStaffControllerTest {

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
    void staffExecuteRefundCompletesRefundAndOrder() {
        Long orderId = createCompletedOrder();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("22.00"),
                "CUSTOMER_REQUEST", "Full refund",
                null, null, "CUSTOMER");

        OrderRefundDO executed = refundService.executeRefund(refund.getId(), 888L, "EXT_RFD_123");

        assertThat(executed.getStatus()).isEqualTo(RefundStatusEnum.REFUNDED.getCode());
        assertThat(executed.getExternalRefundNo()).isEqualTo("EXT_RFD_123");

        OrderDO order = orderService.getOrder(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.REFUNDED.getCode());
        assertThat(order.getRefundAmount()).isEqualByComparingTo(new BigDecimal("22.00"));
    }

    @Test
    void staffExecuteRefundWithoutExternalNo() {
        Long orderId = createCompletedOrder();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("22.00"),
                "CUSTOMER_REQUEST", "No external no",
                null, null, "CUSTOMER");

        OrderRefundDO executed = refundService.executeRefund(refund.getId(), 888L, null);

        assertThat(executed.getStatus()).isEqualTo(RefundStatusEnum.REFUNDED.getCode());
        assertThat(executed.getRefundTime()).isNotNull();
    }

    private Long createCompletedOrder() {
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
        Long orderId = resp.getOrderId();

        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("22.00"), null);
        orderService.acceptOrder(orderId);
        stateMachineService.transition(orderId, "READY", null, "STAFF", "{}");
        stateMachineService.transition(orderId, "DELIVERED", null, "STAFF", "{}");
        stateMachineService.transition(orderId, "COMPLETED", null, "SYSTEM", "{}");
        return orderId;
    }
}
