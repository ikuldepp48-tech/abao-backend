package com.geihou.module.finance.product.controller.admin.vo;

/**
 * Combo status change request VO.
 *
 * <p>newStatus must be a valid ENUM_COMBO_STATUS value (ACTIVE/PAUSED/DEPRECATED).
 */
public class ComboStatusChangeReqVO {

    private String newStatus;
    private String reason;

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
