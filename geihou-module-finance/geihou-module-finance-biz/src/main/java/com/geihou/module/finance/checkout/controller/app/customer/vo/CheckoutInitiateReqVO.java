package com.geihou.module.finance.checkout.controller.app.customer.vo;

import java.util.List;

/**
 * Request VO for checkout initiate.
 *
 * <p>CG-11: couponIds field is retained for PRD OpenAPI consistency,
 * but service layer rejects non-empty couponIds with COUPON_INVALID.
 */
public class CheckoutInitiateReqVO {

    private Long customerUserId;
    private Long shopId;
    private String channel;

    /**
     * Idempotency key (client-generated UUID).
     */
    private String idempotentKey;

    /**
     * Coupon IDs — CG-11: if non-empty, rejected with COUPON_INVALID.
     * PromotionApi not available (组 9-01); cannot silently ignore.
     */
    private List<String> couponIds;

    private String remark;

    // --- Getters and Setters ---

    public Long getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(Long customerUserId) { this.customerUserId = customerUserId; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String idempotentKey) { this.idempotentKey = idempotentKey; }

    public List<String> getCouponIds() { return couponIds; }
    public void setCouponIds(List<String> couponIds) { this.couponIds = couponIds; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
