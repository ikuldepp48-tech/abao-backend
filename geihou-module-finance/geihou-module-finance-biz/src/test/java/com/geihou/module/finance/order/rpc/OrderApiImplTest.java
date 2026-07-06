package com.geihou.module.finance.order.rpc;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.order.dto.OrderEventDTO;
import com.geihou.module.finance.api.order.dto.OrderRespDTO;
import com.geihou.module.finance.api.order.dto.OrderSummaryRespDTO;
import com.geihou.module.finance.api.order.dto.RefundCheckRespDTO;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.service.OrderService;
import com.geihou.module.finance.order.service.refund.RefundService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderApiImpl integration test.
 *
 * <p>Tests all 4 OrderApi methods:
 * getOrder, summarizeByBusinessDate, publishOrderEvent (stub), checkRefundable.
 *
 * <p>Verifies tenant context enforcement (ORDER_TENANT_MISMATCH),
 * BigDecimal types, channel filtering, zero-value empty results,
 * refund status/amount/approval checks.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:order_api_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OrderApiImplTest {

    @Autowired
    private OrderApiImpl orderApi;

    @Autowired
    private OrderService orderService;

    @Autowired
    private RefundService refundService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private DataSource dataSource;

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 6, 22);
    private static final BigDecimal ORDER_TOTAL = new BigDecimal("22.00");

    private Long completedOrderId;

    @BeforeEach
    void setUp() throws Exception {
        OrderTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);

        // Create a COMPLETED order for refund-check tests
        completedOrderId = createOrder(TEST_DATE, "SELF_PICKUP", 1L);
        orderService.markOrderPaid(completedOrderId, "CASH", ORDER_TOTAL, null);
        // Use direct JDBC to set COMPLETED status (bypasses optimistic lock
        // and state machine; we only need a COMPLETED order for read-only checks)
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE orders SET status='COMPLETED', completed_time=? WHERE id=?")) {
            ps.setString(1, LocalDateTime.now().toString());
            ps.setLong(2, completedOrderId);
            ps.executeUpdate();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ===== getOrder tests =====

    @Test
    void getOrderReturnsCorrectDtoWithPlatformFee() {
        OrderRespDTO dto = orderApi.getOrder(1L, completedOrderId);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(completedOrderId);
        assertThat(dto.getTenantId()).isEqualTo(1L);
        assertThat(dto.getChannel()).isEqualTo("SELF_PICKUP");
        assertThat(dto.getStatus()).isEqualTo("COMPLETED");
        assertThat(dto.getTotalAmount()).isInstanceOf(BigDecimal.class);
        assertThat(dto.getPaidAmount()).isEqualByComparingTo(ORDER_TOTAL);
        assertThat(dto.getPlatformFee()).isInstanceOf(BigDecimal.class);
        assertThat(dto.getCouponId()).isNull();
        assertThat(dto.getPromotionIds()).isNull();
    }

    @Test
    void getOrderReturnsNullForNotFound() {
        OrderRespDTO dto = orderApi.getOrder(1L, 99999L);
        assertThat(dto).isNull();
    }

    @Test
    void getOrderThrowsTenantMismatchWhenContextDifferent() {
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(2L);

        assertThatThrownBy(() -> orderApi.getOrder(1L, completedOrderId))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void getOrderThrowsWhenContextTenantNull() {
        TenantContextHolder.clear();

        assertThatThrownBy(() -> orderApi.getOrder(1L, completedOrderId))
                .isInstanceOf(OrderBusinessException.class);
    }

    // ===== summarizeByBusinessDate tests =====

    @Test
    void summarizeByBusinessDateAllChannelReturnsCorrectSummary() {
        // completedOrderId already created with paid 22.00
        OrderSummaryRespDTO dto = orderApi.summarizeByBusinessDate(1L, TEST_DATE, null);

        assertThat(dto).isNotNull();
        assertThat(dto.getBusinessDate()).isEqualTo(TEST_DATE);
        assertThat(dto.getChannel()).isNull();
        assertThat(dto.getOrderCount()).isEqualTo(1);
        assertThat(dto.getTotalAmount()).isEqualByComparingTo(ORDER_TOTAL);
        assertThat(dto.getPaidAmount()).isEqualByComparingTo(ORDER_TOTAL);
        assertThat(dto.getPlatformFee()).isInstanceOf(BigDecimal.class);
        assertThat(dto.getDiscountAmount()).isInstanceOf(BigDecimal.class);
        assertThat(dto.getRefundAmount()).isInstanceOf(BigDecimal.class);
        assertThat(dto.getNetRevenue()).isInstanceOf(BigDecimal.class);
        // netRevenue = paid - refund - fee = 22 - 0 - 0 = 22
        assertThat(dto.getNetRevenue()).isEqualByComparingTo(ORDER_TOTAL);
    }

    @Test
    void summarizeByBusinessDateSpecificChannelReturnsFilteredSummary() {
        // Create another order with a different channel
        Long orderId2 = createOrder(TEST_DATE, "MEITUAN_TAKEOUT", 1L);
        orderService.markOrderPaid(orderId2, "CASH", ORDER_TOTAL, null);

        OrderSummaryRespDTO dto = orderApi.summarizeByBusinessDate(1L, TEST_DATE, "MEITUAN_TAKEOUT");

        assertThat(dto).isNotNull();
        assertThat(dto.getChannel()).isEqualTo("MEITUAN_TAKEOUT");
        assertThat(dto.getOrderCount()).isEqualTo(1);
        assertThat(dto.getPaidAmount()).isEqualByComparingTo(ORDER_TOTAL);
    }

    @Test
    void summarizeByBusinessDateNoMatchingChannelReturnsZeroValues() {
        OrderSummaryRespDTO dto = orderApi.summarizeByBusinessDate(1L, TEST_DATE, "DOUSHAN_TAKEOUT");

        assertThat(dto).isNotNull();
        assertThat(dto.getOrderCount()).isEqualTo(0);
        assertThat(dto.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getNetRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void summarizeByBusinessDateEmptyDateReturnsZeroValues() {
        OrderSummaryRespDTO dto = orderApi.summarizeByBusinessDate(1L, LocalDate.of(2020, 1, 1), null);

        assertThat(dto).isNotNull();
        assertThat(dto.getOrderCount()).isEqualTo(0);
        assertThat(dto.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getRefundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getPlatformFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getNetRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void summarizeByBusinessDateThrowsTenantMismatchWhenContextDifferent() {
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(2L);

        assertThatThrownBy(() -> orderApi.summarizeByBusinessDate(1L, TEST_DATE, null))
                .isInstanceOf(OrderBusinessException.class);
    }

    // ===== checkRefundable tests =====

    @Test
    void checkRefundableCompletedOrderLowAmountReturnsTrue() {
        // Order paid 22.00; refund 10.00 (≤ paid, ≤ 500 threshold)
        RefundCheckRespDTO resp = orderApi.checkRefundable(1L, completedOrderId, new BigDecimal("10.00"));

        assertThat(resp).isNotNull();
        assertThat(resp.getRefundable()).isTrue();
        assertThat(resp.getMaxRefundableAmount()).isEqualByComparingTo(ORDER_TOTAL);
        assertThat(resp.getRequiresApproval()).isFalse();
        assertThat(resp.getReason()).isNull();
    }

    @Test
    void checkRefundableCompletedOrderHighAmountRequiresApproval() {
        // refund 501 > 500 threshold
        RefundCheckRespDTO resp = orderApi.checkRefundable(1L, completedOrderId, new BigDecimal("501.00"));

        // 501 > 22 (paid) so refundable=false; but requiresApproval is based on amount > 500
        // Order only has 22 paid; 501 > 22 so REFUND_EXCEEDS_PAID
        assertThat(resp.getRefundable()).isFalse();
        assertThat(resp.getReason()).isEqualTo("REFUND_EXCEEDS_PAID");
        // requiresApproval is computed regardless of refundable
        assertThat(resp.getRequiresApproval()).isTrue();
    }

    @Test
    void checkRefundableNonCompletedOrderReturnsFalse() {
        // Create a PENDING order (not paid)
        Long pendingOrderId = createOrder(TEST_DATE, "SELF_PICKUP", 1L);

        RefundCheckRespDTO resp = orderApi.checkRefundable(1L, pendingOrderId, new BigDecimal("10.00"));

        assertThat(resp.getRefundable()).isFalse();
        assertThat(resp.getReason()).isEqualTo("ORDER_NOT_COMPLETED");
    }

    @Test
    void checkRefundableAmountExceedsPaidReturnsFalse() {
        // Order paid 22; try to refund 30
        RefundCheckRespDTO resp = orderApi.checkRefundable(1L, completedOrderId, new BigDecimal("30.00"));

        assertThat(resp.getRefundable()).isFalse();
        assertThat(resp.getReason()).isEqualTo("REFUND_EXCEEDS_PAID");
        assertThat(resp.getMaxRefundableAmount()).isEqualByComparingTo(ORDER_TOTAL);
    }

    @Test
    void checkRefundableOrderNotFoundReturnsFalse() {
        RefundCheckRespDTO resp = orderApi.checkRefundable(1L, 99999L, new BigDecimal("10.00"));

        assertThat(resp.getRefundable()).isFalse();
        assertThat(resp.getReason()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(resp.getMaxRefundableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void checkRefundableThrowsTenantMismatchWhenContextDifferent() {
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(2L);

        assertThatThrownBy(() -> orderApi.checkRefundable(1L, completedOrderId, new BigDecimal("10.00")))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void checkRefundableWithExistingRefundReducesMaxRefundable() throws Exception {
        // Insert a refund record directly via JDBC (keeps order in COMPLETED status;
        // createRefund would transition to REFUNDING which changes the order status)
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO order_refund (tenant_id, refund_no, original_order_id, original_payment_id, " +
                     "refund_amount, refund_type, reason_type, reason_detail, status, " +
                     "creator, create_time, updater, update_time, deleted) " +
                     "VALUES (1, 'RFD1', ?, 1, 10.00, 'PARTIAL', 'CUSTOMER_REQUEST', 'test', 'REFUNDED', " +
                     "'OWNER', CURRENT_TIMESTAMP, 'OWNER', CURRENT_TIMESTAMP, false)")) {
            ps.setLong(1, completedOrderId);
            ps.executeUpdate();
        }

        RefundCheckRespDTO resp = orderApi.checkRefundable(1L, completedOrderId, new BigDecimal("15.00"));

        // maxRefundable = 22 - 10 = 12; 15 > 12 → REFUND_EXCEEDS_PAID
        assertThat(resp.getRefundable()).isFalse();
        assertThat(resp.getReason()).isEqualTo("REFUND_EXCEEDS_PAID");
        assertThat(resp.getMaxRefundableAmount()).isEqualByComparingTo(new BigDecimal("12.00"));
    }

    // ===== publishOrderEvent stub tests =====

    @Test
    void publishOrderEventStubDoesNotThrowWhenTenantMatches() {
        OrderEventDTO event = new OrderEventDTO();
        event.setTenantId(1L);
        event.setOrderId(completedOrderId);
        event.setEventType("ORDER_CREATED");
        event.setEventTime(LocalDateTime.now());

        // Should not throw
        orderApi.publishOrderEvent(event);
    }

    @Test
    void publishOrderEventThrowsTenantMismatchWhenContextDifferent() {
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(2L);

        OrderEventDTO event = new OrderEventDTO();
        event.setTenantId(1L);
        event.setOrderId(completedOrderId);
        event.setEventType("ORDER_CREATED");

        assertThatThrownBy(() -> orderApi.publishOrderEvent(event))
                .isInstanceOf(OrderBusinessException.class);
    }

    // ===== Helpers =====

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
}
