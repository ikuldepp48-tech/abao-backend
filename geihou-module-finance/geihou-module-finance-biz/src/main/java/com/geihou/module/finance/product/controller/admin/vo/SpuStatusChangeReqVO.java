package com.geihou.module.finance.product.controller.admin.vo;

/**
 * SPU status change request VO.
 *
 * <p>Field names match PRD-G1-02 Section 3.2 OpenAPI: newStatus, reason.
 * reason must be >= 5 characters.
 */
public class SpuStatusChangeReqVO {

    private String newStatus;
    private String reason;

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
