package com.geihou.module.supplychain.transfer.controller.admin.vo;

/**
 * Receive request VO for transfer order.
 *
 * <p>Source: TASK-G2-02S.
 */
public class TransferOrderReceiveReqVO {
    private Long id;
    private Long tenantId;
    private Long receivedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getReceivedBy() { return receivedBy; }
    public void setReceivedBy(Long receivedBy) { this.receivedBy = receivedBy; }
}
