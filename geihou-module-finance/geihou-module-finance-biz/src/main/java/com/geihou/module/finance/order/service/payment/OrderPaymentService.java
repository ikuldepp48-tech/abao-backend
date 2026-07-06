package com.geihou.module.finance.order.service.payment;

import com.geihou.module.finance.order.dal.dataobject.OrderPaymentDO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order payment service (INSERT-only).
 *
 * <p>Creates payment records in order_payment table.
 * Payment records are immutable after creation.
 */
public interface OrderPaymentService {

    /**
     * Create a payment record for an order.
     *
     * <p>Uses the current system time for initiated/paid timestamps.
     *
     * @param orderId       order ID
     * @param paymentMethod payment method code (ENUM_PAYMENT_METHOD)
     * @param paymentAmount payment amount (must match order total - discount)
     * @param externalNo    external payment number (optional)
     * @return created payment DO
     */
    OrderPaymentDO createPayment(Long orderId, String paymentMethod, BigDecimal paymentAmount, String externalNo);

    /**
     * Create a payment record for an order with an explicit payment timestamp.
     *
     * <p>Used by checkout conversion to preserve checkout_session.payment_time
     * for the order_payment audit fields (initiatedTime, paidTime).
     *
     * @param orderId       order ID
     * @param paymentMethod payment method code (ENUM_PAYMENT_METHOD)
     * @param paymentAmount payment amount (must match order total - discount)
     * @param externalNo    external payment number (optional)
     * @param paymentTime   explicit payment timestamp (for checkout conversion)
     * @return created payment DO
     */
    OrderPaymentDO createPayment(Long orderId, String paymentMethod, BigDecimal paymentAmount,
                                 String externalNo, LocalDateTime paymentTime);

    /**
     * Get payment record by order ID.
     *
     * @param orderId order ID
     * @return payment DO or null
     */
    OrderPaymentDO getPaymentByOrderId(Long orderId);
}
