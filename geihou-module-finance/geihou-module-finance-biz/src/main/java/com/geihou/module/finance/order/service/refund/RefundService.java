package com.geihou.module.finance.order.service.refund;

import com.geihou.module.finance.order.dal.dataobject.OrderRefundDO;

import java.math.BigDecimal;
import java.util.List;

/**
 * Refund service interface for order refund lifecycle management.
 *
 * <p>G1-01C refund chain slice: create, approve, reject, execute, fail.
 * All operations are transactional and write order_event_log entries.
 */
public interface RefundService {

    /**
     * Create a refund request for an order.
     *
     * <p>CG-R1: Order transitions COMPLETED → REFUNDING at submission time.
     * If refund_amount ≤ 500 (CG-R5), refund record auto-approves to REFUNDING.
     * If refund_amount > 500, refund record stays PENDING_REVIEW for owner approval.
     *
     * @param orderId        original order ID (must be COMPLETED)
     * @param refundType     FULL / PARTIAL / ITEM
     * @param refundAmount   refund amount (BigDecimal)
     * @param reasonType     CUSTOMER_REQUEST / QUALITY_ISSUE / WRONG_ORDER / OUT_OF_STOCK / OTHER
     * @param reasonDetail   detailed reason (required, non-blank)
     * @param refundItemIds  item IDs for ITEM type refund (nullable)
     * @param operatorUserId operator user ID (nullable for anonymous customer)
     * @param operatorRole   CUSTOMER / STAFF / OWNER
     * @return created refund DO
     */
    OrderRefundDO createRefund(Long orderId, String refundType, BigDecimal refundAmount,
                               String reasonType, String reasonDetail,
                               List<Long> refundItemIds,
                               Long operatorUserId, String operatorRole);

    /**
     * Approve a refund request (owner/admin).
     *
     * <p>Refund record: PENDING_REVIEW → APPROVED → REFUNDING (merged steps).
     * Order stays REFUNDING (already transitioned at submission per CG-R1).
     *
     * @param refundId       refund record ID
     * @param approverUserId approver user ID
     * @param approveRemark  approval remark
     * @return updated refund DO
     */
    OrderRefundDO approveRefund(Long refundId, Long approverUserId, String approveRemark);

    /**
     * Reject a refund request (owner/admin).
     *
     * <p>Refund record: PENDING_REVIEW → REJECTED.
     * Order: REFUNDING → COMPLETED (D-R2 rollback).
     *
     * @param refundId       refund record ID
     * @param approverUserId approver user ID
     * @param approveRemark  rejection remark (required)
     * @return updated refund DO
     */
    OrderRefundDO rejectRefund(Long refundId, Long approverUserId, String approveRemark);

    /**
     * Execute a refund (staff bridge, CG-R4).
     *
     * <p>Refund record: REFUNDING → REFUNDED.
     * Order: REFUNDING → REFUNDED.
     * Updates orders.refund_amount (accumulated).
     *
     * @param refundId          refund record ID
     * @param operatorUserId    operator user ID
     * @param externalRefundNo  external refund number (optional)
     * @return updated refund DO
     */
    OrderRefundDO executeRefund(Long refundId, Long operatorUserId, String externalRefundNo);

    /**
     * Mark a refund as failed (internal call).
     *
     * <p>CG-R2: Refund record: REFUNDING → REFUND_FAILED.
     * Order stays REFUNDING (no status change).
     *
     * @param refundId   refund record ID
     * @param failReason failure reason
     * @return updated refund DO
     */
    OrderRefundDO failRefund(Long refundId, String failReason);

    /**
     * Get a refund record by ID.
     *
     * @param refundId refund record ID
     * @return refund DO
     */
    OrderRefundDO getRefund(Long refundId);

    /**
     * List refund records for an order.
     *
     * @param orderId order ID
     * @return list of refund DOs
     */
    List<OrderRefundDO> getRefundsByOrderId(Long orderId);
}
