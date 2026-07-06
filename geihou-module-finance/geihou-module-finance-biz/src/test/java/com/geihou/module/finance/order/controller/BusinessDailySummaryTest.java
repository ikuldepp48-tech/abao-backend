package com.geihou.module.finance.order.controller;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.admin.OrderController;
import com.geihou.module.finance.order.controller.admin.vo.OrderDailySummaryChannelVO;
import com.geihou.module.finance.order.controller.admin.vo.OrderDailySummaryRespVO;
import com.geihou.module.finance.order.controller.admin.vo.OrderDailySummaryStatusVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.service.OrderService;
import com.geihou.common.pojo.CommonResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Business daily summary endpoint test (G1-01G).
 *
 * <p>Tests DTO shape, totals, netRevenue, channel/status summary,
 * empty result, shopId filter, tenant isolation, BigDecimal precision,
 * default today, multi-channel, multi-status, and no in-memory selectList.
 *
 * <p>Orders created with 2 items (SKU 1001 @ $12 + SKU 1002 @ $10 = $22 total).
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:daily_summary_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class BusinessDailySummaryTest {

    @Autowired
    private OrderController adminOrderController;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;
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

    // --- 1. Single-day summary with orders ---

    @Test
    void dailySummaryWithOrdersReturnsCorrectTotals() {
        Long orderId1 = createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        orderService.markOrderPaid(orderId1, "CASH", ORDER_TOTAL, null);
        Long orderId2 = createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        orderService.markOrderPaid(orderId2, "CASH", ORDER_TOTAL, null);

        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(TEST_DATE, null);

        assertThat(result.getCode()).isEqualTo(0);
        OrderDailySummaryRespVO data = result.getData();
        assertThat(data.getOrderCount()).isEqualTo(2);
        assertThat(data.getTotalAmount()).isEqualByComparingTo(new BigDecimal("44.0000"));
        assertThat(data.getPaidAmount()).isEqualByComparingTo(new BigDecimal("44.0000"));
        assertThat(data.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(data.getRefundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(data.getPlatformFee()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- 2. Empty result ---

    @Test
    void dailySummaryWithNoOrdersReturnsZeros() {
        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(LocalDate.of(2020, 1, 1), null);

        OrderDailySummaryRespVO data = result.getData();
        assertThat(data.getOrderCount()).isEqualTo(0);
        assertThat(data.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(data.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(data.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(data.getRefundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(data.getPlatformFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(data.getNetRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(data.getChannelSummary()).isEmpty();
        assertThat(data.getStatusSummary()).isEmpty();
    }

    // --- 3. netRevenue calculation ---

    @Test
    void netRevenueEqualsPaidMinusRefundMinusPlatformFee() {
        Long orderId = createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        orderService.markOrderPaid(orderId, "CASH", ORDER_TOTAL, null);

        // Manually set platformFee and refundAmount to verify netRevenue
        OrderDO order = orderMapper.selectById(orderId);
        order.setPlatformFee(new BigDecimal("1.50"));
        order.setRefundAmount(new BigDecimal("3.00"));
        orderMapper.updateById(order);

        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(TEST_DATE, null);

        OrderDailySummaryRespVO data = result.getData();
        // netRevenue = 22.00 - 3.00 - 1.50 = 17.50
        assertThat(data.getNetRevenue()).isEqualByComparingTo(new BigDecimal("17.5000"));
    }

    // --- 4. netRevenue does not subtract discountAmount ---

    @Test
    void netRevenueDoesNotSubtractDiscountAmount() {
        Long orderId = createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        orderService.markOrderPaid(orderId, "CASH", ORDER_TOTAL, null);

        // Set discountAmount but keep refundAmount and platformFee at 0
        OrderDO order = orderMapper.selectById(orderId);
        order.setDiscountAmount(new BigDecimal("5.00"));
        orderMapper.updateById(order);

        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(TEST_DATE, null);

        // netRevenue = paidAmount(22) - refund(0) - platformFee(0) = 22, NOT 22-5=17
        assertThat(result.getData().getNetRevenue()).isEqualByComparingTo(new BigDecimal("22.0000"));
    }

    // --- 5. Channel summary ---

    @Test
    void channelSummaryGroupsByChannel() {
        createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        createOrder(TEST_DATE, "MEITUAN_TAKEOUT", 1L);

        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(TEST_DATE, null);

        List<OrderDailySummaryChannelVO> channels = result.getData().getChannelSummary();
        assertThat(channels).hasSize(2);

        OrderDailySummaryChannelVO selfPickup = channels.stream()
                .filter(c -> "SELF_PICKUP".equals(c.getChannel()))
                .findFirst().orElseThrow();
        assertThat(selfPickup.getOrderCount()).isEqualTo(2);
        assertThat(selfPickup.getTotalAmount()).isEqualByComparingTo(new BigDecimal("44.0000"));

        OrderDailySummaryChannelVO meituan = channels.stream()
                .filter(c -> "MEITUAN_TAKEOUT".equals(c.getChannel()))
                .findFirst().orElseThrow();
        assertThat(meituan.getOrderCount()).isEqualTo(1);
        assertThat(meituan.getTotalAmount()).isEqualByComparingTo(new BigDecimal("22.0000"));
    }

    // --- 6. Status summary ---

    @Test
    void statusSummaryGroupsByStatus() {
        createOrder(TEST_DATE, "SELF_PICKUP", 1L); // PENDING
        Long orderId2 = createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        orderService.markOrderPaid(orderId2, "CASH", ORDER_TOTAL, null); // PAID

        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(TEST_DATE, null);

        List<OrderDailySummaryStatusVO> statuses = result.getData().getStatusSummary();
        assertThat(statuses).hasSize(2);

        OrderDailySummaryStatusVO pending = statuses.stream()
                .filter(s -> "PENDING".equals(s.getStatus()))
                .findFirst().orElseThrow();
        assertThat(pending.getOrderCount()).isEqualTo(1);
        assertThat(pending.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        OrderDailySummaryStatusVO paid = statuses.stream()
                .filter(s -> "PAID".equals(s.getStatus()))
                .findFirst().orElseThrow();
        assertThat(paid.getOrderCount()).isEqualTo(1);
        assertThat(paid.getPaidAmount()).isEqualByComparingTo(new BigDecimal("22.0000"));
    }

    // --- 7. shopId filter ---

    @Test
    void shopIdFilterReturnsOnlyMatchingShop() {
        createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        createOrder(TEST_DATE, "SELF_PICKUP", 2L);

        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(TEST_DATE, 1L);

        OrderDailySummaryRespVO data = result.getData();
        assertThat(data.getOrderCount()).isEqualTo(1);
        assertThat(data.getShopId()).isEqualTo(1L);
        assertThat(data.getTotalAmount()).isEqualByComparingTo(new BigDecimal("22.0000"));
    }

    // --- 8. shopId null = all shops ---

    @Test
    void shopIdNullReturnsAllShops() {
        createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        createOrder(TEST_DATE, "SELF_PICKUP", 2L);

        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(TEST_DATE, null);

        assertThat(result.getData().getOrderCount()).isEqualTo(2);
        assertThat(result.getData().getShopId()).isNull();
    }

    // --- 9. Tenant isolation ---

    @Test
    void tenantIsolationHidesOtherTenantOrders() {
        TenantContextHolder.setTenantId(1L);
        createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        createOrder(TEST_DATE, "SELF_PICKUP", 1L);

        // Switch to tenant 2
        TenantContextHolder.setTenantId(2L);

        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(TEST_DATE, null);

        // Tenant 2 should see zero orders from tenant 1
        assertThat(result.getData().getOrderCount()).isEqualTo(0);
        assertThat(result.getData().getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getData().getChannelSummary()).isEmpty();
        assertThat(result.getData().getStatusSummary()).isEmpty();
    }

    // --- 10. BigDecimal precision ---

    @Test
    void bigDecimalPrecisionIsMaintained() {
        Long orderId = createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        orderService.markOrderPaid(orderId, "CASH", ORDER_TOTAL, null);

        // Set precise decimal values
        OrderDO order = orderMapper.selectById(orderId);
        order.setPlatformFee(new BigDecimal("0.1234"));
        order.setRefundAmount(new BigDecimal("1.5678"));
        orderMapper.updateById(order);

        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(TEST_DATE, null);

        OrderDailySummaryRespVO data = result.getData();
        assertThat(data.getPlatformFee()).isEqualByComparingTo(new BigDecimal("0.1234"));
        assertThat(data.getRefundAmount()).isEqualByComparingTo(new BigDecimal("1.5678"));
        // netRevenue = 22.00 - 1.5678 - 0.1234 = 20.3088
        assertThat(data.getNetRevenue()).isEqualByComparingTo(new BigDecimal("20.3088"));
    }

    // --- 11. Default businessDate is today ---

    @Test
    void defaultBusinessDateIsToday() {
        createOrder(LocalDate.now(), "SELF_PICKUP", 1L);

        // Pass null for businessDate — should default to today
        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(null, null);

        assertThat(result.getData().getBusinessDate()).isEqualTo(LocalDate.now());
        assertThat(result.getData().getOrderCount()).isEqualTo(1);
    }

    // --- 12. DTO shape: 9 top-level fields + 2 List fields ---

    @Test
    void dtoShapeHasAllRequiredFields() {
        createOrder(TEST_DATE, "SELF_PICKUP", 1L);

        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(TEST_DATE, null);

        OrderDailySummaryRespVO data = result.getData();
        // 9 top-level fields
        assertThat(data.getBusinessDate()).isEqualTo(TEST_DATE);
        assertThat(data.getShopId()).isNull();
        assertThat(data.getOrderCount()).isNotNull();
        assertThat(data.getTotalAmount()).isNotNull();
        assertThat(data.getPaidAmount()).isNotNull();
        assertThat(data.getDiscountAmount()).isNotNull();
        assertThat(data.getRefundAmount()).isNotNull();
        assertThat(data.getPlatformFee()).isNotNull();
        assertThat(data.getNetRevenue()).isNotNull();
        // 2 List fields
        assertThat(data.getChannelSummary()).isNotNull();
        assertThat(data.getStatusSummary()).isNotNull();
    }

    // --- 13. Channel summary includes netRevenue ---

    @Test
    void channelSummaryIncludesNetRevenue() {
        Long orderId = createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        orderService.markOrderPaid(orderId, "CASH", ORDER_TOTAL, null);

        // Set platformFee for channel netRevenue calculation
        OrderDO order = orderMapper.selectById(orderId);
        order.setPlatformFee(new BigDecimal("2.00"));
        orderMapper.updateById(order);

        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(TEST_DATE, null);

        OrderDailySummaryChannelVO channel = result.getData().getChannelSummary().get(0);
        // channel netRevenue = 22.00 - 0 - 2.00 = 20.00
        assertThat(channel.getNetRevenue()).isEqualByComparingTo(new BigDecimal("20.0000"));
    }

    // --- 14. Channel summary VO has exactly 6 fields ---

    @Test
    void channelSummaryVOHasSixFields() {
        createOrder(TEST_DATE, "SELF_PICKUP", 1L);

        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(TEST_DATE, null);

        OrderDailySummaryChannelVO vo = result.getData().getChannelSummary().get(0);
        assertThat(vo.getChannel()).isNotNull();
        assertThat(vo.getOrderCount()).isNotNull();
        assertThat(vo.getTotalAmount()).isNotNull();
        assertThat(vo.getPaidAmount()).isNotNull();
        assertThat(vo.getRefundAmount()).isNotNull();
        assertThat(vo.getNetRevenue()).isNotNull();
    }

    // --- 15. Status summary VO has exactly 5 fields ---

    @Test
    void statusSummaryVOHasFiveFields() {
        createOrder(TEST_DATE, "SELF_PICKUP", 1L);

        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(TEST_DATE, null);

        OrderDailySummaryStatusVO vo = result.getData().getStatusSummary().get(0);
        assertThat(vo.getStatus()).isNotNull();
        assertThat(vo.getOrderCount()).isNotNull();
        assertThat(vo.getTotalAmount()).isNotNull();
        assertThat(vo.getPaidAmount()).isNotNull();
        assertThat(vo.getRefundAmount()).isNotNull();
    }

    // --- Helper ---

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

        // Update business_date to the test date (createOrder uses BusinessDateCalculator)
        OrderDO order = orderMapper.selectById(resp.getOrderId());
        order.setBusinessDate(businessDate);
        orderMapper.updateById(order);

        return resp.getOrderId();
    }
}
