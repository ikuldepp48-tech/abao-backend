package com.geihou.module.supplychain.api.stock.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Request DTO for committing a stock reservation (real stock deduction).
 *
 * <p>Used by {@link com.geihou.module.supplychain.api.stock.StockEventApi#commitStock}.
 */
public class StockCommitReqDTO {
    private Long tenantId;
    private Long reserveId;              // stock_reserve.id
    private String idempotentKey;        // 与 reserve 时的 idempotent_key 相同
    private Long operatorUserId;

    // stock_event 写入参数
    private LocalDateTime eventTime;
    private LocalDate businessDate;
    private String referenceNo;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getReserveId() { return reserveId; }
    public void setReserveId(Long reserveId) { this.reserveId = reserveId; }

    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String idempotentKey) { this.idempotentKey = idempotentKey; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
}
