package com.geihou.module.finance.api.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Order summary response DTO for OrderApi.summarizeByBusinessDate.
 *
 * <p>CG-OA1 (G1-01F-contract): 9 fields only — businessDate, channel, orderCount,
 * totalAmount, paidAmount, discountAmount, refundAmount, platformFee, netRevenue.
 * No channelSummary/statusSummary List. No shopId.
 *
 * <p>netRevenue = paidAmount - refundAmount - platformFee (CG-DS2 already ruled).
 */
public class OrderSummaryRespDTO {

    private LocalDate businessDate;
    private String channel;
    private Integer orderCount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal discountAmount;
    private BigDecimal refundAmount;
    private BigDecimal platformFee;
    private BigDecimal netRevenue;

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

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
}
