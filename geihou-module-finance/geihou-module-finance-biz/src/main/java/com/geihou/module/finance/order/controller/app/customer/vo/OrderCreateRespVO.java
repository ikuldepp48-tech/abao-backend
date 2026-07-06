package com.geihou.module.finance.order.controller.app.customer.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order creation response VO.
 *
 * <p>Per PRD-组1-01 Section 3.2 OpenAPI OrderCreateRespVO.
 * paymentRequestData is deferred to payment slice — not included in G1-01A.
 */
public class OrderCreateRespVO {

    private Long orderId;
    private String orderNo;
    private BigDecimal totalAmount;
    private LocalDate businessDate;
    private String status;
    private String channel;
    private LocalDateTime createTime;

    // Order items in the response
    private List<OrderItemRespVO> items;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public List<OrderItemRespVO> getItems() { return items; }
    public void setItems(List<OrderItemRespVO> items) { this.items = items; }

    /**
     * Order item response VO (inner class for item details in order response).
     */
    public static class OrderItemRespVO {
        private Long skuId;
        private String skuName;
        private BigDecimal unitPrice;
        private BigDecimal quantity;
        private BigDecimal itemTotal;

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }

        public String getSkuName() { return skuName; }
        public void setSkuName(String skuName) { this.skuName = skuName; }

        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

        public BigDecimal getItemTotal() { return itemTotal; }
        public void setItemTotal(BigDecimal itemTotal) { this.itemTotal = itemTotal; }
    }
}
