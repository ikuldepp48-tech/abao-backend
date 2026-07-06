package com.geihou.module.supplychain.stock.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for stock_count_session table.
 *
 * <p>Represents a stock count session with status lifecycle:
 * PLANNING → IN_PROGRESS → DIFF_REVIEW → ADJUSTED.
 *
 * <p>Source: PRD-组2-01 §2.2, TASK-G2-02I-2.
 */
@TableName("stock_count_session")
public class StockCountSessionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 盘点单号 (租户内唯一) */
    private String sessionCode;

    private Long locationId;

    /** FULL(全盘) / CYCLE(循环盘) / SPOT(抽盘) */
    private String countType;

    private LocalDateTime scheduledTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** PLANNING / IN_PROGRESS / DIFF_REVIEW / ADJUSTED */
    private String status;

    private Integer totalItems;
    private Integer diffItems;
    private BigDecimal totalDiffValue;

    private Long operatorUserId;
    private Long approverUserId;
    private LocalDateTime approveTime;

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

    public String getSessionCode() { return sessionCode; }
    public void setSessionCode(String sessionCode) { this.sessionCode = sessionCode; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    public String getCountType() { return countType; }
    public void setCountType(String countType) { this.countType = countType; }

    public LocalDateTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalDateTime scheduledTime) { this.scheduledTime = scheduledTime; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getTotalItems() { return totalItems; }
    public void setTotalItems(Integer totalItems) { this.totalItems = totalItems; }

    public Integer getDiffItems() { return diffItems; }
    public void setDiffItems(Integer diffItems) { this.diffItems = diffItems; }

    public BigDecimal getTotalDiffValue() { return totalDiffValue; }
    public void setTotalDiffValue(BigDecimal totalDiffValue) { this.totalDiffValue = totalDiffValue; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    public Long getApproverUserId() { return approverUserId; }
    public void setApproverUserId(Long approverUserId) { this.approverUserId = approverUserId; }

    public LocalDateTime getApproveTime() { return approveTime; }
    public void setApproveTime(LocalDateTime approveTime) { this.approveTime = approveTime; }

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
