package com.geihou.module.finance.checkout.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data object for checkout_session table.
 *
 * <p>All money fields use BigDecimal (never double/float).
 * Status field stores ENUM_CHECKOUT_STATUS code values.
 * Soft delete via @TableLogic.
 *
 * <p>CG-7 Option C: order_id remains NULL after simulated pay.
 *   Checkout-to-order conversion deferred to G1-04C.
 * <p>CG-8 StockApi degraded: no stock_reservation_id.
 *   Oversell risk — not production-grade stock safety.
 * <p>CG-9 Simulated payment: payment_method/payment_time are simulated.
 *   Not real WeChat Pay, no signature verification.
 * <p>CG-11 PromotionApi missing: applied_promotions/applied_coupon_ids
 *   always null; locked_discount always 0.
 */
@TableName("checkout_session")
public class CheckoutSessionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    // Cart & customer
    private Long cartId;
    private Long customerUserId;
    private Long shopId;

    // Session token (server-generated UUID, unique)
    private String sessionToken;

    // Status — ENUM_CHECKOUT_STATUS 5 values
    private String status;

    // Money — BigDecimal only
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal lockedDiscount;
    private BigDecimal totalAmount;

    // Coupon/promotion (CG-11: always null)
    private String appliedPromotions;
    private String appliedCouponIds;

    // Payment (CG-9: simulated local payment bridge)
    private String paymentMethod;
    private LocalDateTime paymentTime;
    private String paymentTradeNo;

    // Order (CG-7 Option C: always null in G1-04B)
    private Long orderId;

    // Business date
    private LocalDate businessDate;

    // Expiry (lazy check in service method)
    private LocalDateTime expireTime;

    // Channel
    private String channel;

    // Remark
    private String remark;

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

    public Long getCartId() { return cartId; }
    public void setCartId(Long cartId) { this.cartId = cartId; }

    public Long getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(Long customerUserId) { this.customerUserId = customerUserId; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public void setSubtotalAmount(BigDecimal subtotalAmount) { this.subtotalAmount = subtotalAmount; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getLockedDiscount() { return lockedDiscount; }
    public void setLockedDiscount(BigDecimal lockedDiscount) { this.lockedDiscount = lockedDiscount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getAppliedPromotions() { return appliedPromotions; }
    public void setAppliedPromotions(String appliedPromotions) { this.appliedPromotions = appliedPromotions; }

    public String getAppliedCouponIds() { return appliedCouponIds; }
    public void setAppliedCouponIds(String appliedCouponIds) { this.appliedCouponIds = appliedCouponIds; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public LocalDateTime getPaymentTime() { return paymentTime; }
    public void setPaymentTime(LocalDateTime paymentTime) { this.paymentTime = paymentTime; }

    public String getPaymentTradeNo() { return paymentTradeNo; }
    public void setPaymentTradeNo(String paymentTradeNo) { this.paymentTradeNo = paymentTradeNo; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

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
