package com.geihou.module.finance.order.service;

import com.geihou.common.pojo.PageResult;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderItemDO;
import com.geihou.module.finance.order.dal.dataobject.OrderRefundDO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order service interface for customer order operations.
 *
 * <p>G1-01A first slice: create + query + list.
 * No payment, refund, state machine transitions, or staff/admin operations.
 */
public interface OrderService {

    /**
     * Create a new order (customer-facing).
     *
     * @param reqVO         order creation request
     * @param idempotentKey idempotent key from header
     * @return order creation response
     */
    OrderCreateRespVO createOrder(OrderCreateReqVO reqVO, String idempotentKey);

    /**
     * Get order detail by ID (including order items).
     *
     * @param orderId order ID
     * @return order DO
     */
    OrderDO getOrder(Long orderId);

    /**
     * Get order items by order ID.
     *
     * @param orderId order ID
     * @return list of order item DOs
     */
    List<OrderItemDO> getOrderItems(Long orderId);

    /**
     * List orders for the current customer (paginated).
     *
     * @param customerUserId customer user ID
     * @param pageNo         page number (1-based)
     * @param pageSize       page size
     * @return paginated order list
     */
    PageResult<OrderDO> listMyOrders(Long customerUserId, Integer pageNo, Integer pageSize);

    /**
     * Cancel an order (customer-facing). Only PENDING orders can be cancelled.
     *
     * @param orderId       order ID
     * @param cancelReason  cancel reason
     */
    void cancelOrder(Long orderId, String cancelReason);

    /**
     * Staff manual mark-as-paid (payment bridge, D-1 decision).
     * Creates order_payment record, updates order payment info, transitions PENDING→PAID.
     * All in a single transaction.
     *
     * @param orderId       order ID
     * @param paymentMethod payment method code (ENUM_PAYMENT_METHOD)
     * @param paymentAmount payment amount (must match order total - discount)
     * @param externalNo    external payment number (optional)
     * @return updated order DO
     */
    OrderDO markOrderPaid(Long orderId, String paymentMethod, BigDecimal paymentAmount, String externalNo);

    /**
     * Staff manual mark-as-paid with an explicit payment timestamp.
     *
     * <p>Used by checkout conversion to preserve checkout_session.payment_time
     * for order.payTime and order_payment audit fields. Existing callers
     * (staff mark-as-paid) should use the 4-arg overload which defaults to now().
     *
     * @param orderId       order ID
     * @param paymentMethod payment method code (ENUM_PAYMENT_METHOD)
     * @param paymentAmount payment amount (must match order total - discount)
     * @param externalNo    external payment number (optional)
     * @param paymentTime   explicit payment timestamp (for checkout conversion)
     * @return updated order DO
     */
    OrderDO markOrderPaid(Long orderId, String paymentMethod, BigDecimal paymentAmount,
                          String externalNo, LocalDateTime paymentTime);

    /**
     * Staff accept order: PAID → ACCEPTED → PREPARING (merged step, no KDS yet).
     *
     * <p>Both transitions are executed within a single transaction boundary.
     * If the second transition (ACCEPTED→PREPARING) fails, the first transition
     * (PAID→ACCEPTED) is rolled back too, ensuring atomicity.
     *
     * @param orderId order ID
     * @return updated order DO (in PREPARING status)
     */
    OrderDO acceptOrder(Long orderId);

    /**
     * Customer refund request (G1-01C). Delegates to RefundService.
     * Creates order_refund record and transitions order COMPLETED → REFUNDING.
     *
     * @param orderId        order ID (must be COMPLETED)
     * @param refundType     FULL / PARTIAL / ITEM
     * @param refundAmount   refund amount
     * @param reasonType     refund reason type enum
     * @param reasonDetail   refund reason detail
     * @param refundItemIds  item IDs for ITEM type (nullable)
     * @param operatorUserId operator user ID (nullable)
     * @param operatorRole   operator role
     * @return created refund DO
     */
    OrderRefundDO refundRequest(Long orderId, String refundType, BigDecimal refundAmount,
                                String reasonType, String reasonDetail,
                                List<Long> refundItemIds,
                                Long operatorUserId, String operatorRole);

    /**
     * Create an order from a paid checkout session.
     *
     * <p>G1-04C: Called internally by CheckoutService.convertToOrder.
     * Single @Transactional boundary: create PENDING order + order_items from cart
     * snapshots + CREATE event log + markOrderPaid + conditional link of
     * checkout_session.order_id. No half-linked state possible.
     *
     * <p>Idempotency guard: conditional UPDATE checkout_session SET order_id = ?
     * WHERE order_id IS NULL. If 0 rows affected (race condition), throws
     * RuntimeException to trigger rollback.
     *
     * @param checkoutSessionId checkout session ID (must be PAID, order_id must be null)
     * @return created OrderDO (status = PAID, with payment record and event logs)
     */
    OrderDO createFromCheckout(Long checkoutSessionId);
}
