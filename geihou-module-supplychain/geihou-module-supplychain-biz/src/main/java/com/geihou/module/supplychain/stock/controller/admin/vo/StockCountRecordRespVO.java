package com.geihou.module.supplychain.stock.controller.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response VO for stock count record.
 *
 * <p>Source: TASK-G2-02I-2.
 */
public class StockCountRecordRespVO {

    private Long id;
    private Long tenantId;
    private Long sessionId;
    private Long stockItemId;
    private BigDecimal systemQty;
    private BigDecimal actualQty;
    private BigDecimal diffQty;
    private String diffReason;
    private String evidenceUrl;
    private Long adjustmentEventId;
    private LocalDateTime createTime;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getStockItemId() { return stockItemId; }
    public void setStockItemId(Long stockItemId) { this.stockItemId = stockItemId; }

    public BigDecimal getSystemQty() { return systemQty; }
    public void setSystemQty(BigDecimal systemQty) { this.systemQty = systemQty; }

    public BigDecimal getActualQty() { return actualQty; }
    public void setActualQty(BigDecimal actualQty) { this.actualQty = actualQty; }

    public BigDecimal getDiffQty() { return diffQty; }
    public void setDiffQty(BigDecimal diffQty) { this.diffQty = diffQty; }

    public String getDiffReason() { return diffReason; }
    public void setDiffReason(String diffReason) { this.diffReason = diffReason; }

    public String getEvidenceUrl() { return evidenceUrl; }
    public void setEvidenceUrl(String evidenceUrl) { this.evidenceUrl = evidenceUrl; }

    public Long getAdjustmentEventId() { return adjustmentEventId; }
    public void setAdjustmentEventId(Long adjustmentEventId) { this.adjustmentEventId = adjustmentEventId; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
