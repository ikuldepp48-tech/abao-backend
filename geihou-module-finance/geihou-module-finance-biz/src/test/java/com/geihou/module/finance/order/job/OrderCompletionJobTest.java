package com.geihou.module.finance.order.job;

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
import com.geihou.module.finance.order.service.OrderService;
import com.geihou.module.finance.order.service.statemachine.OrderStateMachineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Order completion job test.
 *
 * <p>Tests that DELIVERED orders older than 24 hours are completed.
 * Tests call job methods directly (not via @Scheduled).
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:order_completion_job_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OrderCompletionJobTest {

    @Autowired
    private OrderCompletionJob completionJob;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderStateMachineService stateMachineService;
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

    @Test
    void completeDeliveredOrderOlderThan24Hours() {
        Long orderId = createAndDeliverOrder();

        // Set delivered_time to 25 hours ago
        OrderDO order = orderMapper.selectById(orderId);
        order.setDeliveredTime(LocalDateTime.now().minusHours(25));
        orderMapper.updateById(order);

        completionJob.completeDeliveredOrders();

        OrderDO result = orderMapper.selectById(orderId);
        assertThat(result.getStatus()).isEqualTo(OrderStatusEnum.COMPLETED.getCode());
    }

    @Test
    void doNotCompleteRecentDeliveredOrder() {
        Long orderId = createAndDeliverOrder();

        // Just delivered, should not be completed
        completionJob.completeDeliveredOrders();

        OrderDO result = orderMapper.selectById(orderId);
        assertThat(result.getStatus()).isEqualTo(OrderStatusEnum.DELIVERED.getCode());
    }

    @Test
    void completionWritesEventLogWithSystemOperator() {
        Long orderId = createAndDeliverOrder();

        OrderDO order = orderMapper.selectById(orderId);
        order.setDeliveredTime(LocalDateTime.now().minusHours(25));
        orderMapper.updateById(order);

        completionJob.completeDeliveredOrders();

        List<OrderEventLogDO> logs = eventLogMapper.selectList(OrderEventLogDO::getOrderId, orderId);
        OrderEventLogDO completeLog = logs.stream()
                .filter(l -> "COMPLETE".equals(l.getEventType()))
                .findFirst().orElseThrow();
        assertThat(completeLog.getBeforeStatus()).isEqualTo("DELIVERED");
        assertThat(completeLog.getAfterStatus()).isEqualTo("COMPLETED");
        assertThat(completeLog.getOperatorRole()).isEqualTo("SYSTEM");
    }

    private Long createAndDeliverOrder() {
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

        // Transition through full lifecycle to DELIVERED
        orderService.markOrderPaid(resp.getOrderId(), "CASH", new BigDecimal("22.00"), null);
        stateMachineService.transition(resp.getOrderId(), "ACCEPTED", null, "STAFF", "{}");
        stateMachineService.transition(resp.getOrderId(), "PREPARING", null, "STAFF", "{}");
        stateMachineService.transition(resp.getOrderId(), "READY", null, "STAFF", "{}");
        stateMachineService.transition(resp.getOrderId(), "DELIVERED", null, "STAFF", "{}");

        return resp.getOrderId();
    }
}
