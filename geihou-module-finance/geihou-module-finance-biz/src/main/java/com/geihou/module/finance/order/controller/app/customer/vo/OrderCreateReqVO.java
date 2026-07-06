package com.geihou.module.finance.order.controller.app.customer.vo;

import java.util.List;

/**
 * Order creation request VO (customer order creation).
 *
 * <p>Per PRD-组1-01 Section 3.2 OpenAPI OrderCreateReq.
 * shopId, channel, items are required.
 * channel uses ENUM_ORDER_CHANNEL 9 values.
 */
public class OrderCreateReqVO {

    /** Shop ID (required) */
    private Long shopId;

    /** Order channel — ENUM_ORDER_CHANNEL (required) */
    private String channel;

    /** Table session ID (required for DINE_IN) */
    private Long tableSessionId;

    /** Table number (for DINE_IN) */
    private String tableNo;

    /** Order items (required, min 1) */
    private List<OrderItemReqVO> items;

    /** Coupon ID (optional) */
    private Long couponId;

    /** Promotion IDs (optional) */
    private List<Long> promotionIds;

    /** Customer remark (optional, max 500 chars) */
    private String customerRemark;

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public Long getTableSessionId() { return tableSessionId; }
    public void setTableSessionId(Long tableSessionId) { this.tableSessionId = tableSessionId; }

    public String getTableNo() { return tableNo; }
    public void setTableNo(String tableNo) { this.tableNo = tableNo; }

    public List<OrderItemReqVO> getItems() { return items; }
    public void setItems(List<OrderItemReqVO> items) { this.items = items; }

    public Long getCouponId() { return couponId; }
    public void setCouponId(Long couponId) { this.couponId = couponId; }

    public List<Long> getPromotionIds() { return promotionIds; }
    public void setPromotionIds(List<Long> promotionIds) { this.promotionIds = promotionIds; }

    public String getCustomerRemark() { return customerRemark; }
    public void setCustomerRemark(String customerRemark) { this.customerRemark = customerRemark; }
}
