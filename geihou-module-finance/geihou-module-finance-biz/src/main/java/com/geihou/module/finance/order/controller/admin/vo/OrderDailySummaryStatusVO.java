package com.geihou.module.finance.order.controller.admin.vo;

import java.math.BigDecimal;

/**
 * Status-level summary row for daily business summary.
 *
 * <p>One row per order status (ENUM_ORDER_STATUS).
 * Status summary does not include netRevenue (per CG-DS1 contract ruling).
 */
public class OrderDailySummaryStatusVO {

    private String status;
    private Integer orderCount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal refundAmount;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
}
