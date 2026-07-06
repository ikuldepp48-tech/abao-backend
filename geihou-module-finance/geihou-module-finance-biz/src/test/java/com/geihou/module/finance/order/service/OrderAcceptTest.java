package com.geihou.module.finance.order.service;

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
import com.geihou.module.finance.order.framework.OrderErrorCodeConstants;
import com.geihou.module.finance.order.service.statemachine.OrderStateMachineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;

/**
 * Order accept test — verifies the atomic double-transition PAID→ACCEPTED→PREPARING.
 *
 * <p>Tests that:
 * <ul>
 *   <li>Happy path: acceptOrder transitions PAID order to PREPARING and writes 2 event logs</li>
 *   <li>Non-PAID order cannot be accepted</li>
 *   <li>Atomic rollback: if the second transition (ACCEPTED→PREPARING) fails,
 *       the first transition (PAID→ACCEPTED) is rolled back — order remains in PAID</li>
 * </ul>
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:order_accept_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OrderAcceptTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderEventLogMapper eventLogMapper;
    @Autowired
    private DataSource dataSource;

    @SpyBean
    private OrderStateMachineService stateMachineService;

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
    void acceptOrderTransitionsPaidToPreparing() {
        Long orderId = createOrder();
        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("22.00"), null);

        OrderDO result = orderService.acceptOrder(orderId);

        assertThat(result.getStatus()).isEqualTo(OrderStatusEnum.PREPARING.getCode());

        OrderDO order = orderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.PREPARING.getCode());
    }

    @Test
    void acceptOrderWritesTwoEventLogs() {
        Long orderId = createOrder();
        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("22.00"), null);

        orderService.acceptOrder(orderId);

        // Event logs: CREATE + PAY + KITCHEN_IN(ACCEPTED) + KITCHEN_IN(PREPARING) = 4
        List<OrderEventLogDO> logs = eventLogMapper.selectList(OrderEventLogDO::getOrderId, orderId);
        assertThat(logs).hasSize(4);

        List<OrderEventLogDO> kitchenLogs = logs.stream()
                .filter(l -> "KITCHEN_IN".equals(l.getEventType()))
                .toList();
        assertThat(kitchenLogs).hasSize(2);

        // First kitchen log: PAID → ACCEPTED
        OrderEventLogDO acceptedLog = kitchenLogs.get(0);
        assertThat(acceptedLog.getBeforeStatus()).isEqualTo(OrderStatusEnum.PAID.getCode());
        assertThat(acceptedLog.getAfterStatus()).isEqualTo(OrderStatusEnum.ACCEPTED.getCode());
        assertThat(acceptedLog.getOperatorRole()).isEqualTo("STAFF");

        // Second kitchen log: ACCEPTED → PREPARING
        OrderEventLogDO preparingLog = kitchenLogs.get(1);
        assertThat(preparingLog.getBeforeStatus()).isEqualTo(OrderStatusEnum.ACCEPTED.getCode());
        assertThat(preparingLog.getAfterStatus()).isEqualTo(OrderStatusEnum.PREPARING.getCode());
        assertThat(preparingLog.getOperatorRole()).isEqualTo("STAFF");
    }

    @Test
    void acceptOrderRejectsNonPaidOrder() {
        Long orderId = createOrder();
        // Order is still PENDING — accept should fail

        assertThatThrownBy(() -> orderService.acceptOrder(orderId))
                .isInstanceOf(OrderBusinessException.class);

        // Verify order is still PENDING (no partial transition)
        OrderDO order = orderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.PENDING.getCode());
    }

    @Test
    void acceptOrderAtomicRollbackWhenSecondTransitionFails() {
        Long orderId = createOrder();
        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("22.00"), null);

        // Stub the second transition (ACCEPTED→PREPARING) to throw
        doThrow(new OrderBusinessException(OrderErrorCodeConstants.INVALID_STATUS_TRANSFER,
                "forced failure for atomic rollback test"))
                .when(stateMachineService)
                .transition(eq(orderId), eq(OrderStatusEnum.PREPARING.getCode()),
                        any(), anyString(), anyString());

        // acceptOrder should throw because the second transition fails
        assertThatThrownBy(() -> orderService.acceptOrder(orderId))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessageContaining("forced failure");

        // Verify order is still in PAID state — the first transition (PAID→ACCEPTED)
        // was rolled back because both transitions are in a single transaction
        OrderDO order = orderMapper.selectById(orderId);
        assertThat(order.getStatus())
                .as("Order should remain PAID after atomic rollback — ACCEPTED transition must be undone")
                .isEqualTo(OrderStatusEnum.PAID.getCode());

        // Verify only PAY event log was written (no KITCHEN_IN logs persisted)
        List<OrderEventLogDO> logs = eventLogMapper.selectList(OrderEventLogDO::getOrderId, orderId);
        long kitchenInCount = logs.stream()
                .filter(l -> "KITCHEN_IN".equals(l.getEventType()))
                .count();
        assertThat(kitchenInCount)
                .as("No KITCHEN_IN event logs should persist after atomic rollback")
                .isZero();
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
