package com.geihou.module.supplychain.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for production_order_output table.
 *
 * <p>工单产出记录（产出登记）。INSERT-only 语义 — service 层不提供 update / delete。
 *
 * <p>表名沿用 PRD-组2-03 §2.2 命名: production_order_output。
 * 新增 output_seq 列用于幂等序号, 审计字段与其他业务表保持一致。
 *
 * <p>Source: TASK-G2-02N, PRD-组2-03 §2.2。
 */
@TableName("production_order_output")
public class ProductionOrderOutputDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 工单 ID */
    private Long productionOrderId;

    /** 产出 SKU (半成品 product ID) */
    private Long outputSkuId;

    /** 产出序号 (第 N 次产出登记, 幂等键组成部分) */
    private Integer outputSeq;

    /** 实际产出数量 */
    private BigDecimal actualOutputQty;

    /** 产出质量等级: A / B / C (G2-03 填充) */
    private String outputQualityGrade;

    /** 生成的 PRODUCTION_IN 事件 ID */
    private Long stockEventId;

    /** 产出单位成本 (G2-03/组3-03 填充) */
    private BigDecimal unitCost;

    /** 总成本 (G2-03/组3-03 填充) */
    private BigDecimal totalCost;

    /** 批次号 (食安追溯) */
    private String batchNo;

    /** 生产时间 */
    private LocalDateTime producedTime;

    /** 过期时间 (基于保质期, 可选) */
    private LocalDateTime expireTime;

    // --- Audit ---
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;

    @TableLogic
    private Boolean deleted;

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

    public String getOutputQualityGrade() { return outputQualityGrade; }
    public void setOutputQualityGrade(String outputQualityGrade) { this.outputQualityGrade = outputQualityGrade; }

    public Long getStockEventId() { return stockEventId; }
    public void setStockEventId(Long stockEventId) { this.stockEventId = stockEventId; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

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

    public String getUpdater() { return updater; }
    public void setUpdater(String updater) { this.updater = updater; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
