package com.geihou.module.supplychain.production.controller.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response VO for production order.
 *
 * <p>Source: TASK-G2-02K.
 */
public class ProductionOrderRespVO {

    private Long id;
    private Long tenantId;
    private String orderNo;
    private Long productId;
    private Long recipeId;
    private Long locationId;
    private BigDecimal plannedQty;
    private BigDecimal actualQty;
    private String productionStage;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Long operatorUserId;
    private String remark;
    private Integer reworkCount;
    private String qualityCheckResult;
    private String qualityCheckRemark;
    private Long qualityCheckedBy;
    private LocalDateTime qualityCheckedTime;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    public BigDecimal getPlannedQty() { return plannedQty; }
    public void setPlannedQty(BigDecimal plannedQty) { this.plannedQty = plannedQty; }

    public BigDecimal getActualQty() { return actualQty; }
    public void setActualQty(BigDecimal actualQty) { this.actualQty = actualQty; }

    public String getProductionStage() { return productionStage; }
    public void setProductionStage(String productionStage) { this.productionStage = productionStage; }

    public LocalDateTime getPlanStartTime() { return planStartTime; }
    public void setPlanStartTime(LocalDateTime planStartTime) { this.planStartTime = planStartTime; }

    public LocalDateTime getPlanEndTime() { return planEndTime; }
    public void setPlanEndTime(LocalDateTime planEndTime) { this.planEndTime = planEndTime; }

    public LocalDateTime getActualStartTime() { return actualStartTime; }
    public void setActualStartTime(LocalDateTime actualStartTime) { this.actualStartTime = actualStartTime; }

    public LocalDateTime getActualEndTime() { return actualEndTime; }
    public void setActualEndTime(LocalDateTime actualEndTime) { this.actualEndTime = actualEndTime; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getReworkCount() { return reworkCount; }
    public void setReworkCount(Integer reworkCount) { this.reworkCount = reworkCount; }

    public String getQualityCheckResult() { return qualityCheckResult; }
    public void setQualityCheckResult(String qualityCheckResult) { this.qualityCheckResult = qualityCheckResult; }

    public String getQualityCheckRemark() { return qualityCheckRemark; }
    public void setQualityCheckRemark(String qualityCheckRemark) { this.qualityCheckRemark = qualityCheckRemark; }

    public Long getQualityCheckedBy() { return qualityCheckedBy; }
    public void setQualityCheckedBy(Long qualityCheckedBy) { this.qualityCheckedBy = qualityCheckedBy; }

    public LocalDateTime getQualityCheckedTime() { return qualityCheckedTime; }
    public void setQualityCheckedTime(LocalDateTime qualityCheckedTime) { this.qualityCheckedTime = qualityCheckedTime; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getUpdater() { return updater; }
    public void setUpdater(String updater) { this.updater = updater; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
