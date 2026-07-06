package com.geihou.module.finance.order.controller.app.customer.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Customer refund request VO.
 *
 * <p>Used by POST /app-api/customer/order/{orderId}/refund-request.
 */
public class RefundRequestReqVO {

    private String refundType;
    private BigDecimal refundAmount;
    private List<Long> refundItemIds;
    private String reasonType;
    private String reasonDetail;

    public String getRefundType() { return refundType; }
    public void setRefundType(String refundType) { this.refundType = refundType; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public List<Long> getRefundItemIds() { return refundItemIds; }
    public void setRefundItemIds(List<Long> refundItemIds) { this.refundItemIds = refundItemIds; }

    public String getReasonType() { return reasonType; }
    public void setReasonType(String reasonType) { this.reasonType = reasonType; }

    public String getReasonDetail() { return reasonDetail; }
    public void setReasonDetail(String reasonDetail) { this.reasonDetail = reasonDetail; }
}
