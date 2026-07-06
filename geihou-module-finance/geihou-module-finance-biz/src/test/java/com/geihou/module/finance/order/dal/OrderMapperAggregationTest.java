package com.geihou.module.finance.order.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderMapper aggregation query test (G1-01G).
 *
 * <p>Tests the 3 SQL aggregation methods appended to OrderMapper:
 * selectDailySummary, selectDailySummaryByChannel, selectDailySummaryByStatus.
 * Verifies SQL aggregation correctness, tenant isolation, shopId filter,
 * empty result, multi-channel grouping, multi-status grouping.
 *
 * <p>Orders created with 2 items (SKU 1001 @ $12 + SKU 1002 @ $10 = $22 total).
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:mapper_agg_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OrderMapperAggregationTest {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderService orderService;
    @Autowired
    private DataSource dataSource;

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 6, 22);
    private static final BigDecimal ORDER_TOTAL = new BigDecimal("22.00");

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

    // --- 1. SQL aggregation correctness ---

    @Test
    void selectDailySummaryReturnsCorrectAggregates() {
        Long orderId1 = createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        orderService.markOrderPaid(orderId1, "CASH", ORDER_TOTAL, null);
        Long orderId2 = createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        orderService.markOrderPaid(orderId2, "CASH", ORDER_TOTAL, null);

        Map<String, Object> result = orderMapper.selectDailySummary(1L, TEST_DATE, null);

        assertThat(getInt(result, "order_count")).isEqualTo(2);
        assertThat(getDecimal(result, "total_amount")).isEqualByComparingTo(new BigDecimal("44.0000"));
        assertThat(getDecimal(result, "paid_amount")).isEqualByComparingTo(new BigDecimal("44.0000"));
        assertThat(getDecimal(result, "discount_amount")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(getDecimal(result, "refund_amount")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(getDecimal(result, "platform_fee")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- 2. Empty result ---

    @Test
    void selectDailySummaryReturnsZerosForNoOrders() {
        Map<String, Object> result = orderMapper.selectDailySummary(1L, LocalDate.of(2020, 1, 1), null);

        assertThat(getInt(result, "order_count")).isEqualTo(0);
        assertThat(getDecimal(result, "total_amount")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(getDecimal(result, "paid_amount")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- 3. Tenant isolation ---

    @Test
    void selectDailySummaryEnforcesTenantIsolation() {
        // Create orders as tenant 1
        TenantContextHolder.setTenantId(1L);
        createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        createOrder(TEST_DATE, "SELF_PICKUP", 1L);

        // Query as tenant 2 — should see zero orders
        Map<String, Object> result = orderMapper.selectDailySummary(2L, TEST_DATE, null);
        assertThat(getInt(result, "order_count")).isEqualTo(0);
    }

    // --- 4. shopId filter ---

    @Test
    void selectDailySummaryWithShopIdFiltersCorrectly() {
        createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        createOrder(TEST_DATE, "SELF_PICKUP", 2L);

        Map<String, Object> result = orderMapper.selectDailySummary(1L, TEST_DATE, 1L);
        assertThat(getInt(result, "order_count")).isEqualTo(1);

        // Without shopId — all shops
        Map<String, Object> allResult = orderMapper.selectDailySummary(1L, TEST_DATE, null);
        assertThat(getInt(allResult, "order_count")).isEqualTo(2);
    }

    // --- 5. Multi-channel grouping ---

    @Test
    void selectDailySummaryByChannelGroupsCorrectly() {
        createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        createOrder(TEST_DATE, "MEITUAN_TAKEOUT", 1L);

        List<Map<String, Object>> results = orderMapper.selectDailySummaryByChannel(1L, TEST_DATE, null);

        assertThat(results).hasSize(2);

        Map<String, Object> selfPickup = results.stream()
                .filter(r -> "SELF_PICKUP".equals(getString(r, "channel")))
                .findFirst().orElseThrow();
        assertThat(getInt(selfPickup, "order_count")).isEqualTo(2);

        Map<String, Object> meituan = results.stream()
                .filter(r -> "MEITUAN_TAKEOUT".equals(getString(r, "channel")))
                .findFirst().orElseThrow();
        assertThat(getInt(meituan, "order_count")).isEqualTo(1);
    }

    // --- 6. Multi-status grouping ---

    @Test
    void selectDailySummaryByStatusGroupsCorrectly() {
        createOrder(TEST_DATE, "SELF_PICKUP", 1L); // PENDING
        Long orderId2 = createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        orderService.markOrderPaid(orderId2, "CASH", ORDER_TOTAL, null); // PAID

        List<Map<String, Object>> results = orderMapper.selectDailySummaryByStatus(1L, TEST_DATE, null);

        assertThat(results).hasSize(2);

        Map<String, Object> pending = results.stream()
                .filter(r -> "PENDING".equals(getString(r, "status")))
                .findFirst().orElseThrow();
        assertThat(getInt(pending, "order_count")).isEqualTo(1);
        assertThat(getDecimal(pending, "paid_amount")).isEqualByComparingTo(BigDecimal.ZERO);

        Map<String, Object> paid = results.stream()
                .filter(r -> "PAID".equals(getString(r, "status")))
                .findFirst().orElseThrow();
        assertThat(getInt(paid, "order_count")).isEqualTo(1);
        assertThat(getDecimal(paid, "paid_amount")).isEqualByComparingTo(new BigDecimal("22.0000"));
    }

    // --- 7. Channel grouping with shopId filter ---

    @Test
    void channelGroupingRespectsShopIdFilter() {
        createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        createOrder(TEST_DATE, "MEITUAN_TAKEOUT", 2L);

        List<Map<String, Object>> results = orderMapper.selectDailySummaryByChannel(1L, TEST_DATE, 1L);
        assertThat(results).hasSize(1);
        assertThat(getString(results.get(0), "channel")).isEqualTo("SELF_PICKUP");
    }

    // --- 8. Empty channel/status grouping ---

    @Test
    void channelAndStatusGroupingReturnEmptyForNoOrders() {
        List<Map<String, Object>> channelResults =
                orderMapper.selectDailySummaryByChannel(1L, LocalDate.of(2020, 1, 1), null);
        assertThat(channelResults).isEmpty();

        List<Map<String, Object>> statusResults =
                orderMapper.selectDailySummaryByStatus(1L, LocalDate.of(2020, 1, 1), null);
        assertThat(statusResults).isEmpty();
    }

    // --- Helpers ---

    private Long createOrder(LocalDate businessDate, String channel, Long shopId) {
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
        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());

        // Override business_date to test date
        OrderDO order = orderMapper.selectById(resp.getOrderId());
        order.setBusinessDate(businessDate);
        orderMapper.updateById(order);

        return resp.getOrderId();
    }

    private static BigDecimal getDecimal(Map<String, Object> map, String key) {
        if (map == null) return BigDecimal.ZERO;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                Object value = entry.getValue();
                if (value == null) return BigDecimal.ZERO;
                if (value instanceof BigDecimal) return (BigDecimal) value;
                if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
                return new BigDecimal(value.toString());
            }
        }
        return BigDecimal.ZERO;
    }

    private static Integer getInt(Map<String, Object> map, String key) {
        if (map == null) return 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                Object value = entry.getValue();
                if (value == null) return 0;
                if (value instanceof Integer) return (Integer) value;
                if (value instanceof Number) return ((Number) value).intValue();
                return Integer.parseInt(value.toString());
            }
        }
        return 0;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                Object value = entry.getValue();
                return value == null ? null : value.toString();
            }
        }
        return null;
    }
}
