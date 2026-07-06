package com.geihou.module.supplychain.stock.controller.admin.vo;

import java.math.BigDecimal;

/**
 * Request VO for recording a stock count detail (INSERT complete record).
 *
 * <p>Source: TASK-G2-02I-2 §6.3.
 */
public class StockCountRecordReqVO {

    private Long sessionId;
    private Long tenantId;
    private Long stockItemId;
    private BigDecimal actualQty;
    private String diffReason;
    private String evidenceUrl;
    private Long operatorUserId;

    // --- Getters and Setters ---

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getStockItemId() { return stockItemId; }
    public void setStockItemId(Long stockItemId) { this.stockItemId = stockItemId; }

    public BigDecimal getActualQty() { return actualQty; }
    public void setActualQty(BigDecimal actualQty) { this.actualQty = actualQty; }

    public String getDiffReason() { return diffReason; }
    public void setDiffReason(String diffReason) { this.diffReason = diffReason; }

    public String getEvidenceUrl() { return evidenceUrl; }
    public void setEvidenceUrl(String evidenceUrl) { this.evidenceUrl = evidenceUrl; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
}
