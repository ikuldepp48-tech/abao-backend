package com.geihou.module.supplychain.api.stock.dto;

/**
 * Request DTO for releasing a stock reservation.
 *
 * <p>Used by {@link com.geihou.module.supplychain.api.stock.StockEventApi#releaseStock}.
 */
public class StockReleaseReqDTO {
    private Long tenantId;
    private Long reserveId;              // stock_reserve.id
    private String idempotentKey;        // 与 reserve 时的 idempotent_key 相同
    private Long operatorUserId;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getReserveId() { return reserveId; }
    public void setReserveId(Long reserveId) { this.reserveId = reserveId; }

    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String idempotentKey) { this.idempotentKey = idempotentKey; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
}
