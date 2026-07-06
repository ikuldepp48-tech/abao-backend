package com.geihou.module.finance.order.rpc;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.order.OrderApi;
import com.geihou.module.finance.api.order.dto.OrderEventDTO;
import com.geihou.module.finance.api.order.dto.OrderRespDTO;
import com.geihou.module.finance.api.order.dto.OrderSummaryRespDTO;
import com.geihou.module.finance.api.order.dto.RefundCheckRespDTO;
import com.geihou.module.finance.api.order.enums.OrderStatusEnum;
import com.geihou.module.finance.api.order.enums.RefundStatusEnum;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderRefundDO;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.dal.mapper.OrderRefundMapper;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.framework.OrderErrorCodeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP implementation of {@link OrderApi}.
 *
 * <p>Exposed at {@code /rpc-api/finance/order} as an internal RPC endpoint
 * (not admin-api or app-api). Gateway layer should restrict external access.
 *
 * <p>Tenant context: each method validates that the explicit tenantId parameter
 * matches {@link TenantContextHolder#getTenantId()}. On mismatch (or when the
 * context tenantId is null), an {@link OrderBusinessException} with
 * {@link OrderErrorCodeConstants#ORDER_TENANT_MISMATCH} is thrown (fail closed,
 * error code 1001021). No new error codes are introduced.
 *
 * <p>No authentication is implemented in this slice — gateway protection is required.
 */
@RestController
@RequestMapping("/rpc-api/finance/order")
public class OrderApiImpl implements OrderApi {

    private static final Logger log = LoggerFactory.getLogger(OrderApiImpl.class);

    /**
     * Refund approval threshold (matches RefundServiceImpl#REFUND_APPROVE_THRESHOLD).
     * Refund amounts greater than this value require approval.
     */
    private static final BigDecimal REFUND_APPROVE_THRESHOLD = BigDecimal.valueOf(500);

    private final OrderMapper orderMapper;
    private final OrderRefundMapper refundMapper;

    public OrderApiImpl(OrderMapper orderMapper, OrderRefundMapper refundMapper) {
        this.orderMapper = orderMapper;
        this.refundMapper = refundMapper;
    }

    // ===== getOrder =====

    @Override
    @GetMapping("/{orderId}")
    public OrderRespDTO getOrder(Long tenantId, Long orderId) {
        requireTenantMatch(tenantId);

        OrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            return null;
        }
        // Defensive: ensure the order belongs to the explicit tenantId
        if (!Objects.equals(order.getTenantId(), tenantId)) {
            throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_TENANT_MISMATCH);
        }
        return toOrderRespDTO(order);
    }

    // ===== summarizeByBusinessDate =====

    @Override
    @GetMapping("/summary")
    public OrderSummaryRespDTO summarizeByBusinessDate(Long tenantId, LocalDate businessDate, String channel) {
        requireTenantMatch(tenantId);

        if (channel == null || channel.isBlank()) {
            // All-channel summary via selectDailySummary (shopId=null per CG-OA1)
            Map<String, Object> row = orderMapper.selectDailySummary(tenantId, businessDate, null);
            return buildSummaryFromDailyRow(row, businessDate, null);
        }

        // Specific channel: use selectDailySummaryByChannel + Java post-filter
        List<Map<String, Object>> rows = orderMapper.selectDailySummaryByChannel(tenantId, businessDate, null);
        Map<String, Object> matched = null;
        for (Map<String, Object> row : rows) {
            String rowChannel = getString(row, "channel");
            if (channel.equals(rowChannel)) {
                matched = row;
                break;
            }
        }
        if (matched == null) {
            // No matching channel: return zero-value summary
            return buildZeroSummary(businessDate, channel);
        }
        return buildSummaryFromChannelRow(matched, businessDate, channel);
    }

    // ===== publishOrderEvent (stub) =====

    /**
     * Publish order event (for KDS / inventory / marketing attribution to listen).
     *
     * @implSpec Stub — G1-01E deferred. MQ event bus infrastructure is not yet
     * available. This method logs a warning and does NOT publish any event.
     * This method does NOT write to order_event_log — that is the responsibility
     * of internal state machine / order / refund services within their own
     * transactions. When G1-01E MQ is implemented, this method will publish to
     * the appropriate MQ topic (order.created / order.paid / order.ready /
     * order.completed / order.refunded).
     *
     * @param event order event DTO
     */
    @Override
    @PostMapping("/event")
    public void publishOrderEvent(OrderEventDTO event) {
        if (event == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_TENANT_MISMATCH,
                    "event is null");
        }
        requireTenantMatch(event.getTenantId());

        log.warn("publishOrderEvent called but MQ not yet implemented (G1-01E deferred). "
                + "Event: orderId={}, type={}", event.getOrderId(), event.getEventType());
    }

    // ===== checkRefundable =====

    @Override
    @GetMapping("/{orderId}/refund-check")
    public RefundCheckRespDTO checkRefundable(Long tenantId, Long orderId, BigDecimal refundAmount) {
        requireTenantMatch(tenantId);

        RefundCheckRespDTO resp = new RefundCheckRespDTO();

        OrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            resp.setRefundable(false);
            resp.setMaxRefundableAmount(BigDecimal.ZERO);
            resp.setRequiresApproval(false);
            resp.setReason("ORDER_NOT_FOUND");
            return resp;
        }
        // Defensive: ensure the order belongs to the explicit tenantId
        if (!Objects.equals(order.getTenantId(), tenantId)) {
            throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_TENANT_MISMATCH);
        }

        // Compute max refundable = paidAmount - existingRefundTotal
        BigDecimal existingRefundTotal = getExistingRefundTotal(orderId);
        BigDecimal maxRefundable = order.getPaidAmount() == null
                ? BigDecimal.ZERO
                : order.getPaidAmount().subtract(existingRefundTotal);

        resp.setMaxRefundableAmount(maxRefundable);
        resp.setRequiresApproval(refundAmount != null
                && refundAmount.compareTo(REFUND_APPROVE_THRESHOLD) > 0);

        // (a) Validate order status: only COMPLETED can be refunded
        if (!OrderStatusEnum.COMPLETED.getCode().equals(order.getStatus())) {
            resp.setRefundable(false);
            resp.setReason("ORDER_NOT_COMPLETED");
            return resp;
        }

        // (b) Validate refund amount > 0
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            resp.setRefundable(false);
            resp.setReason("REFUND_AMOUNT_INVALID");
            return resp;
        }

        // (c) Validate refund amount + existing refunds <= paid_amount
        if (refundAmount.compareTo(maxRefundable) > 0) {
            resp.setRefundable(false);
            resp.setReason("REFUND_EXCEEDS_PAID");
            return resp;
        }

        // All checks passed
        resp.setRefundable(true);
        return resp;
    }

    // ===== Private helpers =====

    /**
     * Validate that the explicit tenantId parameter matches the TenantContextHolder.
     * Fail closed with ORDER_TENANT_MISMATCH (1001021) if:
     * - context tenantId is null, or
     * - context tenantId != explicit tenantId
     */
    private void requireTenantMatch(Long tenantId) {
        Long contextTenantId = TenantContextHolder.getTenantId();
        if (contextTenantId == null || !contextTenantId.equals(tenantId)) {
            throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_TENANT_MISMATCH);
        }
    }

    private OrderRespDTO toOrderRespDTO(OrderDO order) {
        OrderRespDTO dto = new OrderRespDTO();
        dto.setId(order.getId());
        dto.setTenantId(order.getTenantId());
        dto.setOrderNo(order.getOrderNo());
        dto.setBusinessDate(order.getBusinessDate());
        dto.setChannel(order.getChannel());
        dto.setOrderType(order.getOrderType());
        dto.setCustomerUserId(order.getCustomerUserId());
        dto.setShopId(order.getShopId());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setPaidAmount(order.getPaidAmount());
        dto.setDiscountAmount(order.getDiscountAmount());
        dto.setRefundAmount(order.getRefundAmount());
        dto.setPlatformFee(order.getPlatformFee());
        dto.setStatus(order.getStatus());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setPayTime(order.getPayTime());
        dto.setCreateTime(order.getCreateTime());
        dto.setCompletedTime(order.getCompletedTime());
        dto.setCouponId(order.getCouponId());
        dto.setPromotionIds(order.getPromotionIds());
        return dto;
    }

    private OrderSummaryRespDTO buildSummaryFromDailyRow(Map<String, Object> row,
                                                         LocalDate businessDate, String channel) {
        OrderSummaryRespDTO dto = new OrderSummaryRespDTO();
        dto.setBusinessDate(businessDate);
        dto.setChannel(channel); // null for all-channel
        dto.setOrderCount(getInt(row, "order_count"));
        dto.setTotalAmount(getDecimal(row, "total_amount"));
        dto.setPaidAmount(getDecimal(row, "paid_amount"));
        dto.setDiscountAmount(getDecimal(row, "discount_amount"));
        dto.setRefundAmount(getDecimal(row, "refund_amount"));
        dto.setPlatformFee(getDecimal(row, "platform_fee"));
        dto.setNetRevenue(computeNetRevenue(dto.getPaidAmount(), dto.getRefundAmount(), dto.getPlatformFee()));
        return dto;
    }

    private OrderSummaryRespDTO buildSummaryFromChannelRow(Map<String, Object> row,
                                                           LocalDate businessDate, String channel) {
        OrderSummaryRespDTO dto = new OrderSummaryRespDTO();
        dto.setBusinessDate(businessDate);
        dto.setChannel(channel);
        dto.setOrderCount(getInt(row, "order_count"));
        dto.setTotalAmount(getDecimal(row, "total_amount"));
        dto.setPaidAmount(getDecimal(row, "paid_amount"));
        dto.setDiscountAmount(getDecimal(row, "discount_amount"));
        dto.setRefundAmount(getDecimal(row, "refund_amount"));
        dto.setPlatformFee(getDecimal(row, "platform_fee"));
        dto.setNetRevenue(computeNetRevenue(dto.getPaidAmount(), dto.getRefundAmount(), dto.getPlatformFee()));
        return dto;
    }

    private OrderSummaryRespDTO buildZeroSummary(LocalDate businessDate, String channel) {
        OrderSummaryRespDTO dto = new OrderSummaryRespDTO();
        dto.setBusinessDate(businessDate);
        dto.setChannel(channel);
        dto.setOrderCount(0);
        dto.setTotalAmount(BigDecimal.ZERO);
        dto.setPaidAmount(BigDecimal.ZERO);
        dto.setDiscountAmount(BigDecimal.ZERO);
        dto.setRefundAmount(BigDecimal.ZERO);
        dto.setPlatformFee(BigDecimal.ZERO);
        dto.setNetRevenue(BigDecimal.ZERO);
        return dto;
    }

    /**
     * netRevenue = paidAmount - refundAmount - platformFee (CG-DS2 already ruled).
     */
    private BigDecimal computeNetRevenue(BigDecimal paidAmount, BigDecimal refundAmount, BigDecimal platformFee) {
        BigDecimal paid = paidAmount == null ? BigDecimal.ZERO : paidAmount;
        BigDecimal refund = refundAmount == null ? BigDecimal.ZERO : refundAmount;
        BigDecimal fee = platformFee == null ? BigDecimal.ZERO : platformFee;
        return paid.subtract(refund).subtract(fee);
    }

    /**
     * Get total refund amount for active refund records (PENDING_REVIEW, APPROVED,
     * REFUNDING, REFUNDED). Mirrors RefundServiceImpl#getExistingRefundTotal logic
     * as a read-only check.
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

    // --- Map helpers (case-insensitive key lookup) ---

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
