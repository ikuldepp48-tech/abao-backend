package com.geihou.module.supplychain.api.stock.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only current cost estimate for a finished product.
 *
 * <p>This is not G3-03 activity-based costing. It is a G2-02G contract
 * completion snapshot based on active BOM raw-material quantities and existing
 * {@code stock_balance.avg_unit_cost}.
 *
 * <p>Source: TASK-G2-02G.
 */
public class ProductCurrentCostRespDTO {

    private Long tenantId;
    private Long productId;
    private BigDecimal requestedQuantity;
    private BigDecimal currentCost;
    private String costSource;
    private String calculationMode;
    private int componentCount;
    private int costedComponentCount;
    private List<ComponentCost> components = new ArrayList<>();

    public static class ComponentCost {
        private Long productId;
        private String productCode;
        private String productName;
        private BigDecimal quantity;
        private String unit;
        private BigDecimal unitCost;
        private BigDecimal totalCost;
        private boolean costAvailable;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getProductCode() { return productCode; }
        public void setProductCode(String productCode) { this.productCode = productCode; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public BigDecimal getUnitCost() { return unitCost; }
        public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
        public BigDecimal getTotalCost() { return totalCost; }
        public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
        public boolean isCostAvailable() { return costAvailable; }
        public void setCostAvailable(boolean costAvailable) { this.costAvailable = costAvailable; }
    }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public BigDecimal getRequestedQuantity() { return requestedQuantity; }
    public void setRequestedQuantity(BigDecimal requestedQuantity) { this.requestedQuantity = requestedQuantity; }
    public BigDecimal getCurrentCost() { return currentCost; }
    public void setCurrentCost(BigDecimal currentCost) { this.currentCost = currentCost; }
    public String getCostSource() { return costSource; }
    public void setCostSource(String costSource) { this.costSource = costSource; }
    public String getCalculationMode() { return calculationMode; }
    public void setCalculationMode(String calculationMode) { this.calculationMode = calculationMode; }
    public int getComponentCount() { return componentCount; }
    public void setComponentCount(int componentCount) { this.componentCount = componentCount; }
    public int getCostedComponentCount() { return costedComponentCount; }
    public void setCostedComponentCount(int costedComponentCount) { this.costedComponentCount = costedComponentCount; }
    public List<ComponentCost> getComponents() { return components; }
    public void setComponents(List<ComponentCost> components) { this.components = components; }
}
