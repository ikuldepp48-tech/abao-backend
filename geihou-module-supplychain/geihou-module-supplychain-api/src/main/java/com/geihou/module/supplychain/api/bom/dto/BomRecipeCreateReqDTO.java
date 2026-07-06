package com.geihou.module.supplychain.api.bom.dto;

/**
 * Request DTO for creating a BOM recipe (draft).
 *
 * <p>Version number is auto-assigned by the service (incremented per product).
 *
 * <p>Source: TASK-G2-02A.
 */
public class BomRecipeCreateReqDTO {

    private Long tenantId;
    private Long productId;
    private String remark;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
