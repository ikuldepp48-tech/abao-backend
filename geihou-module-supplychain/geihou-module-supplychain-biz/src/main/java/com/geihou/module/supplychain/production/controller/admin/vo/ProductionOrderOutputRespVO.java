package com.geihou.module.supplychain.production.controller.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response VO for production order output (产出登记).
 *
 * <p>Source: TASK-G2-02N.
 */
public class ProductionOrderOutputRespVO {

    private Long id;
    private Long tenantId;
    private Long productionOrderId;
    private Long outputSkuId;
    private Integer outputSeq;
    private BigDecimal actualOutputQty;
    private Long stockEventId;
    private String batchNo;
    private LocalDateTime producedTime;
    private LocalDateTime expireTime;
    private String creator;
    private LocalDateTime createTime;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getProductionOrderId() { return productionOrderId; }
    public void setProductionOrderId(Long productionOrderId) { this.productionOrderId = productionOrderId; }

    public Long getOutputSkuId() { return outputSkuId; }
    public void setOutputSkuId(Long outputSkuId) { this.outputSkuId = outputSkuId; }

    public Integer getOutputSeq() { return outputSeq; }
    public void setOutputSeq(Integer outputSeq) { this.outputSeq = outputSeq; }

    public BigDecimal getActualOutputQty() { return actualOutputQty; }
    public void setActualOutputQty(BigDecimal actualOutputQty) { this.actualOutputQty = actualOutputQty; }

    public Long getStockEventId() { return stockEventId; }
    public void setStockEventId(Long stockEventId) { this.stockEventId = stockEventId; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public LocalDateTime getProducedTime() { return producedTime; }
    public void setProducedTime(LocalDateTime producedTime) { this.producedTime = producedTime; }

    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
