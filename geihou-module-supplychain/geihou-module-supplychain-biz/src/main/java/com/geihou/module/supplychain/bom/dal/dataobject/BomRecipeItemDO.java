package com.geihou.module.supplychain.bom.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for bom_recipe_item table.
 *
 * <p>BOM recipe line item (配方明细).
 *
 * <p>Source: TASK-G2-02A.
 */
@TableName("bom_recipe_item")
public class BomRecipeItemDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long recipeId;
    private Long componentProductId;
    private BigDecimal quantity;
    private BigDecimal wasteRate;

    // G2-02B augmentation: component classification for explosion aggregation
    /** 子项类型 (FINISHED/SEMI_FINISHED/RAW_MATERIAL) */
    private String componentType;
    /** 子项单位 */
    private String unit;

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

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public Long getComponentProductId() { return componentProductId; }
    public void setComponentProductId(Long componentProductId) { this.componentProductId = componentProductId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getWasteRate() { return wasteRate; }
    public void setWasteRate(BigDecimal wasteRate) { this.wasteRate = wasteRate; }

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

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
