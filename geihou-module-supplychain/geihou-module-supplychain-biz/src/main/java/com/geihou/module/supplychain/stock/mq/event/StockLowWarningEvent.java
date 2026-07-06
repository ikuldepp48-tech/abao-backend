package com.geihou.module.supplychain.stock.mq.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when stock falls below the warning threshold.
 *
 * <p>Topic: stock.low.warning
 * <p>Producer: StockEventServiceImpl (after-commit, when threshold source is available)
 * <p>Consumers (not in this slice): procurement module, notification module
 *
 * <p>Payload fields follow the common event contract:
 * tenantId, sourceModule, sourceRecordId, referenceNo, plus stock-specific fields.
 *
 * <p><b>Threshold source:</b> The warning threshold must come from an existing
 * verified source (e.g. stock_item warning_threshold column or system config).
 * Do not fabricate thresholds. If no threshold source is available in the current
 * codebase, this event is only reserved — not triggered.
 *
 * <p>This is a transitional in-process event (Spring ApplicationEvent).
 * When real MQ infrastructure (geihou-spring-boot-starter-mq) is ready,
 * the StockEventPublisherImpl will be replaced to publish to the real MQ transport.
 * The event class and publisher interface remain unchanged.
 */
public class StockLowWarningEvent {

    private Long tenantId;
    private String sourceModule;
    private Long sourceRecordId;
    private String referenceNo;

    // Stock-specific fields
    private Long stockItemId;
    private Long locationId;
    private Long productId;
    /** Current quantity after the triggering event (BigDecimal, never double/float). */
    private BigDecimal quantity;
    /** Warning threshold that was crossed (BigDecimal). */
    private BigDecimal thresholdQuantity;
    /** Warning level, e.g. "LOW" or "CRITICAL". */
    private String warningLevel;

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

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getThresholdQuantity() { return thresholdQuantity; }
    public void setThresholdQuantity(BigDecimal thresholdQuantity) { this.thresholdQuantity = thresholdQuantity; }

    public String getWarningLevel() { return warningLevel; }
    public void setWarningLevel(String warningLevel) { this.warningLevel = warningLevel; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
}
