package com.geihou.module.finance.order.service.refund;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.order.enums.OrderStatusEnum;
import com.geihou.module.finance.api.order.enums.RefundStatusEnum;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderEventLogDO;
import com.geihou.module.finance.order.dal.dataobject.OrderPaymentDO;
import com.geihou.module.finance.order.dal.dataobject.OrderRefundDO;
import com.geihou.module.finance.order.dal.mapper.OrderEventLogMapper;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.dal.mapper.OrderPaymentMapper;
import com.geihou.module.finance.order.dal.mapper.OrderRefundMapper;
import com.geihou.module.finance.order.enums.OrderEventTypeEnum;
import com.geihou.module.finance.order.enums.RefundReasonTypeEnum;
import com.geihou.module.finance.order.enums.RefundTypeEnum;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.framework.OrderErrorCodeConstants;
import com.geihou.module.finance.order.service.statemachine.OrderStateMachineService;
import com.geihou.module.finance.stock.StockIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Refund service implementation.
 *
 * <p>CG-R1: Order transitions COMPLETED → REFUNDING at refund submission time.
 * CG-R2: No REFUND_FAILED order status; refund failure stays in refund record.
 * CG-R3: RefundReasonTypeEnum with 5 values.
 * CG-R4: Staff/owner execute-refund bridge endpoint.
 * CG-R5: Approval threshold = 500 (service constant).
 * D-R2: REFUNDING → COMPLETED on refund rejection.
 * D-R3: Amount validation in-service, no external calls.
 */
@Service
public class RefundServiceImpl implements RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundServiceImpl.class);

    private static final BigDecimal REFUND_APPROVE_THRESHOLD = BigDecimal.valueOf(500);

    private final OrderRefundMapper refundMapper;
    private final OrderMapper orderMapper;
    private final OrderPaymentMapper paymentMapper;
    private final OrderEventLogMapper eventLogMapper;
    private final OrderStateMachineService stateMachineService;
    private final StockIntegrationService stockIntegrationService;

    public RefundServiceImpl(OrderRefundMapper refundMapper,
                             OrderMapper orderMapper,
                             OrderPaymentMapper paymentMapper,
                             OrderEventLogMapper eventLogMapper,
                             OrderStateMachineService stateMachineService,
                             StockIntegrationService stockIntegrationService) {
        this.refundMapper = refundMapper;
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.eventLogMapper = eventLogMapper;
        this.stateMachineService = stateMachineService;
        this.stockIntegrationService = stockIntegrationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderRefundDO createRefund(Long orderId, String refundType, BigDecimal refundAmount,
                                      String reasonType, String reasonDetail,
                                      List<Long> refundItemIds,
                                      Long operatorUserId, String operatorRole) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // Validate refund type
        RefundTypeEnum typeEnum = RefundTypeEnum.fromCode(refundType);

        // Validate reason
        if (reasonType == null || reasonType.isBlank()) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_REASON_REQUIRED);
        }
        RefundReasonTypeEnum.fromCode(reasonType); // validates enum value
        if (reasonDetail == null || reasonDetail.isBlank()) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_REASON_REQUIRED);
        }

        // Validate refund amount > 0
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_EXCEEDS_PAID,
                    "refund amount must be positive");
        }

        // (a) Validate order exists and is COMPLETED
        OrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_NOT_FOUND);
        }
        if (!OrderStatusEnum.COMPLETED.getCode().equals(order.getStatus())) {
            throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_NOT_COMPLETED,
                    "current status=" + order.getStatus());
        }

        // (b) Validate refund amount: refund_amount + existing refunds ≤ paid_amount (D-R3)
        BigDecimal existingRefundTotal = getExistingRefundTotal(orderId);
        BigDecimal maxRefundable = order.getPaidAmount().subtract(existingRefundTotal);
        if (refundAmount.compareTo(maxRefundable) > 0) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_EXCEEDS_PAID,
                    "requested=" + refundAmount + " maxRefundable=" + maxRefundable);
        }

        // (c) Get original payment record
        OrderPaymentDO payment = paymentMapper.selectOne(
                OrderPaymentDO::getOrderId, orderId,
                OrderPaymentDO::getTenantId, tenantId
        );
        if (payment == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_PAYMENT_NOT_FOUND);
        }

        // (d) Create order_refund record
        LocalDateTime now = LocalDateTime.now();
        OrderRefundDO refund = new OrderRefundDO();
        refund.setTenantId(tenantId);
        refund.setRefundNo(generateRefundNo(tenantId));
        refund.setOriginalOrderId(orderId);
        refund.setOriginalPaymentId(payment.getId());
        refund.setRefundAmount(refundAmount);
        refund.setRefundType(typeEnum.getCode());
        if (refundItemIds != null && !refundItemIds.isEmpty()) {
            refund.setRefundItemIds(refundItemIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")));
        }
        refund.setReasonType(reasonType);
        refund.setReasonDetail(reasonDetail);
        refund.setCreator(operatorRole != null ? operatorRole : "");
        refund.setCreateTime(now);
        refund.setUpdater(operatorRole != null ? operatorRole : "");
        refund.setUpdateTime(now);
        refund.setDeleted(false);

        // CG-R5: Determine if approval is needed
        boolean needsApproval = refundAmount.compareTo(REFUND_APPROVE_THRESHOLD) > 0;
        if (needsApproval) {
            refund.setStatus(RefundStatusEnum.PENDING_REVIEW.getCode());
        } else {
            // Low amount: auto-approve → REFUNDING (CG-R1 merged steps)
            refund.setStatus(RefundStatusEnum.REFUNDING.getCode());
            refund.setApproverUserId(operatorUserId);
            refund.setApproveTime(now);
            refund.setApproveRemark("Auto-approved (amount ≤ threshold)");
        }

        refundMapper.insert(refund);

        // (e) Order status transition COMPLETED → REFUNDING (CG-R1)
        String payload = "{\"refundId\":" + refund.getId() +
                ",\"refundNo\":\"" + refund.getRefundNo() + "\"" +
                ",\"refundAmount\":" + refundAmount +
                ",\"reasonType\":\"" + reasonType + "\"}";
        stateMachineService.transition(orderId, OrderStatusEnum.REFUNDING.getCode(),
                operatorUserId, operatorRole, payload);

        return refund;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderRefundDO approveRefund(Long refundId, Long approverUserId, String approveRemark) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        OrderRefundDO refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_NOT_FOUND);
        }

        if (!RefundStatusEnum.PENDING_REVIEW.getCode().equals(refund.getStatus())) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_STATUS_INVALID,
                    "expected PENDING_REVIEW, actual=" + refund.getStatus());
        }

        // PENDING_REVIEW → APPROVED → REFUNDING (merged, CG-R1)
        LocalDateTime now = LocalDateTime.now();
        refund.setStatus(RefundStatusEnum.REFUNDING.getCode());
        refund.setApproverUserId(approverUserId);
        refund.setApproveTime(now);
        refund.setApproveRemark(approveRemark);
        refund.setUpdater("OWNER");
        refund.setUpdateTime(now);
        refundMapper.updateById(refund);

        return refund;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderRefundDO rejectRefund(Long refundId, Long approverUserId, String approveRemark) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        OrderRefundDO refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_NOT_FOUND);
        }

        if (!RefundStatusEnum.PENDING_REVIEW.getCode().equals(refund.getStatus())) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_STATUS_INVALID,
                    "expected PENDING_REVIEW, actual=" + refund.getStatus());
        }

        // Refund record: PENDING_REVIEW → REJECTED
        LocalDateTime now = LocalDateTime.now();
        refund.setStatus(RefundStatusEnum.REJECTED.getCode());
        refund.setApproverUserId(approverUserId);
        refund.setApproveTime(now);
        refund.setApproveRemark(approveRemark);
        refund.setUpdater("OWNER");
        refund.setUpdateTime(now);
        refundMapper.updateById(refund);

        // D-R2: Order REFUNDING → COMPLETED (rollback)
        Long orderId = refund.getOriginalOrderId();
        String payload = "{\"refundId\":" + refund.getId() +
                ",\"reason\":\"refund rejected\"}";
        stateMachineService.transition(orderId, OrderStatusEnum.COMPLETED.getCode(),
                approverUserId, "OWNER", payload);

        // Write additional REFUND_REJECT event log
        writeEventLog(orderId, "REFUNDING", "COMPLETED",
                OrderEventTypeEnum.REFUND_REJECT, approverUserId, "OWNER", payload, now);

        return refund;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderRefundDO executeRefund(Long refundId, Long operatorUserId, String externalRefundNo) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        OrderRefundDO refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_NOT_FOUND);
        }

        if (!RefundStatusEnum.REFUNDING.getCode().equals(refund.getStatus())) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_STATUS_INVALID,
                    "expected REFUNDING, actual=" + refund.getStatus());
        }

        Long orderId = refund.getOriginalOrderId();
        OrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_NOT_FOUND);
        }

        // Refund record: REFUNDING → REFUNDED
        LocalDateTime now = LocalDateTime.now();
        refund.setStatus(RefundStatusEnum.REFUNDED.getCode());
        refund.setRefundTime(now);
        if (externalRefundNo != null && !externalRefundNo.isBlank()) {
            refund.setExternalRefundNo(externalRefundNo);
        }
        refund.setUpdater("STAFF");
        refund.setUpdateTime(now);
        refundMapper.updateById(refund);

        // Update orders.refund_amount BEFORE state machine transition
        // (state machine uses optimistic lock; updating after would cause version conflict)
        BigDecimal newRefundAmount = order.getRefundAmount().add(refund.getRefundAmount());
        order.setRefundAmount(newRefundAmount);
        order.setUpdater("STAFF");
        order.setUpdateTime(now);
        int updatedRows = orderMapper.updateById(order);
        if (updatedRows != 1) {
            // Optimistic lock conflict (@Version): another thread modified the order
            // between read and update. Rollback transaction to prevent stale refund_amount.
            throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_CONFLICT,
                    "refund_amount update failed, updated rows=" + updatedRows);
        }

        // Order: REFUNDING → REFUNDED
        String payload = "{\"refundId\":" + refund.getId() +
                ",\"refundAmount\":" + refund.getRefundAmount() +
                ",\"externalRefundNo\":\"" + (externalRefundNo != null ? externalRefundNo : "") + "\"}";
        stateMachineService.transition(orderId, OrderStatusEnum.REFUNDED.getCode(),
                operatorUserId, "STAFF", payload);

        // G2-02H-3: Restore original BOM raw-material consumption for the refunded sale source.
        // Uses the same sourceModule / sourceRecordId / referenceNo that were used for
        // salesOutWithBomReverse so supplychain can locate the original CONSUME_OUT events.
        // Non-BOM orders (no original CONSUME_OUT events) are a no-op inside restoreForRefund;
        // conflicts from concurrent restores propagate and roll back this transaction.
        List<Long> sourceOrderItemIds = parseRefundItemIds(refund.getRefundItemIds());
        if (!RefundTypeEnum.FULL.getCode().equals(refund.getRefundType()) && sourceOrderItemIds.isEmpty()) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_ITEM_IDS_INVALID,
                    "partial/item refund requires refundItemIds for BOM restore safety");
        }
        try {
            stockIntegrationService.restoreForRefund(
                    tenantId,
                    orderId,
                    order.getOrderNo(),
                    refund.getId(),
                    operatorUserId,
                    sourceOrderItemIds);
        } catch (Exception e) {
            log.error("BOM refund restore failed for orderId={}, refundId={}, rolling back executeRefund: {}",
                    orderId, refund.getId(), e.getMessage(), e);
            throw e;
        }

        return refund;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderRefundDO failRefund(Long refundId, String failReason) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        OrderRefundDO refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_NOT_FOUND);
        }

        if (!RefundStatusEnum.REFUNDING.getCode().equals(refund.getStatus())) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_STATUS_INVALID,
                    "expected REFUNDING, actual=" + refund.getStatus());
        }

        // CG-R2: Refund record REFUNDING → REFUND_FAILED, order stays REFUNDING
        LocalDateTime now = LocalDateTime.now();
        refund.setStatus(RefundStatusEnum.REFUND_FAILED.getCode());
        refund.setFailReason(failReason);
        refund.setUpdater("SYSTEM");
        refund.setUpdateTime(now);
        refundMapper.updateById(refund);

        return refund;
    }

    @Override
    public OrderRefundDO getRefund(Long refundId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        OrderRefundDO refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_NOT_FOUND);
        }
        return refund;
    }

    @Override
    public List<OrderRefundDO> getRefundsByOrderId(Long orderId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        return refundMapper.selectList(
                OrderRefundDO::getOriginalOrderId, orderId,
                OrderRefundDO::getTenantId, tenantId
        );
    }

    // --- Private helpers ---

    /**
     * Get total refund amount for active refund records (PENDING_REVIEW, APPROVED, REFUNDING, REFUNDED).
     * PENDING_REVIEW also occupies amount quota to prevent parallel over-refund (D-R3).
     */
    private BigDecimal getExistingRefundTotal(Long orderId) {
        List<OrderRefundDO> existingRefunds = refundMapper.selectList(
                OrderRefundDO::getOriginalOrderId, orderId
        );
        BigDecimal total = BigDecimal.ZERO;
        for (OrderRefundDO r : existingRefunds) {
            if (r.getStatus() != null &&
                    (r.getStatus().equals(RefundStatusEnum.PENDING_REVIEW.getCode()) ||
                     r.getStatus().equals(RefundStatusEnum.APPROVED.getCode()) ||
                     r.getStatus().equals(RefundStatusEnum.REFUNDING.getCode()) ||
                     r.getStatus().equals(RefundStatusEnum.REFUNDED.getCode()))) {
                total = total.add(r.getRefundAmount());
            }
        }
        return total;
    }

    private String generateRefundNo(Long tenantId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuidSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "RFD" + tenantId + timestamp + uuidSuffix;
    }

    private List<Long> parseRefundItemIds(String refundItemIds) {
        if (refundItemIds == null || refundItemIds.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.stream(refundItemIds.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(Long::valueOf)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new OrderBusinessException(OrderErrorCodeConstants.REFUND_ITEM_IDS_INVALID,
                    "refundItemIds=" + refundItemIds);
        }
    }

    private void writeEventLog(Long orderId, String beforeStatus, String afterStatus,
                               OrderEventTypeEnum eventType, Long operatorUserId,
                               String operatorRole, String payload, LocalDateTime eventTime) {
        OrderDO order = orderMapper.selectById(orderId);
        OrderEventLogDO eventLog = new OrderEventLogDO();
        eventLog.setTenantId(order.getTenantId());
        eventLog.setOrderId(orderId);
        eventLog.setEventType(eventType.getCode());
        eventLog.setBeforeStatus(beforeStatus);
        eventLog.setAfterStatus(afterStatus);
        eventLog.setOperatorUserId(operatorUserId);
        eventLog.setOperatorRole(operatorRole != null ? operatorRole : "SYSTEM");
        eventLog.setPayload(payload != null ? payload : "{}");
        eventLog.setEventTime(eventTime);
        eventLog.setCreateTime(eventTime);
        eventLogMapper.insert(eventLog);
    }
}
