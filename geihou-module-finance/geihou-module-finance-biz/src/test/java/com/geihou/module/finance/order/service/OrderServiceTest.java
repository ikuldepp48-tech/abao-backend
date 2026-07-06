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
import com.geihou.module.finance.order.dal.dataobject.OrderItemDO;
import com.geihou.module.finance.order.dal.dataobject.OrderTableSessionDO;
import com.geihou.module.finance.order.dal.mapper.OrderEventLogMapper;
import com.geihou.module.finance.order.dal.mapper.OrderItemMapper;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.enums.OrderEventTypeEnum;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.service.tablesession.TableSessionService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Order service integration test.
 *
 * <p>Tests order creation happy path, product snapshot freezing,
 * amount calculation, business_date computation, order_no generation,
 * and event log writing.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:order_service_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OrderServiceTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private OrderEventLogMapper eventLogMapper;
    @Autowired
    private TableSessionService tableSessionService;
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
    void createOrderHappyPath() {
        OrderCreateReqVO req = buildCreateReq("SELF_PICKUP", 1L);
        String idempotentKey = UUID.randomUUID().toString();

        OrderCreateRespVO resp = orderService.createOrder(req, idempotentKey);

        assertThat(resp.getOrderId()).isNotNull();
        assertThat(resp.getOrderNo()).isNotNull().isNotEmpty();
        assertThat(resp.getStatus()).isEqualTo(OrderStatusEnum.PENDING.getCode());
        assertThat(resp.getTotalAmount()).isEqualByComparingTo(new BigDecimal("22.00")); // 12.00 + 10.00
        assertThat(resp.getBusinessDate()).isNotNull();
        assertThat(resp.getItems()).hasSize(2);
    }

    @Test
    void createOrderFreezesProductSnapshot() {
        OrderCreateReqVO req = buildCreateReq("SELF_PICKUP", 1L);
        String idempotentKey = UUID.randomUUID().toString();

        OrderCreateRespVO resp = orderService.createOrder(req, idempotentKey);

        List<OrderItemDO> items = orderItemMapper.selectList(OrderItemDO::getOrderId, resp.getOrderId());
        assertThat(items).hasSize(2);

        // Verify snapshot fields are frozen from SKU data
        OrderItemDO beefItem = items.stream()
                .filter(i -> i.getSkuId() == 1001L)
                .findFirst().orElseThrow();
        assertThat(beefItem.getSkuCode()).isEqualTo("SKU_BEEF");
        assertThat(beefItem.getSkuName()).isEqualTo("Beef Burger");
        assertThat(beefItem.getUnitPrice()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(beefItem.getQuantity()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(beefItem.getItemTotal()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(beefItem.getSpuId()).isEqualTo(101L);
        assertThat(beefItem.getSpuName()).isEqualTo("Burger");
        assertThat(beefItem.getCategoryId()).isEqualTo(10L);
    }

    @Test
    void createOrderComputesTotalAmount() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item1 = new OrderItemReqVO();
        item1.setSkuId(1001L);
        item1.setQuantity(new BigDecimal("2")); // 12.00 * 2 = 24.00

        OrderItemReqVO item2 = new OrderItemReqVO();
        item2.setSkuId(2001L);
        item2.setQuantity(new BigDecimal("3")); // 5.00 * 3 = 15.00

        req.setItems(List.of(item1, item2));

        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());
        assertThat(resp.getTotalAmount()).isEqualByComparingTo(new BigDecimal("39.00"));
    }

    @Test
    void createOrderWritesEventLog() {
        OrderCreateReqVO req = buildCreateReq("SELF_PICKUP", 1L);
        String idempotentKey = UUID.randomUUID().toString();

        OrderCreateRespVO resp = orderService.createOrder(req, idempotentKey);

        List<OrderEventLogDO> logs = eventLogMapper.selectList(
                OrderEventLogDO::getOrderId, resp.getOrderId());
        assertThat(logs).hasSize(1);

        OrderEventLogDO log = logs.get(0);
        assertThat(log.getEventType()).isEqualTo(OrderEventTypeEnum.CREATE.getCode());
        assertThat(log.getBeforeStatus()).isNull();
        assertThat(log.getAfterStatus()).isEqualTo(OrderStatusEnum.PENDING.getCode());
        assertThat(log.getOperatorRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void createOrderGeneratesUniqueOrderNo() {
        OrderCreateReqVO req1 = buildCreateReq("SELF_PICKUP", 1L);
        OrderCreateReqVO req2 = buildCreateReq("SELF_PICKUP", 1L);

        OrderCreateRespVO resp1 = orderService.createOrder(req1, UUID.randomUUID().toString());
        OrderCreateRespVO resp2 = orderService.createOrder(req2, UUID.randomUUID().toString());

        assertThat(resp1.getOrderNo()).isNotEqualTo(resp2.getOrderNo());
    }

    @Test
    void createOrderSetsInitialStatusToPending() {
        OrderCreateReqVO req = buildCreateReq("SELF_PICKUP", 1L);
        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());
        assertThat(resp.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void createOrderWithInvalidChannelShouldFail() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("INVALID_CHANNEL");

        OrderItemReqVO item = new OrderItemReqVO();
        item.setSkuId(1001L);
        item.setQuantity(new BigDecimal("1"));
        req.setItems(List.of(item));

        assertThatThrownBy(() -> orderService.createOrder(req, UUID.randomUUID().toString()))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void createOrderWithEmptyItemsShouldFail() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");
        req.setItems(List.of());

        assertThatThrownBy(() -> orderService.createOrder(req, UUID.randomUUID().toString()))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void createOrderWithNullSkuIdShouldFail() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item = new OrderItemReqVO();
        item.setSkuId(null);
        item.setQuantity(new BigDecimal("1"));
        req.setItems(List.of(item));

        assertThatThrownBy(() -> orderService.createOrder(req, UUID.randomUUID().toString()))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void createOrderWithNonSellableSkuShouldFail() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item = new OrderItemReqVO();
        item.setSkuId(3001L); // PAUSED status
        item.setQuantity(new BigDecimal("1"));
        req.setItems(List.of(item));

        assertThatThrownBy(() -> orderService.createOrder(req, UUID.randomUUID().toString()))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void createOrderWithoutIdempotentKeyShouldFail() {
        OrderCreateReqVO req = buildCreateReq("SELF_PICKUP", 1L);
        assertThatThrownBy(() -> orderService.createOrder(req, null))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void getOrderReturnsOrder() {
        OrderCreateReqVO req = buildCreateReq("SELF_PICKUP", 1L);
        OrderCreateRespVO created = orderService.createOrder(req, UUID.randomUUID().toString());

        OrderDO order = orderService.getOrder(created.getOrderId());
        assertThat(order).isNotNull();
        assertThat(order.getId()).isEqualTo(created.getOrderId());
        assertThat(order.getOrderNo()).isEqualTo(created.getOrderNo());
    }

    @Test
    void getOrderItemsReturnsItems() {
        OrderCreateReqVO req = buildCreateReq("SELF_PICKUP", 1L);
        OrderCreateRespVO created = orderService.createOrder(req, UUID.randomUUID().toString());

        List<OrderItemDO> items = orderService.getOrderItems(created.getOrderId());
        assertThat(items).hasSize(2);
    }

    @Test
    void createOrderWithFractionalQuantity() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item = new OrderItemReqVO();
        item.setSkuId(1001L);
        item.setQuantity(new BigDecimal("0.5")); // 12.00 * 0.5 = 6.00
        req.setItems(List.of(item));

        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());
        assertThat(resp.getTotalAmount()).isEqualByComparingTo(new BigDecimal("6.0000"));
    }

    // --- G1-01D: DINE_IN table session validation tests ---

    @Test
    void dineInOrderWithValidSessionSucceeds() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        OrderCreateReqVO req = buildCreateReq("DINE_IN", 1L);
        req.setTableSessionId(session.getId());

        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        assertThat(resp.getOrderId()).isNotNull();
        OrderDO order = orderService.getOrder(resp.getOrderId());
        assertThat(order.getTableSessionId()).isEqualTo(session.getId());
        assertThat(order.getTableNo()).isEqualTo("T01");
    }

    @Test
    void dineInOrderWithoutSessionIdThrows() {
        OrderCreateReqVO req = buildCreateReq("DINE_IN", 1L);

        assertThatThrownBy(() -> orderService.createOrder(req, UUID.randomUUID().toString()))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void nonDineInOrderWithSessionIdThrows() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        OrderCreateReqVO req = buildCreateReq("SELF_PICKUP", 1L);
        req.setTableSessionId(session.getId());

        assertThatThrownBy(() -> orderService.createOrder(req, UUID.randomUUID().toString()))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void dineInOrderTransitionsSessionFromOpenToOrdering() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        OrderCreateReqVO req = buildCreateReq("DINE_IN", 1L);
        req.setTableSessionId(session.getId());

        orderService.createOrder(req, UUID.randomUUID().toString());

        OrderTableSessionDO updated = tableSessionService.getBySessionNo(session.getSessionNo());
        assertThat(updated.getStatus()).isEqualTo("ORDERING");
    }

    @Test
    void nonDineInOrderKeepsTableSessionIdNull() {
        OrderCreateReqVO req = buildCreateReq("SELF_PICKUP", 1L);

        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        OrderDO order = orderService.getOrder(resp.getOrderId());
        assertThat(order.getTableSessionId()).isNull();
        assertThat(order.getTableNo()).isNull();
    }

    private OrderCreateReqVO buildCreateReq(String channel, Long shopId) {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(shopId);
        req.setChannel(channel);

        OrderItemReqVO item1 = new OrderItemReqVO();
        item1.setSkuId(1001L);
        item1.setQuantity(new BigDecimal("1"));

        OrderItemReqVO item2 = new OrderItemReqVO();
        item2.setSkuId(1002L);
        item2.setQuantity(new BigDecimal("1"));

        req.setItems(List.of(item1, item2));
        return req;
    }
}
