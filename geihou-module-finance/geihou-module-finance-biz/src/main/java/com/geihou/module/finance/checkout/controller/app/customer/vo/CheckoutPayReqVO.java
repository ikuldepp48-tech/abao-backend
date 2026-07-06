package com.geihou.module.finance.checkout.controller.app.customer.vo;

/**
 * Request VO for simulated checkout pay.
 *
 * <p>CG-9: Simulated local payment bridge only.
 * NOT real WeChat Pay. No signature verification.
 * paymentMethod supports WECHAT_PAY / BALANCE / GIFT_CARD (all simulated).
 */
public class CheckoutPayReqVO {

    /**
     * Payment method — ENUM_PAYMENT_METHOD values (simulated).
     * Supported: WECHAT_PAY / BALANCE / GIFT_CARD
     */
    private String paymentMethod;

    /**
     * Test trigger for simulated payment failure (CG-9).
     * When set to "SIMULATE_FAIL", the simulated payment bridge returns PAYMENT_FAILED.
     * This is for testing purposes only.
     */
    private String simulateFail;

    // --- Getters and Setters ---

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getSimulateFail() { return simulateFail; }
    public void setSimulateFail(String simulateFail) { this.simulateFail = simulateFail; }
}
