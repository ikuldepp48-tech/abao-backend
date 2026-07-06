package com.geihou.module.finance.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for order_payment table.
 *
 * <p>INSERT-only: no update/delete path. No deleted field.
 * No @TableLogic since this table does not support soft delete.
 * Payment records are immutable after creation (PRD Section 2.2).
 *
 * <p>All money fields use BigDecimal (never double/float).
 * payment_no is unique per tenant (uk_tenant_payment_no).
 */
@TableName("order_payment")
public class OrderPaymentDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long orderId;

    private String paymentNo;
    private String externalNo;

    private String paymentMethod;
    private BigDecimal paymentAmount;
    private String paymentStatus;

    private LocalDateTime initiatedTime;
    private LocalDateTime paidTime;
    private LocalDateTime failedTime;
    private String failedReason;

    private LocalDateTime createTime;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }

    public String getExternalNo() { return externalNo; }
    public void setExternalNo(String externalNo) { this.externalNo = externalNo; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public BigDecimal getPaymentAmount() { return paymentAmount; }
    public void setPaymentAmount(BigDecimal paymentAmount) { this.paymentAmount = paymentAmount; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public LocalDateTime getInitiatedTime() { return initiatedTime; }
    public void setInitiatedTime(LocalDateTime initiatedTime) { this.initiatedTime = initiatedTime; }

    public LocalDateTime getPaidTime() { return paidTime; }
    public void setPaidTime(LocalDateTime paidTime) { this.paidTime = paidTime; }

    public LocalDateTime getFailedTime() { return failedTime; }
    public void setFailedTime(LocalDateTime failedTime) { this.failedTime = failedTime; }

    public String getFailedReason() { return failedReason; }
    public void setFailedReason(String failedReason) { this.failedReason = failedReason; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
