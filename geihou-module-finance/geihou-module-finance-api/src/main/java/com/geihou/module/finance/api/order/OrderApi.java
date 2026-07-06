package com.geihou.module.finance.api.order;

import com.geihou.module.finance.api.order.dto.OrderEventDTO;
import com.geihou.module.finance.api.order.dto.OrderRespDTO;
import com.geihou.module.finance.api.order.dto.OrderSummaryRespDTO;
import com.geihou.module.finance.api.order.dto.RefundCheckRespDTO;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Order API contract used by Geihou modules (finance, BOM, marketing, data platform).
 *
 * <p>This is a pure Java contract in the finance API module. RPC annotations,
 * implementation, caching, and runtime behavior are intentionally out of scope.
 * The HTTP implementation is provided in geihou-module-finance-biz via
 * {@code OrderApiImpl} exposed at {@code /rpc-api/finance/order}.
 *
 * <p>Tenant context: unlike ProductApi which uses implicit TenantContextHolder,
 * OrderApi methods explicitly accept tenantId as the first parameter, following
 * the PRD section 3.3 method signatures exactly.
 *
 * <p>Source: PRD-G1-01 Section 3.3 dubbo-api definition.
 */
public interface OrderApi {

    /**
     * Query order basic information (for finance / BOM / marketing).
     *
     * @param tenantId tenant ID
     * @param orderId  order ID
     * @return order DTO, or {@code null} when not found
     */
    OrderRespDTO getOrder(Long tenantId, Long orderId);

    /**
     * Summarize orders by business date + channel (for finance month-close / data platform).
     *
     * @param tenantId     tenant ID
     * @param businessDate business date
     * @param channel      order channel (null = all channels)
     * @return summary DTO
     */
    OrderSummaryRespDTO summarizeByBusinessDate(Long tenantId, LocalDate businessDate, String channel);

    /**
     * Publish order event (for KDS / inventory / marketing attribution to listen).
     *
     * <p>Implementation deferred to G1-01E (MQ event bus). Current implementation
     * is a stub that logs a warning.
     *
     * @param event order event DTO
     */
    void publishOrderEvent(OrderEventDTO event);

    /**
     * Check whether an order is refundable (amount / time / status).
     *
     * @param tenantId     tenant ID
     * @param orderId      order ID
     * @param refundAmount requested refund amount
     * @return refund check result DTO
     */
    RefundCheckRespDTO checkRefundable(Long tenantId, Long orderId, BigDecimal refundAmount);
}
