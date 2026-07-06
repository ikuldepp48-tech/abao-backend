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
 * Order expire job test.
 *
 * <p>Tests that PENDING orders older than 15 minutes are expired.
 * Tests call job methods directly (not via @Scheduled).
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:order_expire_job_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OrderExpireJobTest {

    @Autowired
    private OrderExpireJob expireJob;
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

    @Test
    void expirePendingOrderOlderThan15Minutes() {
        Long orderId = createOrder();

        // Manually set create_time to 20 minutes ago to simulate timeout
        OrderDO order = orderMapper.selectById(orderId);
        order.setCreateTime(LocalDateTime.now().minusMinutes(20));
        orderMapper.updateById(order);

        // Run expire job
        expireJob.expirePendingOrders();

        // Verify order is now EXPIRED
        OrderDO expired = orderMapper.selectById(orderId);
        assertThat(expired.getStatus()).isEqualTo(OrderStatusEnum.EXPIRED.getCode());
    }

    @Test
    void doNotExpireRecentPendingOrder() {
        Long orderId = createOrder();

        // Order was just created, should not be expired
        expireJob.expirePendingOrders();

        OrderDO order = orderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.PENDING.getCode());
    }

    @Test
    void expireWritesEventLogWithSystemOperator() {
        Long orderId = createOrder();

        OrderDO order = orderMapper.selectById(orderId);
        order.setCreateTime(LocalDateTime.now().minusMinutes(20));
        orderMapper.updateById(order);

        expireJob.expirePendingOrders();

        List<OrderEventLogDO> logs = eventLogMapper.selectList(OrderEventLogDO::getOrderId, orderId);
        OrderEventLogDO expireLog = logs.stream()
                .filter(l -> "EXPIRE".equals(l.getEventType()))
                .findFirst().orElseThrow();
        assertThat(expireLog.getBeforeStatus()).isEqualTo("PENDING");
        assertThat(expireLog.getAfterStatus()).isEqualTo("EXPIRED");
        assertThat(expireLog.getOperatorRole()).isEqualTo("SYSTEM");
    }

    @Test
    void doNotExpirePaidOrder() {
        Long orderId = createOrder();
        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("22.00"), null);

        // Set create_time to old, but status is PAID so should not be expired
        OrderDO order = orderMapper.selectById(orderId);
        order.setCreateTime(LocalDateTime.now().minusMinutes(20));
        orderMapper.updateById(order);

        expireJob.expirePendingOrders();

        OrderDO result = orderMapper.selectById(orderId);
        assertThat(result.getStatus()).isEqualTo(OrderStatusEnum.PAID.getCode());
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
