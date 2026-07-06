package com.geihou.module.supplychain.stock.mq.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Event published when a stock batch is approaching or has reached its expiry date.
 *
 * <p>Topic: stock.expire.warning
 * <p>Producer: <b>RESERVED</b> — not yet business-triggered as of G2-02O.
 *             No verified batch/expiry scan entry exists in the current codebase.
 * <p>Consumers (not in this slice): notification module, waste management module
 *
 * <p>Payload fields follow the common event contract:
 * tenantId, sourceModule, sourceRecordId, referenceNo, plus batch-expiry-specific fields.
 *
 * <p><b>Reserved status:</b> This event class and the corresponding publisher method
 * ({@code publishStockExpireWarning}) are provided for future batch-expiry scanning.
 * As of G2-02O, no business code triggers this event. Do not claim business
 * triggering until a verified expiry field and scan entry exist.
 *
 * <p>This is a transitional in-process event (Spring ApplicationEvent).
 * When real MQ infrastructure (geihou-spring-boot-starter-mq) is ready,
 * the StockEventPublisherImpl will be replaced to publish to the real MQ transport.
 * The event class and publisher interface remain unchanged.
 */
public class StockExpireWarningEvent {

    private Long tenantId;
    private String sourceModule;
    private Long sourceRecordId;
    private String referenceNo;

    // Stock-specific fields
    private Long stockItemId;
    private Long locationId;
    private Long productId;

    // Batch / expiry fields
    /** Batch number for traceability (nullable if batch tracking is not yet enabled). */
    private String batchNo;
    /** Expiry date of the batch. */
    private LocalDate expireDate;
    /** Days until expiry (negative if already expired). */
    private Integer daysUntilExpire;
    /** Quantity at risk of expiry (BigDecimal, never double/float). */
    private BigDecimal quantity;

    private LocalDateTime eventTime;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getSourceModule() { return sourceModule; }
    public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }

    public Long getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(Long sourceRecordId) { this.sourceRecordId = sourceRecordId; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public Long getStockItemId() { return stockItemId; }
    public void setStockItemId(Long stockItemId) { this.stockItemId = stockItemId; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public LocalDate getExpireDate() { return expireDate; }
    public void setExpireDate(LocalDate expireDate) { this.expireDate = expireDate; }

    public Integer getDaysUntilExpire() { return daysUntilExpire; }
    public void setDaysUntilExpire(Integer daysUntilExpire) { this.daysUntilExpire = daysUntilExpire; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
}
