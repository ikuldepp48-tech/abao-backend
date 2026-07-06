package com.geihou.module.supplychain.stock.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for stock_count_record table.
 *
 * <p>INSERT-only (不可篡改). No @TableLogic, no deleted column, no updater/update_time.
 * This DO and its mapper provide ONLY insert and select methods.
 * adjustment_event_id is retained but NOT backfilled in this slice (INSERT-only constraint).
 *
 * <p>Source: PRD-组2-01 §2.2, TASK-G2-02I-2.
 */
@TableName("stock_count_record")
public class StockCountRecordDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long sessionId;

    private Long stockItemId;

    /** 系统数量(盘前快照) */
    private BigDecimal systemQty;
    /** 实盘数量 */
    private BigDecimal actualQty;
    /** 差异(实盘 - 系统) */
    private BigDecimal diffQty;

    /** 差异原因(diff_qty!=0时必填,>=30字) */
    private String diffReason;
    /** 证据照片URL(差异超阈值时必传) */
    private String evidenceUrl;

    /** 生成的COUNT_ADJUST事件ID(保留字段,本切片不回填) */
    private Long adjustmentEventId;

    // INSERT-only: only create_time, no updater/update_time/creator/deleted
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
