package com.geihou.module.finance.order.controller.admin.vo;

import java.math.BigDecimal;

/**
 * Channel-level summary row for daily business summary.
 *
 * <p>One row per order channel (ENUM_ORDER_CHANNEL).
 * netRevenue = paidAmount - refundAmount - platformFee (row-level).
 */
public class OrderDailySummaryChannelVO {

    private String channel;
    private Integer orderCount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal refundAmount;
    private BigDecimal netRevenue;

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public BigDecimal getNetRevenue() { return netRevenue; }
    public void setNetRevenue(BigDecimal netRevenue) { this.netRevenue = netRevenue; }
}
