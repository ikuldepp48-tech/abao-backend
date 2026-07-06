package com.geihou.module.supplychain.transfer.controller.admin.vo;

/**
 * Cancel request VO for transfer order.
 *
 * <p>Source: TASK-G2-02S.
 */
public class TransferOrderCancelReqVO {
    private Long id;
    private Long tenantId;
    private Long cancelledBy;
    private String cancelReason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(Long cancelledBy) { this.cancelledBy = cancelledBy; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
}
