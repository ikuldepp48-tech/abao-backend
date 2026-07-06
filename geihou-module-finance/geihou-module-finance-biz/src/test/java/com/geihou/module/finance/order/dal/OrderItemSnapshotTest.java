package com.geihou.module.finance.order.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderItemDO;
import com.geihou.module.finance.order.dal.mapper.OrderItemMapper;
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

/**
 * Order item snapshot test.
 *
 * <p>Verifies that product snapshot fields in order_items are frozen at creation time
 * and contain the correct SKU/SPU data from ProductApi.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:snapshot_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OrderItemSnapshotTest {

    @Autowired
    private OrderService orderService;
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
    void snapshotContainsAllRequiredFields() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item = new OrderItemReqVO();
        item.setSkuId(1001L);
        item.setQuantity(new BigDecimal("2"));
        req.setItems(List.of(item));

        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        List<OrderItemDO> items = orderItemMapper.selectList(
                OrderItemDO::getOrderId, resp.getOrderId());
        assertThat(items).hasSize(1);

        OrderItemDO snapshot = items.get(0);
        // Verify all snapshot fields are populated
        assertThat(snapshot.getSkuId()).isEqualTo(1001L);
        assertThat(snapshot.getSkuCode()).isEqualTo("SKU_BEEF");
        assertThat(snapshot.getSkuName()).isEqualTo("Beef Burger");
        assertThat(snapshot.getSpuId()).isEqualTo(101L);
        assertThat(snapshot.getSpuName()).isEqualTo("Burger");
        assertThat(snapshot.getCategoryId()).isEqualTo(10L);
        assertThat(snapshot.getUnitPrice()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(snapshot.getQuantity()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(snapshot.getItemTotal()).isEqualByComparingTo(new BigDecimal("24.00"));
    }

    @Test
    void snapshotUnitPriceIsFromSkuSellingPrice() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item = new OrderItemReqVO();
        item.setSkuId(2001L); // Cola, price 5.00
        item.setQuantity(new BigDecimal("1"));
        req.setItems(List.of(item));

        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        List<OrderItemDO> items = orderItemMapper.selectList(
                OrderItemDO::getOrderId, resp.getOrderId());
        OrderItemDO snapshot = items.get(0);
        assertThat(snapshot.getUnitPrice()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void snapshotItemTotalEqualsUnitPriceTimesQuantity() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item = new OrderItemReqVO();
        item.setSkuId(1001L); // price 12.00
        item.setQuantity(new BigDecimal("3.5"));
        req.setItems(List.of(item));

        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        List<OrderItemDO> items = orderItemMapper.selectList(
                OrderItemDO::getOrderId, resp.getOrderId());
        OrderItemDO snapshot = items.get(0);

        BigDecimal expectedTotal = new BigDecimal("12.00").multiply(new BigDecimal("3.5"));
        assertThat(snapshot.getItemTotal()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    void snapshotItemPaidEqualsItemTotalWhenNoDiscount() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item = new OrderItemReqVO();
        item.setSkuId(1002L);
        item.setQuantity(new BigDecimal("1"));
        req.setItems(List.of(item));

        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        List<OrderItemDO> items = orderItemMapper.selectList(
                OrderItemDO::getOrderId, resp.getOrderId());
        OrderItemDO snapshot = items.get(0);

        assertThat(snapshot.getItemDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getItemPaid()).isEqualByComparingTo(snapshot.getItemTotal());
    }

    @Test
    void snapshotInitialItemStatusIsPending() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item = new OrderItemReqVO();
        item.setSkuId(1001L);
        item.setQuantity(new BigDecimal("1"));
        req.setItems(List.of(item));

        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        List<OrderItemDO> items = orderItemMapper.selectList(
                OrderItemDO::getOrderId, resp.getOrderId());
        assertThat(items.get(0).getItemStatus()).isEqualTo("PENDING");
    }

    @Test
    void snapshotRefundedFieldsAreZero() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item = new OrderItemReqVO();
        item.setSkuId(1001L);
        item.setQuantity(new BigDecimal("1"));
        req.setItems(List.of(item));

        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        List<OrderItemDO> items = orderItemMapper.selectList(
                OrderItemDO::getOrderId, resp.getOrderId());
        OrderItemDO snapshot = items.get(0);
        assertThat(snapshot.getRefundedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getRefundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void modifiersAreStoredWhenProvided() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item = new OrderItemReqVO();
        item.setSkuId(1001L);
        item.setQuantity(new BigDecimal("1"));
        item.setModifiers("{\"addon\":\"extra_cheese\"}");
        req.setItems(List.of(item));

        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        List<OrderItemDO> items = orderItemMapper.selectList(
                OrderItemDO::getOrderId, resp.getOrderId());
        assertThat(items.get(0).getModifiers()).isEqualTo("{\"addon\":\"extra_cheese\"}");
    }
}
