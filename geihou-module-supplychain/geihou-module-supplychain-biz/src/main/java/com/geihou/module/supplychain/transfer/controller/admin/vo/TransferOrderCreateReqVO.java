package com.geihou.module.supplychain.transfer.controller.admin.vo;

import java.util.List;

/**
 * Create request VO for transfer order.
 *
 * <p>Source: TASK-G2-02S.
 */
public class TransferOrderCreateReqVO {
    private Long tenantId;
    private Long fromLocationId;
    private Long toLocationId;
    private List<TransferOrderItemReqVO> items;
    private String remark;
    private Long createdBy;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getFromLocationId() { return fromLocationId; }
    public void setFromLocationId(Long fromLocationId) { this.fromLocationId = fromLocationId; }

    public Long getToLocationId() { return toLocationId; }
    public void setToLocationId(Long toLocationId) { this.toLocationId = toLocationId; }

    public List<TransferOrderItemReqVO> getItems() { return items; }
    public void setItems(List<TransferOrderItemReqVO> items) { this.items = items; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
