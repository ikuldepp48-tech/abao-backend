package com.geihou.module.finance.product.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for product_combo table.
 *
 * <p>套餐定义。combo_sku_id 关联 product_sku.id(spu.type = COMBO)。
 * combo_price uses BigDecimal (never double/float).
 * status stores ENUM_COMBO_STATUS code values (ACTIVE/PAUSED/DEPRECATED).
 * 无 sort_order 字段(套餐内排序按 id 即插入顺序,G1-02F 任务包约束)。
 */
@TableName("product_combo")
public class ProductComboDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long comboSkuId;

    private String comboName;
    private BigDecimal comboPrice;

    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;

    private String status;

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

    public Long getComboSkuId() { return comboSkuId; }
    public void setComboSkuId(Long comboSkuId) { this.comboSkuId = comboSkuId; }

    public String getComboName() { return comboName; }
    public void setComboName(String comboName) { this.comboName = comboName; }

    public BigDecimal getComboPrice() { return comboPrice; }
    public void setComboPrice(BigDecimal comboPrice) { this.comboPrice = comboPrice; }

    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDateTime getEffectiveUntil() { return effectiveUntil; }
    public void setEffectiveUntil(LocalDateTime effectiveUntil) { this.effectiveUntil = effectiveUntil; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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
