package com.geihou.module.supplychain.stock.controller.admin.vo;

/**
 * Request VO for submitting (completing) stock count recording.
 *
 * <p>Source: TASK-G2-02I-2 §6.4.
 */
public class StockCountSubmitReqVO {

    private Long sessionId;
    private Long tenantId;
    private Long operatorUserId;

    // --- Getters and Setters ---

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
}
