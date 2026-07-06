package com.geihou.module.finance.order.controller.app.staff.vo;

/**
 * Staff pending order list request VO.
 *
 * <p>Query parameters for staff to view orders pending acceptance (PAID status).
 */
public class StaffOrderPendingListReqVO {

    private Integer pageNo = 1;
    private Integer pageSize = 10;
    private Long shopId;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }
}
