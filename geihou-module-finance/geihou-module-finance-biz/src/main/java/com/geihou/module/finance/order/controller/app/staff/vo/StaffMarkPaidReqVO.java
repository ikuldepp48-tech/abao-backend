package com.geihou.module.finance.order.controller.app.staff.vo;

import java.math.BigDecimal;

/**
 * Staff mark-as-paid request VO.
 *
 * <p>Payment bridge: staff manually marks an order as paid (D-1 decision).
 */
public class StaffMarkPaidReqVO {

    private String paymentMethod;
    private BigDecimal paymentAmount;
    private String externalNo;

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public BigDecimal getPaymentAmount() { return paymentAmount; }
    public void setPaymentAmount(BigDecimal paymentAmount) { this.paymentAmount = paymentAmount; }

    public String getExternalNo() { return externalNo; }
    public void setExternalNo(String externalNo) { this.externalNo = externalNo; }
}
