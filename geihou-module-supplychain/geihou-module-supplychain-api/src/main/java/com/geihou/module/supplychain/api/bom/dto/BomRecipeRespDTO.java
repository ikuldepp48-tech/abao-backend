package com.geihou.module.supplychain.api.bom.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for BOM recipe queries.
 *
 * <p>Includes recipe header and its items.
 *
 * <p>Source: TASK-G2-02A.
 */
public class BomRecipeRespDTO {

    private Long id;
    private Long tenantId;
    private Long productId;
    private Integer versionNo;
    private String status;
    private String remark;

    // G2-02B fields
    /** 本配方产出量 (default 1) */
    private java.math.BigDecimal outputQuantity;
    /** 产出单位 */
    private String outputUnit;

    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
    private List<Item> items;

    // G0-04H185 SLICE-2C-2A: BOM lookup status (independent of recipe status).
    // Five values: FOUND / NO_PRODUCT / NO_ACTIVE_RECIPE / AMBIGUOUS_PRODUCT / INVALID_SKU_CODE.
    // Not persisted - set by BomApiImpl on every return branch so finance classifier
    // can distinguish empty-state causes without inferring from id/productId.
    private String lookupStatus;

    // --- Inner class for items ---

    public static class Item {
        private Long id;
        private Long componentProductId;
        private String componentProductCode;
        private String componentProductName;
        private BigDecimal quantity;
        private BigDecimal wasteRate;

        // G2-02B fields
        /** 子项类型 (FINISHED/SEMI_FINISHED/RAW_MATERIAL) */
        private String componentType;
        /** 子项单位 */
        private String unit;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Long getComponentProductId() { return componentProductId; }
        public void setComponentProductId(Long componentProductId) { this.componentProductId = componentProductId; }

        public String getComponentProductCode() { return componentProductCode; }
        public void setComponentProductCode(String componentProductCode) { this.componentProductCode = componentProductCode; }

        public String getComponentProductName() { return componentProductName; }
        public void setComponentProductName(String componentProductName) { this.componentProductName = componentProductName; }

        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

        public BigDecimal getWasteRate() { return wasteRate; }
        public void setWasteRate(BigDecimal wasteRate) { this.wasteRate = wasteRate; }

        public String getComponentType() { return componentType; }
        public void setComponentType(String componentType) { this.componentType = componentType; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public java.math.BigDecimal getOutputQuantity() { return outputQuantity; }
    public void setOutputQuantity(java.math.BigDecimal outputQuantity) { this.outputQuantity = outputQuantity; }

    public String getOutputUnit() { return outputUnit; }
    public void setOutputUnit(String outputUnit) { this.outputUnit = outputUnit; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getUpdater() { return updater; }
    public void setUpdater(String updater) { this.updater = updater; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    public String getLookupStatus() { return lookupStatus; }
    public void setLookupStatus(String lookupStatus) { this.lookupStatus = lookupStatus; }
}
