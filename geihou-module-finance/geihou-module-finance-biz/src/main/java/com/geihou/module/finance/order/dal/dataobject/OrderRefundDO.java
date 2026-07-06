package com.geihou.module.finance.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for order_refund table.
 *
 * <p>Supports soft delete via @TableLogic (deleted field).
 * Refund records can be updated (status, approver, refund_time, etc.).
 * All money fields use BigDecimal (never double/float).
 * refund_no is unique per tenant (uk_tenant_refund_no).
 */
@TableName("order_refund")
public class OrderRefundDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    // Refund identity
    private String refundNo;
    private Long originalOrderId;
    private Long originalPaymentId;

    // Refund amount / scope
    private BigDecimal refundAmount;
    private String refundType;
    private String refundItemIds;

    // Refund reason (required)
    private String reasonType;
    private String reasonDetail;

    // Status
    private String status;

    // Approval (high amount requires approval)
    private Long approverUserId;
    private LocalDateTime approveTime;
    private String approveRemark;

    // Refund execution
    private LocalDateTime refundTime;
    private String externalRefundNo;

    // Failure info
    private String failReason;

    // Audit
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

    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }

    public Long getOriginalOrderId() { return originalOrderId; }
    public void setOriginalOrderId(Long originalOrderId) { this.originalOrderId = originalOrderId; }

    public Long getOriginalPaymentId() { return originalPaymentId; }
    public void setOriginalPaymentId(Long originalPaymentId) { this.originalPaymentId = originalPaymentId; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public String getRefundType() { return refundType; }
    public void setRefundType(String refundType) { this.refundType = refundType; }

    public String getRefundItemIds() { return refundItemIds; }
    public void setRefundItemIds(String refundItemIds) { this.refundItemIds = refundItemIds; }

    public String getReasonType() { return reasonType; }
    public void setReasonType(String reasonType) { this.reasonType = reasonType; }

    public String getReasonDetail() { return reasonDetail; }
    public void setReasonDetail(String reasonDetail) { this.reasonDetail = reasonDetail; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getApproverUserId() { return approverUserId; }
    public void setApproverUserId(Long approverUserId) { this.approverUserId = approverUserId; }

    public LocalDateTime getApproveTime() { return approveTime; }
    public void setApproveTime(LocalDateTime approveTime) { this.approveTime = approveTime; }

    public String getApproveRemark() { return approveRemark; }
    public void setApproveRemark(String approveRemark) { this.approveRemark = approveRemark; }

    public LocalDateTime getRefundTime() { return refundTime; }
    public void setRefundTime(LocalDateTime refundTime) { this.refundTime = refundTime; }

    public String getExternalRefundNo() { return externalRefundNo; }
    public void setExternalRefundNo(String externalRefundNo) { this.externalRefundNo = externalRefundNo; }

    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }

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
