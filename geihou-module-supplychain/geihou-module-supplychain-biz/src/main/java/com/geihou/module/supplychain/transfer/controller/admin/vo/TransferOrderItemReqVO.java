package com.geihou.module.supplychain.transfer.controller.admin.vo;

import java.math.BigDecimal;

/**
 * Item request VO for transfer order creation.
 *
 * <p>Source: TASK-G2-02S.
 */
public class TransferOrderItemReqVO {
    private Long productId;
    private Long stockItemId;
    private String skuCode;
    private BigDecimal quantity;
    private String unit;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getStockItemId() { return stockItemId; }
    public void setStockItemId(Long stockItemId) { this.stockItemId = stockItemId; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
