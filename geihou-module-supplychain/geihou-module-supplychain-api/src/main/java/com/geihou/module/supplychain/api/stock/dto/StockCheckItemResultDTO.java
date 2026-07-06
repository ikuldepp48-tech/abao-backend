package com.geihou.module.supplychain.api.stock.dto;

import java.math.BigDecimal;

/**
 * Per-component result of a stock preflight check.
 *
 * <p>Each result corresponds to one RAW_MATERIAL leaf component aggregated
 * across all input SKUs.  The {@code status} field is one of:
 * <ul>
 *   <li>{@code SUFFICIENT} – available qty >= required qty</li>
 *   <li>{@code INSUFFICIENT} – available qty < required qty</li>
 *   <li>{@code UNMAPPED} – no active {@code stock_item} found for skuCode</li>
 * </ul>
 *
 * <p>Source: TASK-G2-02C.
 */
public class StockCheckItemResultDTO {

    /** Stock-item SKU code (from product_master.sku_code or product_code fallback). */
    private String skuCode;
    /** Product code from BOM explosion node. */
    private String productCode;
    /** Product name from BOM explosion node. */
    private String productName;
    /** Product ID from BOM explosion node (product_master.id). */
    private Long productId;
    /** Stock item ID from stock_item mapping (null if UNMAPPED). */
    private Long stockItemId;
    /** Aggregated required quantity across all input SKUs. */
    private BigDecimal requiredQty;
    /** Total available quantity summed across all locations. */
    private BigDecimal availableQty;
    /** SUFFICIENT / INSUFFICIENT / UNMAPPED. */
    private String status;

    // --- Getters and Setters ---

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getStockItemId() { return stockItemId; }
    public void setStockItemId(Long stockItemId) { this.stockItemId = stockItemId; }

    public BigDecimal getRequiredQty() { return requiredQty; }
    public void setRequiredQty(BigDecimal requiredQty) { this.requiredQty = requiredQty; }

    public BigDecimal getAvailableQty() { return availableQty; }
    public void setAvailableQty(BigDecimal availableQty) { this.availableQty = availableQty; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
