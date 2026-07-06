package com.geihou.module.supplychain.production.controller.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request VO for creating a production order.
 *
 * <p>Source: TASK-G2-02K.
 */
public class ProductionOrderCreateReqVO {

    private Long tenantId;
    private Long productId;
    private Long recipeId;
    private Long locationId;
    private BigDecimal plannedQty;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private Long operatorUserId;
    private String remark;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    public BigDecimal getPlannedQty() { return plannedQty; }
    public void setPlannedQty(BigDecimal plannedQty) { this.plannedQty = plannedQty; }

    public LocalDateTime getPlanStartTime() { return planStartTime; }
    public void setPlanStartTime(LocalDateTime planStartTime) { this.planStartTime = planStartTime; }

    public LocalDateTime getPlanEndTime() { return planEndTime; }
    public void setPlanEndTime(LocalDateTime planEndTime) { this.planEndTime = planEndTime; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
