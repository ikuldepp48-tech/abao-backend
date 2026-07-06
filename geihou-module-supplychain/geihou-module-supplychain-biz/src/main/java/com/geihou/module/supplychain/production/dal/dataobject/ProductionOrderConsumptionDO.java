package com.geihou.module.supplychain.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for production_order_consumption table.
 *
 * <p>工单原料消耗记录（领料登记）。INSERT-only 语义 — service 层不提供 update / delete。
 *
 * <p>表名沿用 PRD-组2-03 §2.2 命名: production_order_consumption。
 * 新增 pick_seq 列用于幂等序号, 审计字段与其他业务表保持一致。
 *
 * <p>Source: TASK-G2-02N, PRD-组2-03 §2.2。
 */
@TableName("production_order_consumption")
public class ProductionOrderConsumptionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 工单 ID */
    private Long productionOrderId;

    /** 消耗的 SKU (component product ID from product_master) */
    private Long inputSkuId;

    /** 领料序号 (同一组件第 N 次领料, 幂等键组成部分) */
    private Integer pickSeq;

    /** 计划消耗 (BOM 展开后该组件的计划数量) */
    private BigDecimal plannedQty;

    /** 实际消耗 (本次领料数量) */
    private BigDecimal actualQty;

    /** 差异 (actual_qty - planned_qty, 本次视角) */
    private BigDecimal diffQty;

    /** 差异原因 */
    private String diffReason;

    /** 生成的 PRODUCTION_OUT 事件 ID */
    private Long stockEventId;

    /** 消耗时单位成本 (加权, G2-03/组3-03 填充) */
    private BigDecimal unitCost;

    /** 总成本 (G2-03/组3-03 填充) */
    private BigDecimal totalCost;

    // --- Audit (INSERT-only 语义: service 层不 update, 但补全审计字段) ---
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

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

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
