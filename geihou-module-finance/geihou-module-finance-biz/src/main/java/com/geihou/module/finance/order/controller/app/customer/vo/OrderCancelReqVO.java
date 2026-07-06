package com.geihou.module.finance.order.controller.app.customer.vo;

/**
 * Customer order cancel request VO.
 */
public class OrderCancelReqVO {

    private String cancelReason;

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
}
