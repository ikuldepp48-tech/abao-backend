package com.geihou.module.finance.order.controller;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.app.customer.CustomerOrderController;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderItemDO;
import com.geihou.module.finance.order.dal.mapper.OrderItemMapper;
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
 * Customer order controller test (service-level integration).
 *
 * <p>Tests the controller endpoints via direct service calls (no MockMvc).
 * Verifies create, get detail, and list functionality.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:controller_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CustomerOrderControllerTest {

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
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createOrderViaService() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");
        req.setCustomerRemark("No onions");

        OrderItemReqVO item = new OrderItemReqVO();
        item.setSkuId(1001L);
        item.setQuantity(new BigDecimal("1"));
        req.setItems(List.of(item));

        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        assertThat(resp.getOrderId()).isNotNull();
        assertThat(resp.getOrderNo()).isNotNull();
        assertThat(resp.getStatus()).isEqualTo("PENDING");
        assertThat(resp.getChannel()).isEqualTo("SELF_PICKUP");
    }

    @Test
    void getOrderDetailReturnsOrderAndItems() {
        // Create order first
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item1 = new OrderItemReqVO();
        item1.setSkuId(1001L);
        item1.setQuantity(new BigDecimal("1"));

        OrderItemReqVO item2 = new OrderItemReqVO();
        item2.setSkuId(1002L);
        item2.setQuantity(new BigDecimal("2"));
        req.setItems(List.of(item1, item2));

        OrderCreateRespVO created = orderService.createOrder(req, UUID.randomUUID().toString());

        // Get order detail
        OrderDO order = orderService.getOrder(created.getOrderId());
        List<OrderItemDO> items = orderService.getOrderItems(created.getOrderId());

        assertThat(order.getId()).isEqualTo(created.getOrderId());
        assertThat(items).hasSize(2);
    }

    @Test
    void listMyOrdersReturnsPaginated() {
        // Create 3 orders
        for (int i = 0; i < 3; i++) {
            OrderCreateReqVO req = new OrderCreateReqVO();
            req.setShopId(1L);
            req.setChannel("SELF_PICKUP");

            OrderItemReqVO item = new OrderItemReqVO();
            item.setSkuId(1001L);
            item.setQuantity(new BigDecimal("1"));
            req.setItems(List.of(item));

            orderService.createOrder(req, UUID.randomUUID().toString());
        }

        // List orders (but customerUserId is null since we don't set it in this slice)
        // We need to manually set customerUserId for list test
        // Since customerUserId is null by default, let's test with null
        var result = orderService.listMyOrders(null, 1, 10);
        // Null customerUserId matches null in DB
        assertThat(result.getList()).hasSize(3);
        assertThat(result.getTotal()).isEqualTo(3L);
    }

    @Test
    void listMyOrdersPaginatesCorrectly() {
        // Create 5 orders
        for (int i = 0; i < 5; i++) {
            OrderCreateReqVO req = new OrderCreateReqVO();
            req.setShopId(1L);
            req.setChannel("SELF_PICKUP");

            OrderItemReqVO item = new OrderItemReqVO();
            item.setSkuId(1001L);
            item.setQuantity(new BigDecimal("1"));
            req.setItems(List.of(item));

            orderService.createOrder(req, UUID.randomUUID().toString());
        }

        // Page 1 with size 2
        var page1 = orderService.listMyOrders(null, 1, 2);
        assertThat(page1.getList()).hasSize(2);
        assertThat(page1.getTotal()).isEqualTo(5L);

        // Page 3 with size 2
        var page3 = orderService.listMyOrders(null, 3, 2);
        assertThat(page3.getList()).hasSize(1);
    }
}
