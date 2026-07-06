package com.geihou.module.supplychain.stock.controller.admin.vo;

/**
 * Request VO for approving a stock loss/scrap record.
 *
 * <p>Source: TASK-G2-02I-3 Section 5.2.
 */
public class StockLossApproveReqVO {

    private Long lossId;
    private Long tenantId;
    private Long approverUserId;

    // --- Getters and Setters ---

    public Long getLossId() { return lossId; }
    public void setLossId(Long lossId) { this.lossId = lossId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getApproverUserId() { return approverUserId; }
    public void setApproverUserId(Long approverUserId) { this.approverUserId = approverUserId; }
}
