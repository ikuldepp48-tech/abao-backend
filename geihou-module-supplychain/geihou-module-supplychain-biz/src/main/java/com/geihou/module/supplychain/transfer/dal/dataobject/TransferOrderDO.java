package com.geihou.module.supplychain.transfer.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Data object for transfer_order table.
 *
 * <p>调拨单主表 — 记录源库位到目标库位的库存调拨。
 * 状态机: PENDING → SENT → RECEIVED; PENDING → CANCELLED; SENT 不可取消。
 * 库存变更通过 StockEventService.recordEvent 写 TRANSFER_OUT/TRANSFER_IN 事件。
 *
 * <p>Source: TASK-G2-02S, PRD-组2-02 §2.1, §4.2, §4.3。
 */
@TableName("transfer_order")
public class TransferOrderDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;

    /** 调拨单号(租户内唯一) */
    private String transferNo;

    /** 源库位ID(stock_location) */
    private Long fromLocationId;
    /** 目标库位ID(stock_location) */
    private Long toLocationId;

    /** 状态: PENDING / SENT / RECEIVED / CANCELLED */
    private String status;

    /** 创建人ID */
    private Long createdBy;
    /** 发货人ID */
    private Long shippedBy;
    /** 收货人ID */
    private Long receivedBy;
    /** 取消人ID */
    private Long cancelledBy;

    /** 发货时间(SENT 时填写) */
    private LocalDateTime shippedAt;
    /** 收货时间(RECEIVED 时填写) */
    private LocalDateTime receivedAt;
    /** 取消时间(CANCELLED 时填写) */
    private LocalDateTime cancelledAt;

    /** 备注 */
    private String remark;
    /** 取消原因(CANCELLED 时必填) */
    private String cancelReason;

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

    public String getTransferNo() { return transferNo; }
    public void setTransferNo(String transferNo) { this.transferNo = transferNo; }

    public Long getFromLocationId() { return fromLocationId; }
    public void setFromLocationId(Long fromLocationId) { this.fromLocationId = fromLocationId; }

    public Long getToLocationId() { return toLocationId; }
    public void setToLocationId(Long toLocationId) { this.toLocationId = toLocationId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Long getShippedBy() { return shippedBy; }
    public void setShippedBy(Long shippedBy) { this.shippedBy = shippedBy; }

    public Long getReceivedBy() { return receivedBy; }
    public void setReceivedBy(Long receivedBy) { this.receivedBy = receivedBy; }

    public Long getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(Long cancelledBy) { this.cancelledBy = cancelledBy; }

    public LocalDateTime getShippedAt() { return shippedAt; }
    public void setShippedAt(LocalDateTime shippedAt) { this.shippedAt = shippedAt; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

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
