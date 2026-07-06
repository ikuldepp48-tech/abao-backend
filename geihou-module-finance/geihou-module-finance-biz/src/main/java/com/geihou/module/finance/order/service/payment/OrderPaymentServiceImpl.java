package com.geihou.module.finance.order.service.payment;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.order.enums.PaymentStatusEnum;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderPaymentDO;
import com.geihou.module.finance.order.dal.mapper.OrderPaymentMapper;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.enums.PaymentMethodEnum;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.framework.OrderErrorCodeConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * Order payment service implementation (INSERT-only).
 *
 * <p>Payment records are created with payment_status=SUCCESS for the
 * staff mark-as-paid payment bridge (D-1 decision).
 * No update or delete operations on order_payment table.
 */
@Service
public class OrderPaymentServiceImpl implements OrderPaymentService {

    private final OrderPaymentMapper paymentMapper;
    private final OrderMapper orderMapper;

    public OrderPaymentServiceImpl(OrderPaymentMapper paymentMapper, OrderMapper orderMapper) {
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderPaymentDO createPayment(Long orderId, String paymentMethod, BigDecimal paymentAmount, String externalNo) {
        return createPayment(orderId, paymentMethod, paymentAmount, externalNo, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderPaymentDO createPayment(Long orderId, String paymentMethod, BigDecimal paymentAmount,
                                        String externalNo, LocalDateTime paymentTime) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // Validate payment method
        PaymentMethodEnum methodEnum = PaymentMethodEnum.fromCode(paymentMethod);

        // Get order
        OrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_NOT_FOUND);
        }

        // Validate amount matches (total - discount)
        BigDecimal expectedAmount = order.getTotalAmount().subtract(order.getDiscountAmount());
        if (paymentAmount.compareTo(expectedAmount) != 0) {
            throw new OrderBusinessException(OrderErrorCodeConstants.PAYMENT_AMOUNT_MISMATCH,
                    "expected=" + expectedAmount + " actual=" + paymentAmount);
        }

        // Create payment record (INSERT-only)
        // Use the provided paymentTime (preserves checkout_session.payment_time for checkout conversion)
        OrderPaymentDO payment = new OrderPaymentDO();
        payment.setTenantId(tenantId);
        payment.setOrderId(orderId);
        payment.setPaymentNo(generatePaymentNo(tenantId));
        payment.setExternalNo(externalNo);
        payment.setPaymentMethod(methodEnum.getCode());
        payment.setPaymentAmount(paymentAmount);
        payment.setPaymentStatus(PaymentStatusEnum.SUCCESS.getCode());
        payment.setInitiatedTime(paymentTime);
        payment.setPaidTime(paymentTime);
        payment.setCreateTime(paymentTime);

        paymentMapper.insert(payment);
        return payment;
    }

    @Override
    public OrderPaymentDO getPaymentByOrderId(Long orderId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");
        return paymentMapper.selectOne(OrderPaymentDO::getOrderId, orderId,
                OrderPaymentDO::getTenantId, tenantId);
    }

    private String generatePaymentNo(Long tenantId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuidSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "PAY" + tenantId + timestamp + uuidSuffix;
    }
}
