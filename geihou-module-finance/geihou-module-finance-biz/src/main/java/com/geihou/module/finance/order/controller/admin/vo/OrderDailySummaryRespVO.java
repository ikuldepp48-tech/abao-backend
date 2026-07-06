package com.geihou.module.finance.order.controller.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Admin daily summary response VO.
 *
 * <p>Aggregates order count, amounts, and dimension breakdowns by business date.
 * Top-level: 9 fields + 2 List fields (channelSummary, statusSummary).
 * netRevenue = paidAmount - refundAmount - platformFee (per CG-DS2 ruling).
 */
public class OrderDailySummaryRespVO {

    // Top-level summary (9 fields)
    private LocalDate businessDate;
    private Long shopId;
    private Integer orderCount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal discountAmount;
    private BigDecimal refundAmount;
    private BigDecimal platformFee;
    private BigDecimal netRevenue;

    // Dimension breakdowns (2 List fields)
    private List<OrderDailySummaryChannelVO> channelSummary;
    private List<OrderDailySummaryStatusVO> statusSummary;

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }

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

    public BigDecimal getNetRevenue() { return netRevenue; }
    public void setNetRevenue(BigDecimal netRevenue) { this.netRevenue = netRevenue; }

    public List<OrderDailySummaryChannelVO> getChannelSummary() { return channelSummary; }
    public void setChannelSummary(List<OrderDailySummaryChannelVO> channelSummary) { this.channelSummary = channelSummary; }

    public List<OrderDailySummaryStatusVO> getStatusSummary() { return statusSummary; }
    public void setStatusSummary(List<OrderDailySummaryStatusVO> statusSummary) { this.statusSummary = statusSummary; }
}
