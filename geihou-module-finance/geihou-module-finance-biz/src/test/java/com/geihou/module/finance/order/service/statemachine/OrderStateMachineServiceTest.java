package com.geihou.module.finance.order.service.statemachine;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.order.enums.OrderStatusEnum;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderEventLogDO;
import com.geihou.module.finance.order.dal.mapper.OrderEventLogMapper;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
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
 * Order state machine service test.
 *
 * <p>Tests all legal transitions, illegal transitions, event log writing,
 * and the immutable transition whitelist.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:order_state_machine_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OrderStateMachineServiceTest {

    @Autowired
    private OrderStateMachineService stateMachineService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderEventLogMapper eventLogMapper;
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

    // --- Legal transitions ---

    @Test
    void pendingToPaidIsLegal() {
        assertThat(stateMachineService.isLegalTransition("PENDING", "PAID")).isTrue();
    }

    @Test
    void pendingToCancelledIsLegal() {
        assertThat(stateMachineService.isLegalTransition("PENDING", "CANCELLED")).isTrue();
    }

    @Test
    void pendingToExpiredIsLegal() {
        assertThat(stateMachineService.isLegalTransition("PENDING", "EXPIRED")).isTrue();
    }

    @Test
    void paidToAcceptedIsLegal() {
        assertThat(stateMachineService.isLegalTransition("PAID", "ACCEPTED")).isTrue();
    }

    @Test
    void acceptedToPreparingIsLegal() {
        assertThat(stateMachineService.isLegalTransition("ACCEPTED", "PREPARING")).isTrue();
    }

    @Test
    void preparingToReadyIsLegal() {
        assertThat(stateMachineService.isLegalTransition("PREPARING", "READY")).isTrue();
    }

    @Test
    void readyToDeliveredIsLegal() {
        assertThat(stateMachineService.isLegalTransition("READY", "DELIVERED")).isTrue();
    }

    @Test
    void readyToDeliveringIsLegal() {
        assertThat(stateMachineService.isLegalTransition("READY", "DELIVERING")).isTrue();
    }

    @Test
    void deliveringToDeliveredIsLegal() {
        assertThat(stateMachineService.isLegalTransition("DELIVERING", "DELIVERED")).isTrue();
    }

    @Test
    void deliveredToCompletedIsLegal() {
        assertThat(stateMachineService.isLegalTransition("DELIVERED", "COMPLETED")).isTrue();
    }

    // --- Illegal transitions ---

    @Test
    void pendingToCompletedIsIllegal() {
        assertThat(stateMachineService.isLegalTransition("PENDING", "COMPLETED")).isFalse();
    }

    @Test
    void paidToCancelledIsIllegal() {
        assertThat(stateMachineService.isLegalTransition("PAID", "CANCELLED")).isFalse();
    }

    @Test
    void pendingToReadyIsIllegal() {
        assertThat(stateMachineService.isLegalTransition("PENDING", "READY")).isFalse();
    }

    @Test
    void completedToPendingIsIllegal() {
        assertThat(stateMachineService.isLegalTransition("COMPLETED", "PENDING")).isFalse();
    }

    @Test
    void cancelledToPaidIsIllegal() {
        assertThat(stateMachineService.isLegalTransition("CANCELLED", "PAID")).isFalse();
    }

    @Test
    void expiredToPaidIsIllegal() {
        assertThat(stateMachineService.isLegalTransition("EXPIRED", "PAID")).isFalse();
    }

    @Test
    void deliveredToPendingIsIllegal() {
        assertThat(stateMachineService.isLegalTransition("DELIVERED", "PENDING")).isFalse();
    }

    // --- Terminal states have no outgoing transitions ---

    @Test
    void cancelledIsTerminalState() {
        assertThat(stateMachineService.isLegalTransition("CANCELLED", "PAID")).isFalse();
        assertThat(stateMachineService.isLegalTransition("CANCELLED", "COMPLETED")).isFalse();
    }

    @Test
    void expiredIsTerminalState() {
        assertThat(stateMachineService.isLegalTransition("EXPIRED", "PAID")).isFalse();
        assertThat(stateMachineService.isLegalTransition("EXPIRED", "CANCELLED")).isFalse();
    }

    // --- Transition execution with event log ---

    @Test
    void transitionWritesEventLog() {
        Long orderId = createOrder();
        stateMachineService.transition(orderId, OrderStatusEnum.CANCELLED.getCode(), null, "CUSTOMER", "{\"reason\":\"test\"}");

        List<OrderEventLogDO> logs = eventLogMapper.selectList(OrderEventLogDO::getOrderId, orderId);
        assertThat(logs).hasSize(2); // CREATE + CANCEL

        OrderEventLogDO cancelLog = logs.stream()
                .filter(l -> "CANCEL".equals(l.getEventType()))
                .findFirst().orElseThrow();
        assertThat(cancelLog.getBeforeStatus()).isEqualTo("PENDING");
        assertThat(cancelLog.getAfterStatus()).isEqualTo("CANCELLED");
        assertThat(cancelLog.getOperatorRole()).isEqualTo("CUSTOMER");
        assertThat(cancelLog.getPayload()).isEqualTo("{\"reason\":\"test\"}");
    }

    @Test
    void transitionRejectsIllegalAndThrowsException() {
        Long orderId = createOrder();
        assertThatThrownBy(() -> stateMachineService.transition(
                orderId, OrderStatusEnum.COMPLETED.getCode(), null, "STAFF", "{}"))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void transitionRejectsAlreadyPaidOrder() {
        Long orderId = createOrder();
        // First transition to PAID via markOrderPaid
        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("22.00"), null);

        // Try to cancel PAID order — should fail
        assertThatThrownBy(() -> stateMachineService.transition(
                orderId, OrderStatusEnum.CANCELLED.getCode(), null, "CUSTOMER", "{}"))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void transitionToExpiredFromPendingSucceeds() {
        Long orderId = createOrder();
        OrderDO result = stateMachineService.transition(
                orderId, OrderStatusEnum.EXPIRED.getCode(), null, "SYSTEM", "{}");
        assertThat(result.getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void fullHappyPathTransition() {
        Long orderId = createOrder();

        // PENDING → PAID
        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("22.00"), null);

        // PAID → ACCEPTED
        stateMachineService.transition(orderId, "ACCEPTED", null, "STAFF", "{}");

        // ACCEPTED → PREPARING
        stateMachineService.transition(orderId, "PREPARING", null, "STAFF", "{}");

        // PREPARING → READY
        stateMachineService.transition(orderId, "READY", null, "STAFF", "{}");

        // READY → DELIVERED
        stateMachineService.transition(orderId, "DELIVERED", null, "STAFF", "{}");

        // DELIVERED → COMPLETED
        OrderDO result = stateMachineService.transition(orderId, "COMPLETED", null, "SYSTEM", "{}");
        assertThat(result.getStatus()).isEqualTo("COMPLETED");

        // Verify event log count: CREATE + PAY + KITCHEN_IN + KITCHEN_IN + READY + DELIVER + COMPLETE = 7
        List<OrderEventLogDO> logs = eventLogMapper.selectList(OrderEventLogDO::getOrderId, orderId);
        assertThat(logs).hasSize(7);
    }

    @Test
    void eventLogPayloadIsValidJson() {
        Long orderId = createOrder();
        stateMachineService.transition(orderId, "CANCELLED", null, "CUSTOMER", "{\"reason\":\"test\"}");

        List<OrderEventLogDO> logs = eventLogMapper.selectList(OrderEventLogDO::getOrderId, orderId);
        OrderEventLogDO cancelLog = logs.stream()
                .filter(l -> "CANCEL".equals(l.getEventType()))
                .findFirst().orElseThrow();
        // Payload should be valid JSON (starts with { and ends with })
        assertThat(cancelLog.getPayload()).startsWith("{").endsWith("}");
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
