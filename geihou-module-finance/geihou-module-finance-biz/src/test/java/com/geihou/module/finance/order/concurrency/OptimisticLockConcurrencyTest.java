package com.geihou.module.finance.order.concurrency;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.order.enums.OrderStatusEnum;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.service.OrderService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optimistic lock concurrency test.
 *
 * <p>Tests that concurrent state transitions on the same order
 * result in only one success and the other failing with conflict.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:optimistic_lock_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OptimisticLockConcurrencyTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderStateMachineService stateMachineService;
    @Autowired
    private OrderMapper orderMapper;
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
    void concurrentTransitionsOnlyOneSucceeds() throws InterruptedException {
        Long orderId = createOrder();
        // Mark as paid so we can transition PAID → ACCEPTED concurrently
        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("22.00"), null);

        int threadCount = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContextHolder.setTenantId(1L);
                    stateMachineService.transition(
                            orderId, OrderStatusEnum.ACCEPTED.getCode(), null, "STAFF", "{}");
                    successCount.incrementAndGet();
                } catch (OrderBusinessException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    // Other exceptions
                    conflictCount.incrementAndGet();
                } finally {
                    TenantContextHolder.clear();
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Only one should succeed, the other should fail
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        // Verify order status is ACCEPTED
        OrderDO order = orderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.ACCEPTED.getCode());
    }

    @Test
    void versionFieldIncrementsAfterTransition() {
        Long orderId = createOrder();
        OrderDO before = orderMapper.selectById(orderId);
        int versionBefore = before.getVersion();

        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("22.00"), null);

        OrderDO after = orderMapper.selectById(orderId);
        assertThat(after.getVersion()).isGreaterThan(versionBefore);
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
