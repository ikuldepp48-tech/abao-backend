package com.geihou.module.finance.order.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderItemDO;
import com.geihou.module.finance.order.dal.mapper.OrderItemMapper;
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
 * Order tenant isolation test.
 *
 * <p>Verifies that tenant A cannot query orders from tenant B.
 * Verifies that order_no uniqueness is per-tenant (same order_no in different tenants is allowed).
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:tenant_iso_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OrderTenantIsolationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        OrderTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void tenantACannotQueryOrderOfTenantB() {
        // Create order as tenant A
        TenantContextHolder.setTenantId(1L);
        OrderCreateReqVO req = buildCreateReq();
        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        // Switch to tenant B
        TenantContextHolder.setTenantId(2L);

        // Querying tenant A's order should fail
        assertThatThrownBy(() -> orderService.getOrder(resp.getOrderId()))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void tenantACannotSeeTenantBOrderItems() {
        // Create order as tenant A
        TenantContextHolder.setTenantId(1L);
        OrderCreateReqVO req = buildCreateReq();
        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        // Switch to tenant B
        TenantContextHolder.setTenantId(2L);

        // Querying items should return empty (tenant filter)
        List<OrderItemDO> items = orderItemMapper.selectList(
                OrderItemDO::getOrderId, resp.getOrderId());
        assertThat(items).isEmpty();
    }

    @Test
    void sameIdempotentKeyInDifferentTenantsCreatesDifferentOrders() {
        String key = UUID.randomUUID().toString();

        // Create order as tenant A
        TenantContextHolder.setTenantId(1L);
        OrderCreateReqVO req1 = buildCreateReq();
        OrderCreateRespVO resp1 = orderService.createOrder(req1, key);

        // Create order as tenant B with same key
        TenantContextHolder.setTenantId(2L);
        OrderCreateReqVO req2 = buildCreateReq();
        OrderCreateRespVO resp2 = orderService.createOrder(req2, key);

        // Should be different orders
        assertThat(resp1.getOrderId()).isNotEqualTo(resp2.getOrderId());
    }

    @Test
    void orderTenantIdMatchesCreatingTenant() {
        TenantContextHolder.setTenantId(1L);
        OrderCreateReqVO req = buildCreateReq();
        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        OrderDO order = orderMapper.selectById(resp.getOrderId());
        assertThat(order.getTenantId()).isEqualTo(1L);
    }

    @Test
    void orderItemsTenantIdMatchesCreatingTenant() {
        TenantContextHolder.setTenantId(1L);
        OrderCreateReqVO req = buildCreateReq();
        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        List<OrderItemDO> items = orderItemMapper.selectList(
                OrderItemDO::getOrderId, resp.getOrderId());
        for (OrderItemDO item : items) {
            assertThat(item.getTenantId()).isEqualTo(1L);
        }
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
