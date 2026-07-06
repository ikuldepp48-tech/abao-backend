package com.geihou.module.supplychain.stock.controller.admin.vo;

/**
 * Request VO for starting a stock count session.
 *
 * <p>Source: TASK-G2-02I-2 §6.2.
 */
public class StockCountSessionStartReqVO {

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
