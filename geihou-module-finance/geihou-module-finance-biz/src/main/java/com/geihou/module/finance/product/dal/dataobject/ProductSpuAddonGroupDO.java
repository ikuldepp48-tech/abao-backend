package com.geihou.module.finance.product.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * Data object for product_spu_addon_group mapping table.
 *
 * <p>Maps SPU to addon groups (many-to-many). Required by ProductApi.getAddonGroupsBySpu
 * since product_addon_group has no spu_id field (G1-02F R-1/R-6 gap).
 *
 * <p>Tenant isolation via tenant_id; soft delete via @TableLogic.
 * Unique constraint: (tenant_id, spu_id, addon_group_id, deleted).
 */
@TableName("product_spu_addon_group")
public class ProductSpuAddonGroupDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long spuId;
    private Long addonGroupId;
    private Integer sortOrder;

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

    public Long getSpuId() { return spuId; }
    public void setSpuId(Long spuId) { this.spuId = spuId; }

    public Long getAddonGroupId() { return addonGroupId; }
    public void setAddonGroupId(Long addonGroupId) { this.addonGroupId = addonGroupId; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

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
