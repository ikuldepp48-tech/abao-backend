package com.geihou.module.supplychain.api.bom.dto;

/**
 * Request DTO for updating a product master.
 *
 * <p>Only mutable fields are included. productCode and productType are immutable after creation.
 *
 * <p>Source: TASK-G2-02A.
 */
public class ProductMasterUpdateReqDTO {

    private Long tenantId;
    private Long id;
    private String productName;
    private String skuCode;
    private String unit;
    private String category;
    private String description;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
