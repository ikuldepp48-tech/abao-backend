package com.geihou.module.supplychain.stock.controller.admin.vo;

/**
 * Request VO for cancelling a stock loss/scrap record.
 *
 * <p>Only PENDING_APPROVE status records can be cancelled.
 * Source: TASK-G2-02I-3 Section 5.5.
 */
public class StockLossCancelReqVO {

    private Long lossId;
    private Long tenantId;
    private Long operatorUserId;

    // --- Getters and Setters ---

    public Long getLossId() { return lossId; }
    public void setLossId(Long lossId) { this.lossId = lossId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
}
