package com.geihou.module.supplychain.api.bom.preview.dto;

import java.math.BigDecimal;

/**
 * Response DTO for BOM cost preview.
 *
 * <p>Each row represents an aggregated raw-material leaf from the explosion tree.
 * Cost fields are null/zero placeholders — cost calculation is deferred to G2-02C.
 *
 * <p>Source: TASK-G2-02B.
 */
public class BomCostPreviewRespDTO {

    /** Raw material product ID. */
    private Long productId;
    /** Raw material product code. */
    private String productCode;
    /** Raw material product name. */
    private String productName;
    /** Aggregated total quantity across all explosion paths. */
    private BigDecimal totalQuantity;
    /** Unit of measure. */
    private String unit;
    /** Component type (always RAW_MATERIAL for aggregated rows). */
    private String componentType;

    // Cost placeholder — cost calculation deferred to G2-02C
    /** Unit cost (null placeholder, to be populated in G2-02C). */
    private BigDecimal unitCost;
    /** Total cost = totalQuantity * unitCost (null placeholder, to be populated in G2-02C). */
    private BigDecimal totalCost;

    // --- Getters and Setters ---

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
}
