package com.geihou.module.supplychain.transfer.controller.admin.vo;

import java.math.BigDecimal;

/**
 * Item response VO for transfer order.
 *
 * <p>Source: TASK-G2-02S.
 */
public class TransferOrderItemRespVO {
    private Long id;
    private Long transferOrderId;
    private Long productId;
    private Long stockItemId;
    private String skuCode;
    private BigDecimal quantity;
    private String unit;
    private Long outEventId;
    private Long inEventId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTransferOrderId() { return transferOrderId; }
    public void setTransferOrderId(Long transferOrderId) { this.transferOrderId = transferOrderId; }

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

    public Long getOutEventId() { return outEventId; }
    public void setOutEventId(Long outEventId) { this.outEventId = outEventId; }

    public Long getInEventId() { return inEventId; }
    public void setInEventId(Long inEventId) { this.inEventId = inEventId; }
}
