package com.geihou.module.finance.order.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
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
 * Idempotent service test.
 *
 * <p>Tests that duplicate Idempotent-Key returns the same order,
 * and that processing state blocks concurrent requests.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:idempotent_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class IdempotentServiceTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private IdempotentService idempotentService;
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
    void duplicateIdempotentKeyReturnsSameOrder() {
        OrderCreateReqVO req = buildCreateReq();
        String key = UUID.randomUUID().toString();

        OrderCreateRespVO r1 = orderService.createOrder(req, key);
        OrderCreateRespVO r2 = orderService.createOrder(req, key);

        assertThat(r1.getOrderId()).isEqualTo(r2.getOrderId());
        assertThat(r1.getOrderNo()).isEqualTo(r2.getOrderNo());
        // Duplicate idempotent response must include non-null items matching the original
        assertThat(r2.getItems()).isNotNull();
        assertThat(r2.getItems()).hasSameSizeAs(r1.getItems());
        for (int i = 0; i < r1.getItems().size(); i++) {
            OrderCreateRespVO.OrderItemRespVO orig = r1.getItems().get(i);
            OrderCreateRespVO.OrderItemRespVO dup = r2.getItems().get(i);
            assertThat(dup.getSkuId()).isEqualTo(orig.getSkuId());
            assertThat(dup.getSkuName()).isEqualTo(orig.getSkuName());
            assertThat(dup.getUnitPrice()).isEqualByComparingTo(orig.getUnitPrice());
            assertThat(dup.getQuantity()).isEqualByComparingTo(orig.getQuantity());
            assertThat(dup.getItemTotal()).isEqualByComparingTo(orig.getItemTotal());
        }
    }

    @Test
    void differentIdempotentKeysCreateDifferentOrders() {
        OrderCreateReqVO req = buildCreateReq();

        OrderCreateRespVO r1 = orderService.createOrder(req, UUID.randomUUID().toString());
        OrderCreateRespVO r2 = orderService.createOrder(req, UUID.randomUUID().toString());

        assertThat(r1.getOrderId()).isNotEqualTo(r2.getOrderId());
    }

    @Test
    void tryAcquireReturnsNullForNewKey() {
        String key = UUID.randomUUID().toString();
        Long existingOrderId = idempotentService.tryAcquire(key);
        assertThat(existingOrderId).isNull();
    }

    @Test
    void tryAcquireReturnsOrderIdForSuccessKey() {
        String key = UUID.randomUUID().toString();
        idempotentService.tryAcquire(key);
        idempotentService.markSuccess(key, 12345L);

        Long orderId = idempotentService.tryAcquire(key);
        assertThat(orderId).isEqualTo(12345L);
    }

    @Test
    void tryAcquireThrowsForProcessingKey() {
        String key = UUID.randomUUID().toString();
        idempotentService.tryAcquire(key); // Sets to PROCESSING

        // Second try should throw
        assertThatThrownBy(() -> idempotentService.tryAcquire(key))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void failedKeyAllowsRetry() {
        String key = UUID.randomUUID().toString();
        idempotentService.tryAcquire(key); // PROCESSING
        idempotentService.markFailed(key); // FAILED

        // Retry should succeed (return null for new acquisition)
        Long orderId = idempotentService.tryAcquire(key);
        assertThat(orderId).isNull();
    }

    @Test
    void markSuccessUpdatesRecord() {
        String key = UUID.randomUUID().toString();
        idempotentService.tryAcquire(key);
        idempotentService.markSuccess(key, 99999L);

        Long orderId = idempotentService.tryAcquire(key);
        assertThat(orderId).isEqualTo(99999L);
    }

    @Test
    void markFailedUpdatesRecord() {
        String key = UUID.randomUUID().toString();
        idempotentService.tryAcquire(key);
        idempotentService.markFailed(key);

        // Should allow retry
        Long orderId = idempotentService.tryAcquire(key);
        assertThat(orderId).isNull();
    }

    private OrderCreateReqVO buildCreateReq() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item = new OrderItemReqVO();
        item.setSkuId(1001L);
        item.setQuantity(new BigDecimal("1"));
        req.setItems(List.of(item));
        return req;
    }
}
