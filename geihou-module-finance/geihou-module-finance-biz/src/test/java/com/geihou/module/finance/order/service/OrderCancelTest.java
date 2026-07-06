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
import com.geihou.module.finance.order.framework.OrderBusinessException;
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
 * Order cancel test.
 *
 * <p>Tests customer cancel: only PENDING can be cancelled,
 * PAID and later states cannot be cancelled directly.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:order_cancel_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OrderCancelTest {

    @Autowired
    private OrderService orderService;
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
    void cancelPendingOrderSucceeds() {
        Long orderId = createOrder();
        orderService.cancelOrder(orderId, "Customer changed mind");

        OrderDO order = orderService.getOrder(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
        assertThat(order.getCancelReason()).isEqualTo("Customer changed mind");
        assertThat(order.getCancelledTime()).isNotNull();
    }

    @Test
    void cancelWritesEventLog() {
        Long orderId = createOrder();
        orderService.cancelOrder(orderId, "Test reason");

        List<OrderEventLogDO> logs = eventLogMapper.selectList(OrderEventLogDO::getOrderId, orderId);
        assertThat(logs).hasSize(2); // CREATE + CANCEL

        OrderEventLogDO cancelLog = logs.stream()
                .filter(l -> "CANCEL".equals(l.getEventType()))
                .findFirst().orElseThrow();
        assertThat(cancelLog.getBeforeStatus()).isEqualTo("PENDING");
        assertThat(cancelLog.getAfterStatus()).isEqualTo("CANCELLED");
        assertThat(cancelLog.getOperatorRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void cancelPaidOrderFails() {
        Long orderId = createOrder();
        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("22.00"), null);

        assertThatThrownBy(() -> orderService.cancelOrder(orderId, "Want to cancel"))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void cancelNonExistentOrderFails() {
        assertThatThrownBy(() -> orderService.cancelOrder(99999L, "No such order"))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void cancelWithNullReasonSucceeds() {
        Long orderId = createOrder();
        orderService.cancelOrder(orderId, null);

        OrderDO order = orderService.getOrder(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
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
