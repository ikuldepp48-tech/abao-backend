package com.geihou.module.supplychain.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for production_order table.
 *
 * <p>生产工单主表 — 半成品制作工单（中央厨房）。
 * 状态机服从 ENUM_PRODUCTION_STAGE: CREATED → MATERIAL_REQUEST → IN_PROGRESS → QUALITY_CHECK → COMPLETED / REWORK; 非终态可 CANCELLED。
 * 本切片实现 CREATED → MATERIAL_REQUEST → IN_PROGRESS → COMPLETED + 非终态→CANCELLED。
 *
 * <p>Source: TASK-G2-02K, PRD-组2-02 §2.1, 02-全局枚举表-V2.md ENUM_PRODUCTION_STAGE。
 */
@TableName("production_order")
public class ProductionOrderDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 工单号(租户内唯一) */
    private String orderNo;

    /** 产品ID(product_master, product_type=SEMI_FINISHED) */
    private Long productId;

    /** 配方ID(bom_recipe, status=ACTIVE) */
    private Long recipeId;

    /** 库位ID(stock_location, is_active=true) */
    private Long locationId;

    /** 计划生产数量(正数) */
    private BigDecimal plannedQty;

    /** 实际生产数量(COMPLETED 时填写) */
    private BigDecimal actualQty;

    /**
     * 生产工单状态 (ENUM_PRODUCTION_STAGE)
     * CREATED / MATERIAL_REQUEST / IN_PROGRESS / QUALITY_CHECK / COMPLETED / REWORK / CANCELLED
     */
    private String productionStage;

    /** 计划开始时间 */
    private LocalDateTime planStartTime;

    /** 计划结束时间 */
    private LocalDateTime planEndTime;

    /** 实际开始时间(IN_PROGRESS 时填写) */
    private LocalDateTime actualStartTime;

    /** 实际结束时间(COMPLETED 时填写) */
    private LocalDateTime actualEndTime;

    /** 操作人ID(中央厨房员工) */
    private Long operatorUserId;

    /** 备注 */
    private String remark;

    // --- G2-02M: Quality check / rework fields ---

    /** 返工次数(累计) */
    private Integer reworkCount;

    /** 质检结果: PASS / FAIL */
    private String qualityCheckResult;

    /** 质检备注/返工原因 */
    private String qualityCheckRemark;

    /** 质检人ID */
    private Long qualityCheckedBy;

    /** 质检时间 */
    private LocalDateTime qualityCheckedTime;

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

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
