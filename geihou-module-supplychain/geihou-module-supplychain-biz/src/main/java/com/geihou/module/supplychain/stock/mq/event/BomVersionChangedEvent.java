package com.geihou.module.supplychain.stock.mq.event;

import java.time.LocalDateTime;

/**
 * Event published when a BOM recipe version is activated (status changed to ACTIVE).
 *
 * <p>Topic: bom.version.changed
 * <p>Producer: BomRecipeServiceImpl.activateRecipe (after-commit)
 * <p>Consumers (not in this slice): production module (re-calculate cost),
 *                                   data platform (PRD-G8-01)
 *
 * <p>Payload fields:
 * tenantId, recipeId, productId, recipeVersion, status.
 *
 * <p>This is a transitional in-process event (Spring ApplicationEvent).
 * When real MQ infrastructure (geihou-spring-boot-starter-mq) is ready,
 * the StockEventPublisherImpl will be replaced to publish to the real MQ transport.
 * The event class and publisher interface remain unchanged.
 */
public class BomVersionChangedEvent {

    private Long tenantId;
    private Long recipeId;
    private Long productId;
    private Integer recipeVersion;
    /** New status of the recipe, e.g. "ACTIVE". */
    private String status;

    private LocalDateTime eventTime;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getRecipeVersion() { return recipeVersion; }
    public void setRecipeVersion(Integer recipeVersion) { this.recipeVersion = recipeVersion; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
}
