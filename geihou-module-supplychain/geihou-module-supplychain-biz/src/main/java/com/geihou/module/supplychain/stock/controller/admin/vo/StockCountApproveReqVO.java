package com.geihou.module.supplychain.stock.controller.admin.vo;

/**
 * Request VO for approving a stock count session.
 *
 * <p>Source: TASK-G2-02I-2 §6.5.
 */
public class StockCountApproveReqVO {

    private Long sessionId;
    private Long tenantId;
    private Long approverUserId;

    // --- Getters and Setters ---

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getApproverUserId() { return approverUserId; }
    public void setApproverUserId(Long approverUserId) { this.approverUserId = approverUserId; }
}
