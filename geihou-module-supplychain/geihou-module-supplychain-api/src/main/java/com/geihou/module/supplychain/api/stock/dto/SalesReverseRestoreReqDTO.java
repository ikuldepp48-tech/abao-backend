package com.geihou.module.supplychain.api.stock.dto;

import java.util.List;

/**
 * Request DTO for sales reverse restore.
 *
 * <p>Given a source sale identified by {@code sourceModule} / {@code sourceRecordId} /
 * {@code referenceNo}, this DTO drives the reverse-restore flow that finds the
 * original raw-material {@code CONSUME_OUT} events created by
 * {@link com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseReqDTO}
 * and creates matching inbound restore events.
 *
 * <p>Key rules (TASK-G2-02E):
 * <ul>
 *   <li>No parent stock_event is created — parentEventId stays null.</li>
 *   <li>Restore events use an existing IN enum value (no enum modification).</li>
 *   <li>Per-component idempotency key: clientRequestId + "::restore::" + originalEventId.</li>
 *   <li>Double-restore prevention even if clientRequestId changes.</li>
 * </ul>
 */
public class SalesReverseRestoreReqDTO {

    /** Tenant ID (required). */
    private Long tenantId;

    /** Source module for traceability, e.g. "SALES" (required). */
    private String sourceModule;

    /** Source record ID for traceability, e.g. sales order ID (required). */
    private Long sourceRecordId;

    /** Human-readable reference number for traceability (optional). */
    private String referenceNo;

    /** Operator user ID (required). */
    private Long operatorUserId;

    /** Client request ID for whole-request idempotency (required).
     *  Per-component idempotency keys are derived as
     *  clientRequestId + "::restore::" + originalEventId. */
    private String clientRequestId;

    /**
     * Optional order item scope for partial/item refunds.
     * Null or empty means restore all matched original CONSUME_OUT events.
     */
    private List<Long> sourceOrderItemIds;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getSourceModule() { return sourceModule; }
    public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }

    public Long getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(Long sourceRecordId) { this.sourceRecordId = sourceRecordId; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }

    public List<Long> getSourceOrderItemIds() { return sourceOrderItemIds; }
    public void setSourceOrderItemIds(List<Long> sourceOrderItemIds) { this.sourceOrderItemIds = sourceOrderItemIds; }
}
