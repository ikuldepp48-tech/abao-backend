package com.geihou.module.supplychain.stock.controller.admin.vo;

/**
 * Request VO for rejecting a stock loss/scrap record.
 *
 * <p>Source: TASK-G2-02I-3 Section 5.3.
 */
public class StockLossRejectReqVO {

    private Long lossId;
    private Long tenantId;
    private Long approverUserId;
    private String rejectReason;

    // --- Getters and Setters ---

    public Long getLossId() { return lossId; }
    public void setLossId(Long lossId) { this.lossId = lossId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getApproverUserId() { return approverUserId; }
    public void setApproverUserId(Long approverUserId) { this.approverUserId = approverUserId; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
}
