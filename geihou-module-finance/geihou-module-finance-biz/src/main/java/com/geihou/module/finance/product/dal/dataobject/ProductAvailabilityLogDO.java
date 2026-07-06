package com.geihou.module.finance.product.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * Data object for product_availability_log table.
 *
 * <p>INSERT-only table. No update/delete operations are allowed.
 * This DO intentionally has no 'deleted' field and no @TableLogic annotation.
 */
@TableName("product_availability_log")
public class ProductAvailabilityLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String targetType;
    private Long targetId;

    private String oldStatus;
    private String newStatus;

    private String changeReason;
    private Long changedByUserId;

    private LocalDateTime changeTime;
    private LocalDateTime createTime;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    public Long getChangedByUserId() { return changedByUserId; }
    public void setChangedByUserId(Long changedByUserId) { this.changedByUserId = changedByUserId; }

    public LocalDateTime getChangeTime() { return changeTime; }
    public void setChangeTime(LocalDateTime changeTime) { this.changeTime = changeTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
