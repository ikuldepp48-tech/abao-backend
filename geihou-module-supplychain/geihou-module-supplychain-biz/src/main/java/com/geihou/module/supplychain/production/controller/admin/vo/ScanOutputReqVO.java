package com.geihou.module.supplychain.production.controller.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request VO for scan-output (产出登记).
 *
 * <p>Source: TASK-G2-02N §6.7.
 */
public class ScanOutputReqVO {

    private Long tenantId;

    /** 工单 ID (由 controller 从 path variable 设置) */
    private Long orderId;

    /** 实际产出数量 (> 0) */
    private BigDecimal actualOutputQty;

    /** 产出序号 (第 N 次产出登记, 幂等键) */
    private Integer outputSeq;

    /** 产出产品 ID (可选, 默认取工单 productId; 传入时必须与工单 productId 一致) */
    private Long outputProductId;

    /** 批次号 (可选, 食安追溯) */
    private String batchNo;

    /** 生产时间 (可选, 默认当前时间) */
    private LocalDateTime producedTime;

    /** 操作人 ID */
    private Long operatorUserId;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public BigDecimal getActualOutputQty() { return actualOutputQty; }
    public void setActualOutputQty(BigDecimal actualOutputQty) { this.actualOutputQty = actualOutputQty; }

    public Integer getOutputSeq() { return outputSeq; }
    public void setOutputSeq(Integer outputSeq) { this.outputSeq = outputSeq; }

    public Long getOutputProductId() { return outputProductId; }
    public void setOutputProductId(Long outputProductId) { this.outputProductId = outputProductId; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public LocalDateTime getProducedTime() { return producedTime; }
    public void setProducedTime(LocalDateTime producedTime) { this.producedTime = producedTime; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
}
