package com.geihou.module.finance.order.controller.app.staff.vo;

/**
 * Table session open request VO (staff operation).
 *
 * <p>Per G1-01D slice: staff opens a table session for dine-in.
 */
public class TableSessionOpenReqVO {

    /** Shop ID (required) */
    private Long shopId;

    /** Table ID (required) */
    private Long tableId;

    /** Table number (required) */
    private String tableNo;

    /** Customer count (optional) */
    private Integer customerCount;

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public String getTableNo() { return tableNo; }
    public void setTableNo(String tableNo) { this.tableNo = tableNo; }

    public Integer getCustomerCount() { return customerCount; }
    public void setCustomerCount(Integer customerCount) { this.customerCount = customerCount; }
}
