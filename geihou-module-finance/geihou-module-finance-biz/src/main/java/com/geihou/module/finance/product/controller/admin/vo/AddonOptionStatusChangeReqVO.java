package com.geihou.module.finance.product.controller.admin.vo;

/**
 * Addon option status change request VO.
 *
 * <p>newStatus must be a valid ENUM_ADDON_OPTION_STATUS value (ACTIVE/SOLD_OUT/DISABLED).
 */
public class AddonOptionStatusChangeReqVO {

    private String newStatus;
    private String reason;

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
