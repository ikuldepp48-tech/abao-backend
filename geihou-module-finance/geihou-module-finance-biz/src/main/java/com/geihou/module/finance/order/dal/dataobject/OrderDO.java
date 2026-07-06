package com.geihou.module.finance.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data object for orders table.
 *
 * <p>Core fields (order_no, total_amount, business_date) are immutable after creation.
 * All money fields use BigDecimal (never double/float).
 * Status field stores ENUM_ORDER_STATUS code values.
 * Soft delete via @TableLogic.
 * Optimistic locking via @Version.
 */
@TableName("orders")
public class OrderDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    // Order identity
    private String orderNo;

    // Business date (cross-day cutoff)
    private LocalDate businessDate;

    // Order basics
    private String channel;
    private String orderType;

    // Customer info
    private Long customerUserId;
    private String customerPhone;
    private String customerName;
    private Long memberId;
    private String memberLevel;

    // Shop / table
    private Long shopId;
    private Long tableSessionId;
    private String tableNo;

    // Money - BigDecimal only
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal discountAmount;
    private BigDecimal refundAmount;
    private BigDecimal platformFee;

    // Status
    private String status;

    // Payment info
    private String paymentMethod;
    private LocalDateTime payTime;

    // Kitchen / delivery times
    private LocalDateTime kitchenTime;
    private LocalDateTime readyTime;
    private LocalDateTime deliveredTime;
    private LocalDateTime completedTime;
    private LocalDateTime cancelledTime;
    private String cancelReason;

    // Marketing attribution
    private String promotionIds;
    private Long couponId;

    // Remarks
    private String customerRemark;
    private String internalRemark;

    // Optimistic lock
    @Version
    private Integer version;

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

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public Long getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(Long customerUserId) { this.customerUserId = customerUserId; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }

    public String getMemberLevel() { return memberLevel; }
    public void setMemberLevel(String memberLevel) { this.memberLevel = memberLevel; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public Long getTableSessionId() { return tableSessionId; }
    public void setTableSessionId(Long tableSessionId) { this.tableSessionId = tableSessionId; }

    public String getTableNo() { return tableNo; }
    public void setTableNo(String tableNo) { this.tableNo = tableNo; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public BigDecimal getPlatformFee() { return platformFee; }
    public void setPlatformFee(BigDecimal platformFee) { this.platformFee = platformFee; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public LocalDateTime getPayTime() { return payTime; }
    public void setPayTime(LocalDateTime payTime) { this.payTime = payTime; }

    public LocalDateTime getKitchenTime() { return kitchenTime; }
    public void setKitchenTime(LocalDateTime kitchenTime) { this.kitchenTime = kitchenTime; }

    public LocalDateTime getReadyTime() { return readyTime; }
    public void setReadyTime(LocalDateTime readyTime) { this.readyTime = readyTime; }

    public LocalDateTime getDeliveredTime() { return deliveredTime; }
    public void setDeliveredTime(LocalDateTime deliveredTime) { this.deliveredTime = deliveredTime; }

    public LocalDateTime getCompletedTime() { return completedTime; }
    public void setCompletedTime(LocalDateTime completedTime) { this.completedTime = completedTime; }

    public LocalDateTime getCancelledTime() { return cancelledTime; }
    public void setCancelledTime(LocalDateTime cancelledTime) { this.cancelledTime = cancelledTime; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public String getPromotionIds() { return promotionIds; }
    public void setPromotionIds(String promotionIds) { this.promotionIds = promotionIds; }

    public Long getCouponId() { return couponId; }
    public void setCouponId(Long couponId) { this.couponId = couponId; }

    public String getCustomerRemark() { return customerRemark; }
    public void setCustomerRemark(String customerRemark) { this.customerRemark = customerRemark; }

    public String getInternalRemark() { return internalRemark; }
    public void setInternalRemark(String internalRemark) { this.internalRemark = internalRemark; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

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
