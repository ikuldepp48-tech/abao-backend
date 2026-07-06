package com.geihou.module.supplychain.production.controller.admin.vo;

import java.math.BigDecimal;

/**
 * Request VO for scan-pick (领料记录).
 *
 * <p>Source: TASK-G2-02N §6.7.
 */
public class ScanPickReqVO {

    private Long tenantId;

    /** 工单 ID (由 controller 从 path variable 设置) */
    private Long orderId;

    /** BOM 组件产品 ID (product_master) */
    private Long componentProductId;

    /** 实际领料数量 (> 0) */
    private BigDecimal actualQty;

    /** 领料序号 (同一组件第 N 次领料, 幂等键) */
    private Integer pickSeq;

    /** 差异原因 (可选) */
    private String diffReason;

    /** 操作人 ID */
    private Long operatorUserId;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getComponentProductId() { return componentProductId; }
    public void setComponentProductId(Long componentProductId) { this.componentProductId = componentProductId; }

    public BigDecimal getActualQty() { return actualQty; }
    public void setActualQty(BigDecimal actualQty) { this.actualQty = actualQty; }

    public Integer getPickSeq() { return pickSeq; }
    public void setPickSeq(Integer pickSeq) { this.pickSeq = pickSeq; }

    public String getDiffReason() { return diffReason; }
    public void setDiffReason(String diffReason) { this.diffReason = diffReason; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
}
