package com.geihou.module.finance.order.enums;

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
import com.geihou.module.finance.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BigDecimal money test.
 *
 * <p>Verifies that all money fields in OrderDO and OrderItemDO are BigDecimal type.
 * Verifies that no double/float fields are used for money.
 * Verifies actual money values in created orders are BigDecimal instances.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:bigdecimal_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class BigDecimalMoneyTest {

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
    void orderDOHasNoDoubleOrFloatMoneyFields() {
        Field[] fields = OrderDO.class.getDeclaredFields();
        for (Field field : fields) {
            String name = field.getName();
            // Check money-related fields are BigDecimal
            if (name.contains("amount") || name.contains("Amount") || name.contains("fee") || name.contains("Fee")) {
                assertThat(field.getType())
                        .as("Field %s in OrderDO must be BigDecimal, not %s", name, field.getType().getSimpleName())
                        .isEqualTo(BigDecimal.class);
            }
        }
    }

    @Test
    void orderItemDOHasNoDoubleOrFloatMoneyFields() {
        Field[] fields = OrderItemDO.class.getDeclaredFields();
        for (Field field : fields) {
            String name = field.getName();
            // Check money-related fields are BigDecimal
            if (name.contains("price") || name.contains("Price") ||
                name.contains("quantity") || name.contains("Quantity") ||
                name.contains("discount") || name.contains("Discount") ||
                name.contains("total") || name.contains("Total") ||
                name.contains("paid") || name.contains("Paid") ||
                name.contains("refunded") || name.contains("Refunded")) {
                assertThat(field.getType())
                        .as("Field %s in OrderItemDO must be BigDecimal, not %s", name, field.getType().getSimpleName())
                        .isEqualTo(BigDecimal.class);
            }
        }
    }

    @Test
    void createdOrderHasBigDecimalMoneyValues() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item = new OrderItemReqVO();
        item.setSkuId(1001L);
        item.setQuantity(new BigDecimal("1"));
        req.setItems(List.of(item));

        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        OrderDO order = orderMapper.selectById(resp.getOrderId());
        assertThat(order.getTotalAmount()).isInstanceOf(BigDecimal.class);
        assertThat(order.getPaidAmount()).isInstanceOf(BigDecimal.class);
        assertThat(order.getDiscountAmount()).isInstanceOf(BigDecimal.class);
        assertThat(order.getRefundAmount()).isInstanceOf(BigDecimal.class);
        assertThat(order.getPlatformFee()).isInstanceOf(BigDecimal.class);
    }

    @Test
    void createdOrderItemsHaveBigDecimalMoneyValues() {
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
        OrderItemDO itemDO = items.get(0);

        assertThat(itemDO.getUnitPrice()).isInstanceOf(BigDecimal.class);
        assertThat(itemDO.getQuantity()).isInstanceOf(BigDecimal.class);
        assertThat(itemDO.getItemDiscount()).isInstanceOf(BigDecimal.class);
        assertThat(itemDO.getItemTotal()).isInstanceOf(BigDecimal.class);
        assertThat(itemDO.getItemPaid()).isInstanceOf(BigDecimal.class);
        assertThat(itemDO.getRefundedQuantity()).isInstanceOf(BigDecimal.class);
        assertThat(itemDO.getRefundedAmount()).isInstanceOf(BigDecimal.class);
    }

    @Test
    void orderCreateRespVOUsesBigDecimal() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item = new OrderItemReqVO();
        item.setSkuId(1001L);
        item.setQuantity(new BigDecimal("1"));
        req.setItems(List.of(item));

        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        assertThat(resp.getTotalAmount()).isInstanceOf(BigDecimal.class);
    }
}
