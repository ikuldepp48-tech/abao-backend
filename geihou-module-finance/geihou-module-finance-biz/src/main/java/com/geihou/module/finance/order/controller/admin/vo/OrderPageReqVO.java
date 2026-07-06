package com.geihou.module.finance.order.controller.admin.vo;

import java.time.LocalDate;

/**
 * Admin order page request VO.
 *
 * <p>Supports filtering by businessDate, channel, status, shopId.
 */
public class OrderPageReqVO {

    private Integer pageNo = 1;
    private Integer pageSize = 10;
    private LocalDate businessDate;
    private String channel;
    private String status;
    private Long shopId;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }
}
