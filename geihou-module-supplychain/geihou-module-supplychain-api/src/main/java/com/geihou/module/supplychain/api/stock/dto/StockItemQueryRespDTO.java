package com.geihou.module.supplychain.api.stock.dto;

/**
 * Response DTO for stock_item mapping queries.
 *
 * <p>Returned by {@link com.geihou.module.supplychain.api.stock.StockQueryApi#getStockItemBySkuCode}.
 * Contains only the fields needed by finance for stock reserve/commit.
 *
 * <p>Source: TASK-G2-01B2 Section 10.4
 */
public class StockItemQueryRespDTO {

    private Long id;           // stock_item.id
    private String skuCode;    // sku_code
    private Long tenantId;     // tenant_id
    private String unit;       // unit (for reserve request)

    // --- Constructors ---

    public StockItemQueryRespDTO() {
    }

    public StockItemQueryRespDTO(Long id, String skuCode, Long tenantId, String unit) {
        this.id = id;
        this.skuCode = skuCode;
        this.tenantId = tenantId;
        this.unit = unit;
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
