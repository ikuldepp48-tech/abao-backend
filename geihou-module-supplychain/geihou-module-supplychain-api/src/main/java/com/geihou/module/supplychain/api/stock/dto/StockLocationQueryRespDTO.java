package com.geihou.module.supplychain.api.stock.dto;

/**
 * Response DTO for stock_location mapping queries.
 *
 * <p>Returned by {@link com.geihou.module.supplychain.api.stock.StockQueryApi#getStockLocationByStoreId}.
 * Contains only the fields needed by finance for stock reserve/commit.
 *
 * <p>Source: TASK-G2-01B2 Section 10.4
 */
public class StockLocationQueryRespDTO {

    private Long id;            // stock_location.id
    private Long storeId;       // store_id
    private String locationType; // location_type
    private Long tenantId;      // tenant_id

    // --- Constructors ---

    public StockLocationQueryRespDTO() {
    }

    public StockLocationQueryRespDTO(Long id, Long storeId, String locationType, Long tenantId) {
        this.id = id;
        this.storeId = storeId;
        this.locationType = locationType;
        this.tenantId = tenantId;
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public String getLocationType() { return locationType; }
    public void setLocationType(String locationType) { this.locationType = locationType; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}
