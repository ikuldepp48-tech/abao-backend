package com.geihou.module.supplychain.production.controller.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response VO for production order consumption (领料记录).
 *
 * <p>Source: TASK-G2-02N.
 */
public class ProductionOrderConsumptionRespVO {

    private Long id;
    private Long tenantId;
    private Long productionOrderId;
    private Long inputSkuId;
    private Integer pickSeq;
    private BigDecimal plannedQty;
    private BigDecimal actualQty;
    private BigDecimal diffQty;
    private String diffReason;
    private Long stockEventId;
    private String creator;
    private LocalDateTime createTime;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getProductionOrderId() { return productionOrderId; }
    public void setProductionOrderId(Long productionOrderId) { this.productionOrderId = productionOrderId; }

    public Long getInputSkuId() { return inputSkuId; }
    public void setInputSkuId(Long inputSkuId) { this.inputSkuId = inputSkuId; }

    public Integer getPickSeq() { return pickSeq; }
    public void setPickSeq(Integer pickSeq) { this.pickSeq = pickSeq; }

    public BigDecimal getPlannedQty() { return plannedQty; }
    public void setPlannedQty(BigDecimal plannedQty) { this.plannedQty = plannedQty; }

    public BigDecimal getActualQty() { return actualQty; }
    public void setActualQty(BigDecimal actualQty) { this.actualQty = actualQty; }

    public BigDecimal getDiffQty() { return diffQty; }
    public void setDiffQty(BigDecimal diffQty) { this.diffQty = diffQty; }

    public String getDiffReason() { return diffReason; }
    public void setDiffReason(String diffReason) { this.diffReason = diffReason; }

    public Long getStockEventId() { return stockEventId; }
    public void setStockEventId(Long stockEventId) { this.stockEventId = stockEventId; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
