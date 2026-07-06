package com.geihou.module.supplychain.bom.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for bom_recipe table.
 *
 * <p>BOM recipe header (配方头).
 *
 * <p>Source: TASK-G2-02A. Augmented in G2-02B with output_quantity / output_unit.
 */
@TableName("bom_recipe")
public class BomRecipeDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long productId;
    private Integer versionNo;
    /** DRAFT / ACTIVE / ARCHIVED */
    private String status;
    private String remark;

    // G2-02B augmentation: output fields for proportional explosion
    /** 本配方产出量 (default 1) */
    private BigDecimal outputQuantity;
    /** 产出单位 */
    private String outputUnit;

    // 审计
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

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

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

    // --- G2-02B getters/setters ---

    public BigDecimal getOutputQuantity() { return outputQuantity; }
    public void setOutputQuantity(BigDecimal outputQuantity) { this.outputQuantity = outputQuantity; }

    public String getOutputUnit() { return outputUnit; }
    public void setOutputUnit(String outputUnit) { this.outputUnit = outputUnit; }
}
