package com.geihou.module.finance.order.controller.admin.vo;

/**
 * Admin refund approve/reject request VO.
 *
 * <p>Used by POST /admin-api/order/{orderId}/refund/approve.
 * The action field distinguishes approve vs reject.
 */
public class RefundApproveReqVO {

    private Long refundId;
    private String action;
    private String approveRemark;

    public Long getRefundId() { return refundId; }
    public void setRefundId(Long refundId) { this.refundId = refundId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getApproveRemark() { return approveRemark; }
    public void setApproveRemark(String approveRemark) { this.approveRemark = approveRemark; }
}
