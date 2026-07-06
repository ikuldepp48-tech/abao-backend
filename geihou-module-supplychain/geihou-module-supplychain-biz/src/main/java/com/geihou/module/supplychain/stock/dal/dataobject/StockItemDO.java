package com.geihou.module.supplychain.stock.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Data object for stock_item table.
 *
 * <p>Stock item master data (库存品项主档).
 *
 * <p>Source: TASK-G2-01B1 Section 4.3 DDL.
 */
@TableName("stock_item")
public class StockItemDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String skuCode;
    private String itemName;
    private String category;
    private String unit;
    private Integer shelfLifeDays;
    private String storageCondition;
    private Boolean isRawMaterial;
    private Boolean isSemiFinished;
    private Boolean isFinished;
    private Boolean isActive;

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

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Integer getShelfLifeDays() { return shelfLifeDays; }
    public void setShelfLifeDays(Integer shelfLifeDays) { this.shelfLifeDays = shelfLifeDays; }

    public String getStorageCondition() { return storageCondition; }
    public void setStorageCondition(String storageCondition) { this.storageCondition = storageCondition; }

    public Boolean getIsRawMaterial() { return isRawMaterial; }
    public void setIsRawMaterial(Boolean isRawMaterial) { this.isRawMaterial = isRawMaterial; }

    public Boolean getIsSemiFinished() { return isSemiFinished; }
    public void setIsSemiFinished(Boolean isSemiFinished) { this.isSemiFinished = isSemiFinished; }

    public Boolean getIsFinished() { return isFinished; }
    public void setIsFinished(Boolean isFinished) { this.isFinished = isFinished; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

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
