package com.geihou.module.finance.order.controller.app.staff.vo;

/**
 * Staff execute refund request VO.
 *
 * <p>Used by POST /app-api/staff/order/{orderId}/execute-refund.
 */
public class StaffExecuteRefundReqVO {

    private Long refundId;
    private String externalRefundNo;

    public Long getRefundId() { return refundId; }
    public void setRefundId(Long refundId) { this.refundId = refundId; }

    public String getExternalRefundNo() { return externalRefundNo; }
    public void setExternalRefundNo(String externalRefundNo) { this.externalRefundNo = externalRefundNo; }
}
