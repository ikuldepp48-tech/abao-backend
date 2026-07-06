package com.geihou.module.supplychain.stock.controller.admin.vo;

import java.math.BigDecimal;

/**
 * Request VO for creating a stock loss/scrap record.
 *
 * <p>Source: TASK-G2-02I-3 Section 5.1.
 */
public class StockLossCreateReqVO {

    private Long tenantId;
    /** LOSS(损耗) / SCRAP(报废) */
    private String lossType;
    private Long stockItemId;
    private String skuCode;
    private Long locationId;
    private BigDecimal quantity;
    private String unit;
    /** ENUM_LOSS_REASON */
    private String lossReason;
    private String remark;
    private Long operatorUserId;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getLossType() { return lossType; }
    public void setLossType(String lossType) { this.lossType = lossType; }

    public Long getStockItemId() { return stockItemId; }
    public void setStockItemId(Long stockItemId) { this.stockItemId = stockItemId; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getLossReason() { return lossReason; }
    public void setLossReason(String lossReason) { this.lossReason = lossReason; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
}
