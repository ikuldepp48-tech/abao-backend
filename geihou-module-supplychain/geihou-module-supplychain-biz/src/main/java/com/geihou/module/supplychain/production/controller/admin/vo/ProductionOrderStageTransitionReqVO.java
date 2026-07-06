package com.geihou.module.supplychain.production.controller.admin.vo;

import java.math.BigDecimal;

/**
 * Request VO for production order stage transition.
 *
 * <p>Source: TASK-G2-02K.
 */
public class ProductionOrderStageTransitionReqVO {

    private Long id;
    private Long tenantId;
    private String targetStage;
    private BigDecimal actualQty;
    private Long operatorUserId;
    private String remark;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getTargetStage() { return targetStage; }
    public void setTargetStage(String targetStage) { this.targetStage = targetStage; }

    public BigDecimal getActualQty() { return actualQty; }
    public void setActualQty(BigDecimal actualQty) { this.actualQty = actualQty; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
