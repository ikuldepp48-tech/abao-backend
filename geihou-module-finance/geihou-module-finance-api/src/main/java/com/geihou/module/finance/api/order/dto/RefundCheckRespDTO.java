package com.geihou.module.finance.api.order.dto;

import java.math.BigDecimal;

/**
 * Refund check response DTO for OrderApi.checkRefundable.
 *
 * <p>CG-OA2 (G1-01F-contract): 4 fields only — refundable, maxRefundableAmount,
 * requiresApproval, reason. No tenantId/orderId echo. No currentRefundAmount.
 * reason is a short machine-readable text (e.g. "ORDER_NOT_COMPLETED",
 * "REFUND_EXCEEDS_PAID"), not PII.
 */
public class RefundCheckRespDTO {

    private Boolean refundable;
    private BigDecimal maxRefundableAmount;
    private Boolean requiresApproval;
    private String reason;

    public Boolean getRefundable() { return refundable; }
    public void setRefundable(Boolean refundable) { this.refundable = refundable; }

    public BigDecimal getMaxRefundableAmount() { return maxRefundableAmount; }
    public void setMaxRefundableAmount(BigDecimal maxRefundableAmount) { this.maxRefundableAmount = maxRefundableAmount; }

    public Boolean getRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(Boolean requiresApproval) { this.requiresApproval = requiresApproval; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
