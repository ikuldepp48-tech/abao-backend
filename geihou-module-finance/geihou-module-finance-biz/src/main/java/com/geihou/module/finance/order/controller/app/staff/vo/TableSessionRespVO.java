package com.geihou.module.finance.order.controller.app.staff.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Table session response VO (includes session detail + associated orders).
 *
 * <p>Per G1-01D slice: returns session info and order list.
 */
public class TableSessionRespVO {

    private Long id;
    private String sessionNo;
    private Long shopId;
    private Long tableId;
    private String tableNo;
    private Integer customerCount;
    private String status;
    private LocalDateTime openTime;
    private LocalDateTime settleTime;
    private LocalDateTime closeTime;
    private LocalDate businessDate;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private Integer orderCount;
    private List<OrderSummary> orders;

    public static class OrderSummary {
        private Long orderId;
        private String orderNo;
        private String status;
        private BigDecimal totalAmount;
        private BigDecimal paidAmount;
        private LocalDateTime createTime;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

        public BigDecimal getPaidAmount() { return paidAmount; }
        public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionNo() { return sessionNo; }
    public void setSessionNo(String sessionNo) { this.sessionNo = sessionNo; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public String getTableNo() { return tableNo; }
    public void setTableNo(String tableNo) { this.tableNo = tableNo; }

    public Integer getCustomerCount() { return customerCount; }
    public void setCustomerCount(Integer customerCount) { this.customerCount = customerCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalDateTime openTime) { this.openTime = openTime; }

    public LocalDateTime getSettleTime() { return settleTime; }
    public void setSettleTime(LocalDateTime settleTime) { this.settleTime = settleTime; }

    public LocalDateTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalDateTime closeTime) { this.closeTime = closeTime; }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }

    public List<OrderSummary> getOrders() { return orders; }
    public void setOrders(List<OrderSummary> orders) { this.orders = orders; }
}
