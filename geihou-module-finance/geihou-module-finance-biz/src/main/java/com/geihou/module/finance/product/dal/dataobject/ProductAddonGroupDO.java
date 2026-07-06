package com.geihou.module.finance.product.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * Data object for product_addon_group table.
 *
 * <p>加料分组为租户级实体(无 spu_id 字段,SPU-加料组映射待顾客端菜单切片)。
 * Tenant isolation via tenant_id; soft delete via @TableLogic.
 */
@TableName("product_addon_group")
public class ProductAddonGroupDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String groupCode;
    private String groupName;
    private Integer selectMin;
    private Integer selectMax;
    private Boolean isRequired;
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

    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public Integer getSelectMin() { return selectMin; }
    public void setSelectMin(Integer selectMin) { this.selectMin = selectMin; }

    public Integer getSelectMax() { return selectMax; }
    public void setSelectMax(Integer selectMax) { this.selectMax = selectMax; }

    public Boolean getIsRequired() { return isRequired; }
    public void setIsRequired(Boolean isRequired) { this.isRequired = isRequired; }

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
