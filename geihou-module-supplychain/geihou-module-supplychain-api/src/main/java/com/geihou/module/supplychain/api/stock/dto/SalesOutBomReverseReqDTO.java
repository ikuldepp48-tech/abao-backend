package com.geihou.module.supplychain.api.stock.dto;

import java.math.BigDecimal;

/**
 * Request DTO for sales-out BOM reverse consumption.
 *
 * <p>Given a finished product (or its SKU code) and a sold quantity, this DTO
 * drives the BOM explosion and raw-material deduction flow.
 *
 * <p>Key rules (TASK-G2-02D):
 * <ul>
 *   <li>No parent stock_event is created — only CONSUME_OUT events for raw-material leaves.</li>
 *   <li>parent_event_id stays null; sales traceability uses sourceModule / sourceRecordId / referenceNo.</li>
 *   <li>sourceOrderItemId is optional for legacy/admin callers, but checkout callers should pass it.</li>
 *   <li>Either productId or skuCode must be provided (not both null).</li>
 * </ul>
 */
public class SalesOutBomReverseReqDTO {

    /** Tenant ID (required). */
    private Long tenantId;

    /** Finished-product product_master ID. Either productId or skuCode must be non-null. */
    private Long productId;

    /** Finished-product SKU code. Either productId or skuCode must be non-null. */
    private String skuCode;

    /** Sold quantity of the finished product (must be positive). */
    private BigDecimal quantity;

    /** Location ID for raw-material deduction (required). */
    private Long locationId;

    /** Source module for traceability, e.g. "SALES" (required). */
    private String sourceModule;

    /** Source record ID for traceability, e.g. sales order ID (required). */
    private Long sourceRecordId;

    /** Source order item ID for line-level sales traceability (nullable for legacy/admin callers). */
    private Long sourceOrderItemId;

    /** Human-readable reference number for traceability (optional). */
    private String referenceNo;

    /** Operator user ID (required). */
    private Long operatorUserId;

    /** Client request ID for whole-request idempotency (required).
     *  Per-component idempotency keys are derived as clientRequestId + "::" + componentProductId. */
    private String clientRequestId;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    public String getSourceModule() { return sourceModule; }
    public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }

    public Long getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(Long sourceRecordId) { this.sourceRecordId = sourceRecordId; }

    public Long getSourceOrderItemId() { return sourceOrderItemId; }
    public void setSourceOrderItemId(Long sourceOrderItemId) { this.sourceOrderItemId = sourceOrderItemId; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }
}
