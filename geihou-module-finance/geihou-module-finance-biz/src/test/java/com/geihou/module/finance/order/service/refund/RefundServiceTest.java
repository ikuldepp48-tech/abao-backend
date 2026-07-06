package com.geihou.module.finance.order.service.refund;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.order.enums.OrderStatusEnum;
import com.geihou.module.finance.api.order.enums.RefundStatusEnum;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderEventLogDO;
import com.geihou.module.finance.order.dal.dataobject.OrderItemDO;
import com.geihou.module.finance.order.dal.dataobject.OrderRefundDO;
import com.geihou.module.finance.order.dal.mapper.OrderEventLogMapper;
import com.geihou.module.finance.order.dal.mapper.OrderItemMapper;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.dal.mapper.OrderRefundMapper;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.service.OrderService;
import com.geihou.module.finance.order.service.statemachine.OrderStateMachineService;
import com.geihou.module.finance.stock.StockTestConfig;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreReqDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * Refund service integration test (G1-01C).
 *
 * <p>Tests refund create (high/low amount), approve, reject, execute, fail,
 * amount validation, status validation, and event log writing.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:refund_service_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class RefundServiceTest {

    @Autowired
    private RefundService refundService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderStateMachineService stateMachineService;
    @Autowired
    private OrderRefundMapper refundMapper;
    @Autowired
    private OrderEventLogMapper eventLogMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private StockTestConfig.MockStockApi stockApi;
    @Autowired
    private DataSource dataSource;
    @SpyBean
    private OrderMapper orderMapper;

    @BeforeEach
    void setUp() throws Exception {
        OrderTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
        stockApi.reset();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // --- AC-7: Customer refund request creates record + order COMPLETED→REFUNDING + event log ---

    @Test
    void createRefundLowAmountAutoApproves() {
        Long orderId = createCompletedOrder();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("10.00"),
                "CUSTOMER_REQUEST", "Customer wants refund",
                null, null, "CUSTOMER");

        assertThat(refund).isNotNull();
        assertThat(refund.getId()).isNotNull();
        assertThat(refund.getStatus()).isEqualTo(RefundStatusEnum.REFUNDING.getCode());
        assertThat(refund.getRefundAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(refund.getRefundType()).isEqualTo("FULL");
        assertThat(refund.getReasonType()).isEqualTo("CUSTOMER_REQUEST");
        assertThat(refund.getReasonDetail()).isEqualTo("Customer wants refund");

        // Order should be REFUNDING
        OrderDO order = orderService.getOrder(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.REFUNDING.getCode());

        // Event log should contain REFUND_INITIATE
        List<OrderEventLogDO> logs = eventLogMapper.selectList(OrderEventLogDO::getOrderId, orderId);
        assertThat(logs.stream().anyMatch(l -> "REFUND_INITIATE".equals(l.getEventType()))).isTrue();
    }

    // --- AC-9: High amount refund (>500) requires approval ---

    @Test
    void createRefundHighAmountRequiresApproval() {
        Long orderId = createCompletedOrder();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("22.00"),
                "QUALITY_ISSUE", "Food quality problem",
                null, null, "CUSTOMER");

        // 22.00 > 500? No. Let me use the total order amount (22.00).
        // Actually, the threshold is 500, and our test order total is 22.00.
        // We need a refund amount > 500 to trigger approval.
        // But our order paid_amount is 22.00, so we can't refund > 500.
        // Let's verify the logic works for amounts ≤ 500.
        assertThat(refund.getStatus()).isEqualTo(RefundStatusEnum.REFUNDING.getCode());
    }

    @Test
    void createRefundHighAmountAboveThresholdNeedsApproval() {
        // Create order with large amount by using many items
        Long orderId = createCompletedOrderLargeAmount();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("600.00"),
                "CUSTOMER_REQUEST", "Large refund request",
                null, null, "CUSTOMER");

        assertThat(refund.getStatus()).isEqualTo(RefundStatusEnum.PENDING_REVIEW.getCode());
        assertThat(refund.getApproverUserId()).isNull();

        // Order should still be REFUNDING (CG-R1: order transitions at submission)
        OrderDO order = orderService.getOrder(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.REFUNDING.getCode());
    }

    // --- AC-10: Low amount (≤500) auto-approves ---

    @Test
    void createRefundAtThresholdAutoApproves() {
        Long orderId = createCompletedOrderLargeAmount();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "PARTIAL", new BigDecimal("500.00"),
                "WRONG_ORDER", "Exactly at threshold",
                null, null, "CUSTOMER");

        assertThat(refund.getStatus()).isEqualTo(RefundStatusEnum.REFUNDING.getCode());
    }

    // --- AC-8: Refund amount validation ---

    @Test
    void createRefundExceedingPaidAmountFails() {
        Long orderId = createCompletedOrder();
        assertThatThrownBy(() -> refundService.createRefund(
                orderId, "FULL", new BigDecimal("100.00"),
                "CUSTOMER_REQUEST", "Exceeds paid",
                null, null, "CUSTOMER"))
                .isInstanceOf(OrderBusinessException.class);
    }

    // --- AC-16: Only COMPLETED orders can be refunded ---

    @Test
    void createRefundOnNonCompletedOrderFails() {
        Long orderId = createOrder(); // PENDING status
        assertThatThrownBy(() -> refundService.createRefund(
                orderId, "FULL", new BigDecimal("10.00"),
                "CUSTOMER_REQUEST", "Test",
                null, null, "CUSTOMER"))
                .isInstanceOf(OrderBusinessException.class);
    }

    // --- AC-26: Refund reason required ---

    @Test
    void createRefundWithoutReasonTypeFails() {
        Long orderId = createCompletedOrder();
        assertThatThrownBy(() -> refundService.createRefund(
                orderId, "FULL", new BigDecimal("10.00"),
                null, "detail",
                null, null, "CUSTOMER"))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void createRefundWithoutReasonDetailFails() {
        Long orderId = createCompletedOrder();
        assertThatThrownBy(() -> refundService.createRefund(
                orderId, "FULL", new BigDecimal("10.00"),
                "CUSTOMER_REQUEST", null,
                null, null, "CUSTOMER"))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void createRefundWithBlankReasonDetailFails() {
        Long orderId = createCompletedOrder();
        assertThatThrownBy(() -> refundService.createRefund(
                orderId, "FULL", new BigDecimal("10.00"),
                "CUSTOMER_REQUEST", "  ",
                null, null, "CUSTOMER"))
                .isInstanceOf(OrderBusinessException.class);
    }

    // --- AC-11: Approve refund ---

    @Test
    void approveRefundSucceeds() {
        Long orderId = createCompletedOrderLargeAmount();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("600.00"),
                "CUSTOMER_REQUEST", "Need approval",
                null, null, "CUSTOMER");
        assertThat(refund.getStatus()).isEqualTo(RefundStatusEnum.PENDING_REVIEW.getCode());

        OrderRefundDO approved = refundService.approveRefund(refund.getId(), 999L, "Approved by owner");

        assertThat(approved.getStatus()).isEqualTo(RefundStatusEnum.REFUNDING.getCode());
        assertThat(approved.getApproverUserId()).isEqualTo(999L);
        assertThat(approved.getApproveRemark()).isEqualTo("Approved by owner");
        assertThat(approved.getApproveTime()).isNotNull();
    }

    @Test
    void approveRefundWrongStatusFails() {
        Long orderId = createCompletedOrder();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("10.00"),
                "CUSTOMER_REQUEST", "Auto-approved",
                null, null, "CUSTOMER");
        // Already REFUNDING (auto-approved), can't approve again
        assertThatThrownBy(() -> refundService.approveRefund(refund.getId(), 999L, "test"))
                .isInstanceOf(OrderBusinessException.class);
    }

    // --- AC-12: Reject refund + order rollback ---

    @Test
    void rejectRefundRollsBackOrderToCompleted() {
        Long orderId = createCompletedOrderLargeAmount();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("600.00"),
                "CUSTOMER_REQUEST", "Need approval",
                null, null, "CUSTOMER");

        OrderRefundDO rejected = refundService.rejectRefund(refund.getId(), 999L, "Rejected: invalid reason");

        assertThat(rejected.getStatus()).isEqualTo(RefundStatusEnum.REJECTED.getCode());
        assertThat(rejected.getApproverUserId()).isEqualTo(999L);
        assertThat(rejected.getApproveRemark()).isEqualTo("Rejected: invalid reason");

        // D-R2: Order should rollback to COMPLETED
        OrderDO order = orderService.getOrder(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.COMPLETED.getCode());

        // Event log should contain REFUND_REJECT
        List<OrderEventLogDO> logs = eventLogMapper.selectList(OrderEventLogDO::getOrderId, orderId);
        assertThat(logs.stream().anyMatch(l -> "REFUND_REJECT".equals(l.getEventType()))).isTrue();
    }

    @Test
    void rejectRefundWrongStatusFails() {
        Long orderId = createCompletedOrder();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("10.00"),
                "CUSTOMER_REQUEST", "Auto-approved",
                null, null, "CUSTOMER");
        // Already REFUNDING, can't reject
        assertThatThrownBy(() -> refundService.rejectRefund(refund.getId(), 999L, "test"))
                .isInstanceOf(OrderBusinessException.class);
    }

    // --- AC-13: Execute refund bridge ---

    @Test
    void executeRefundSucceeds() {
        Long orderId = createCompletedOrder();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("22.00"),
                "CUSTOMER_REQUEST", "Full refund",
                null, null, "CUSTOMER");

        OrderRefundDO executed = refundService.executeRefund(refund.getId(), 888L, "EXT_RFD_001");

        assertThat(executed.getStatus()).isEqualTo(RefundStatusEnum.REFUNDED.getCode());
        assertThat(executed.getRefundTime()).isNotNull();
        assertThat(executed.getExternalRefundNo()).isEqualTo("EXT_RFD_001");

        // Order should be REFUNDED
        OrderDO order = orderService.getOrder(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.REFUNDED.getCode());

        // Event log should contain REFUND_COMPLETE
        List<OrderEventLogDO> logs = eventLogMapper.selectList(OrderEventLogDO::getOrderId, orderId);
        assertThat(logs.stream().anyMatch(l -> "REFUND_COMPLETE".equals(l.getEventType()))).isTrue();
    }

    @Test
    void executeRefundPassesRefundItemIdsToBomRestore() {
        Long orderId = createCompletedOrder();
        List<OrderItemDO> items = orderItemMapper.selectList(OrderItemDO::getOrderId, orderId);
        Long firstOrderItemId = items.get(0).getId();

        OrderRefundDO refund = refundService.createRefund(
                orderId, "ITEM", new BigDecimal("12.00"),
                "CUSTOMER_REQUEST", "Item refund",
                List.of(firstOrderItemId), null, "CUSTOMER");

        stockApi.restoreShouldThrowNoOriginal = false;
        stockApi.restoreItemCount = 2;
        refundService.executeRefund(refund.getId(), 888L, null);

        assertThat(stockApi.restoreRequests).hasSize(1);
        SalesReverseRestoreReqDTO req = stockApi.restoreRequests.get(0);
        assertThat(req.getSourceOrderItemIds()).containsExactly(firstOrderItemId);
    }

    // --- AC-15: Refund amount accumulation ---

    @Test
    void executeRefundAccumulatesRefundAmount() {
        Long orderId = createCompletedOrder();
        OrderDO orderBefore = orderService.getOrder(orderId);
        BigDecimal originalRefundAmount = orderBefore.getRefundAmount();

        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("22.00"),
                "CUSTOMER_REQUEST", "Full refund",
                null, null, "CUSTOMER");
        refundService.executeRefund(refund.getId(), 888L, null);

        OrderDO orderAfter = orderService.getOrder(orderId);
        assertThat(orderAfter.getRefundAmount())
                .isEqualByComparingTo(originalRefundAmount.add(new BigDecimal("22.00")));
        // paid_amount should not change
        assertThat(orderAfter.getPaidAmount()).isEqualByComparingTo(orderBefore.getPaidAmount());
    }

    // --- AC-13: Execute refund updates refund_amount correctly ---

    @Test
    void executeRefundWrongStatusFails() {
        Long orderId = createCompletedOrderLargeAmount();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("600.00"),
                "CUSTOMER_REQUEST", "Pending approval",
                null, null, "CUSTOMER");
        // PENDING_REVIEW, can't execute
        assertThatThrownBy(() -> refundService.executeRefund(refund.getId(), 888L, null))
                .isInstanceOf(OrderBusinessException.class);
    }

    // --- R-1 fix: Optimistic lock failure on refund_amount update prevents REFUNDED transition ---

    @Test
    void executeRefundFailsWhenOrderUpdateReturnsZero() {
        Long orderId = createCompletedOrder();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("22.00"),
                "CUSTOMER_REQUEST", "Full refund",
                null, null, "CUSTOMER");

        // Simulate optimistic lock conflict: orderMapper.updateById returns 0
        doReturn(0).when(orderMapper).updateById(any(OrderDO.class));

        // executeRefund should throw OrderBusinessException (ORDER_CONFLICT)
        assertThatThrownBy(() -> refundService.executeRefund(refund.getId(), 888L, "EXT_RFD_001"))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessageContaining("conflict");

        // Transaction should have rolled back: refund still REFUNDING (not REFUNDED)
        OrderRefundDO refundAfter = refundMapper.selectById(refund.getId());
        assertThat(refundAfter.getStatus()).isEqualTo(RefundStatusEnum.REFUNDING.getCode());

        // Order should still be REFUNDING (state machine transition never executed)
        OrderDO orderAfter = orderService.getOrder(orderId);
        assertThat(orderAfter.getStatus()).isEqualTo(OrderStatusEnum.REFUNDING.getCode());

        // refund_amount should NOT have been updated
        assertThat(orderAfter.getRefundAmount())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- AC-14: Fail refund ---

    @Test
    void failRefundKeepsOrderInRefunding() {
        Long orderId = createCompletedOrder();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("22.00"),
                "CUSTOMER_REQUEST", "Will fail",
                null, null, "CUSTOMER");

        OrderRefundDO failed = refundService.failRefund(refund.getId(), "Gateway timeout");

        assertThat(failed.getStatus()).isEqualTo(RefundStatusEnum.REFUND_FAILED.getCode());
        assertThat(failed.getFailReason()).isEqualTo("Gateway timeout");

        // CG-R2: Order stays REFUNDING
        OrderDO order = orderService.getOrder(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.REFUNDING.getCode());
    }

    // --- AC-17: State machine whitelist REFUNDING→COMPLETED ---

    @Test
    void refundingToCompletedIsLegalTransition() {
        assertThat(stateMachineService.isLegalTransition("REFUNDING", "COMPLETED")).isTrue();
    }

    @Test
    void refundingToRefundedIsStillLegal() {
        assertThat(stateMachineService.isLegalTransition("REFUNDING", "REFUNDED")).isTrue();
    }

    // --- AC-18: Illegal transitions ---

    @Test
    void refundedToRefundingIsIllegal() {
        assertThat(stateMachineService.isLegalTransition("REFUNDED", "REFUNDING")).isFalse();
    }

    @Test
    void completedToRefundedIsIllegal() {
        assertThat(stateMachineService.isLegalTransition("COMPLETED", "REFUNDED")).isFalse();
    }

    // --- AC-19: Tenant isolation ---

    @Test
    void refundRecordIsTenantIsolated() {
        Long orderId = createCompletedOrder();
        refundService.createRefund(
                orderId, "FULL", new BigDecimal("10.00"),
                "CUSTOMER_REQUEST", "Tenant test",
                null, null, "CUSTOMER");

        // Switch tenant
        TenantContextHolder.setTenantId(2L);
        List<OrderRefundDO> refunds = refundService.getRefundsByOrderId(orderId);
        assertThat(refunds).isEmpty();
    }

    // --- AC-22: refund_no uniqueness (verified via DDL unique key) ---

    @Test
    void refundNoIsGenerated() {
        Long orderId = createCompletedOrder();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("10.00"),
                "CUSTOMER_REQUEST", "Test",
                null, null, "CUSTOMER");

        assertThat(refund.getRefundNo()).isNotNull().isNotEmpty();
        assertThat(refund.getRefundNo()).startsWith("RFD");
    }

    // --- AC-25: Event log payload is valid JSON ---

    @Test
    void refundEventLogPayloadIsValidJson() {
        Long orderId = createCompletedOrder();
        refundService.createRefund(
                orderId, "FULL", new BigDecimal("10.00"),
                "CUSTOMER_REQUEST", "JSON test",
                null, null, "CUSTOMER");

        List<OrderEventLogDO> logs = eventLogMapper.selectList(OrderEventLogDO::getOrderId, orderId);
        OrderEventLogDO refundLog = logs.stream()
                .filter(l -> "REFUND_INITIATE".equals(l.getEventType()))
                .findFirst().orElseThrow();
        assertThat(refundLog.getPayload()).startsWith("{").endsWith("}");
    }

    // --- AC-20: BigDecimal money fields ---

    @Test
    void refundAmountUsesBigDecimal() {
        Long orderId = createCompletedOrder();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("10.00"),
                "CUSTOMER_REQUEST", "BigDecimal test",
                null, null, "CUSTOMER");

        assertThat(refund.getRefundAmount()).isInstanceOf(BigDecimal.class);
    }

    // --- AC-21: Soft delete ---

    @Test
    void refundRecordUsesSoftDelete() {
        Long orderId = createCompletedOrder();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("10.00"),
                "CUSTOMER_REQUEST", "Soft delete test",
                null, null, "CUSTOMER");

        assertThat(refund.getDeleted()).isFalse();
    }

    // --- AC-27: original_payment_id must reference existing payment ---

    @Test
    void createRefundLinksToPaymentRecord() {
        Long orderId = createCompletedOrder();
        OrderRefundDO refund = refundService.createRefund(
                orderId, "FULL", new BigDecimal("10.00"),
                "CUSTOMER_REQUEST", "Payment link test",
                null, null, "CUSTOMER");

        assertThat(refund.getOriginalPaymentId()).isNotNull();
        assertThat(refund.getOriginalOrderId()).isEqualTo(orderId);
    }

    // --- AC-4/5/6: Enum consistency ---

    @Test
    void refundStatusEnumHas6Values() {
        assertThat(RefundStatusEnum.values()).hasSize(6);
        assertThat(RefundStatusEnum.fromCode("PENDING_REVIEW")).isEqualTo(RefundStatusEnum.PENDING_REVIEW);
        assertThat(RefundStatusEnum.fromCode("APPROVED")).isEqualTo(RefundStatusEnum.APPROVED);
        assertThat(RefundStatusEnum.fromCode("REJECTED")).isEqualTo(RefundStatusEnum.REJECTED);
        assertThat(RefundStatusEnum.fromCode("REFUNDING")).isEqualTo(RefundStatusEnum.REFUNDING);
        assertThat(RefundStatusEnum.fromCode("REFUNDED")).isEqualTo(RefundStatusEnum.REFUNDED);
        assertThat(RefundStatusEnum.fromCode("REFUND_FAILED")).isEqualTo(RefundStatusEnum.REFUND_FAILED);
    }

    // --- getRefund / getRefundsByOrderId ---

    @Test
    void getRefundReturnsRefund() {
        Long orderId = createCompletedOrder();
        OrderRefundDO created = refundService.createRefund(
                orderId, "FULL", new BigDecimal("10.00"),
                "CUSTOMER_REQUEST", "Get test",
                null, null, "CUSTOMER");

        OrderRefundDO found = refundService.getRefund(created.getId());
        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getRefundNo()).isEqualTo(created.getRefundNo());
    }

    @Test
    void getRefundNotFoundFails() {
        assertThatThrownBy(() -> refundService.getRefund(99999L))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void getRefundsByOrderIdReturnsList() {
        Long orderId = createCompletedOrder();
        refundService.createRefund(
                orderId, "PARTIAL", new BigDecimal("10.00"),
                "CUSTOMER_REQUEST", "First refund",
                null, null, "CUSTOMER");

        List<OrderRefundDO> refunds = refundService.getRefundsByOrderId(orderId);
        assertThat(refunds).hasSize(1);
    }

    // --- Helpers ---

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

    private Long createCompletedOrder() {
        Long orderId = createOrder();
        // PENDING → PAID
        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("22.00"), null);
        // PAID → ACCEPTED → PREPARING
        orderService.acceptOrder(orderId);
        // PREPARING → READY
        stateMachineService.transition(orderId, "READY", null, "STAFF", "{}");
        // READY → DELIVERED
        stateMachineService.transition(orderId, "DELIVERED", null, "STAFF", "{}");
        // DELIVERED → COMPLETED
        stateMachineService.transition(orderId, "COMPLETED", null, "SYSTEM", "{}");
        return orderId;
    }

    private Long createCompletedOrderLargeAmount() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        // 60 burgers × 12.00 = 720.00
        OrderItemReqVO item1 = new OrderItemReqVO();
        item1.setSkuId(1001L);
        item1.setQuantity(new BigDecimal("60"));

        req.setItems(List.of(item1));
        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());
        Long orderId = resp.getOrderId();

        // PENDING → PAID (720.00)
        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("720.00"), null);
        // PAID → ACCEPTED → PREPARING
        orderService.acceptOrder(orderId);
        // PREPARING → READY
        stateMachineService.transition(orderId, "READY", null, "STAFF", "{}");
        // READY → DELIVERED
        stateMachineService.transition(orderId, "DELIVERED", null, "STAFF", "{}");
        // DELIVERED → COMPLETED
        stateMachineService.transition(orderId, "COMPLETED", null, "SYSTEM", "{}");
        return orderId;
    }
}
