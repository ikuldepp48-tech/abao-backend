package com.geihou.module.finance.product.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * Data object for product_combo_item table.
 *
 * <p>套餐内组成。item_sku_id 关联同租户 ACTIVE/SOLD_OUT 状态的 product_sku.id。
 * 无 sort_order 字段(套餐内排序按 id 即插入顺序,G1-02F 任务包约束)。
 * 同套餐内 (combo_id, item_sku_id, deleted) 唯一(AC-11 增强约束)。
 */
@TableName("product_combo_item")
public class ProductComboItemDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long comboId;

    private Long itemSkuId;
    private Integer quantity;
    private Boolean isOptional;
    private Integer alternativeGroup;

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

    public Long getComboId() { return comboId; }
    public void setComboId(Long comboId) { this.comboId = comboId; }

    public Long getItemSkuId() { return itemSkuId; }
    public void setItemSkuId(Long itemSkuId) { this.itemSkuId = itemSkuId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Boolean getIsOptional() { return isOptional; }
    public void setIsOptional(Boolean isOptional) { this.isOptional = isOptional; }

    public Integer getAlternativeGroup() { return alternativeGroup; }
    public void setAlternativeGroup(Integer alternativeGroup) { this.alternativeGroup = alternativeGroup; }

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
