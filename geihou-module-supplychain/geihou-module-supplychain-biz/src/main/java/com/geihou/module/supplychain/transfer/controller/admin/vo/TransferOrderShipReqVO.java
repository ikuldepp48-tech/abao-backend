package com.geihou.module.supplychain.transfer.controller.admin.vo;

/**
 * Ship request VO for transfer order.
 *
 * <p>Source: TASK-G2-02S.
 */
public class TransferOrderShipReqVO {
    private Long id;
    private Long tenantId;
    private Long shippedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getShippedBy() { return shippedBy; }
    public void setShippedBy(Long shippedBy) { this.shippedBy = shippedBy; }
}
