package com.geihou.module.supplychain.api.bom.dto;

/**
 * Request DTO for creating a product master.
 *
 * <p>Source: TASK-G2-02A.
 */
public class ProductMasterCreateReqDTO {

    private Long tenantId;
    private String productCode;
    private String productName;
    /** FINISHED / SEMI_FINISHED / RAW_MATERIAL */
    private String productType;
    /** Optional: link to stock_item sku_code */
    private String skuCode;
    private String unit;
    private String category;
    private String description;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
