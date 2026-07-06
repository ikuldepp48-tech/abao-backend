package com.geihou.module.finance.product.mq.event;

import java.time.LocalDateTime;

/**
 * Event published when a product (SPU/SKU/COMBO) status changes.
 *
 * <p>Topic: product.status.changed
 * <p>Producers: SpuService.changeSpuStatus, SkuService.changeSkuStatus,
 *               ComboService.changeComboStatus (all after-commit)
 * <p>Consumers (not in this slice): cart (PRD-G1-04, clear offline products),
 *                                   data platform (PRD-G8-01)
 *
 * <p>targetType is "SPU", "SKU", or "COMBO".
 * oldStatus/newStatus use the corresponding enum values.
 *
 * <p>This is a transitional in-process event (Spring ApplicationEvent).
 * When real MQ infrastructure is ready, the publisher implementation
 * will be replaced without changing this event class.
 */
public class ProductStatusChangedEvent {

    private Long tenantId;
    private String targetType;
    private Long targetId;
    private String oldStatus;
    private String newStatus;
    private String changeReason;
    private Long changedByUserId;
    private LocalDateTime changeTime;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    public Long getChangedByUserId() { return changedByUserId; }
    public void setChangedByUserId(Long changedByUserId) { this.changedByUserId = changedByUserId; }

    public LocalDateTime getChangeTime() { return changeTime; }
    public void setChangeTime(LocalDateTime changeTime) { this.changeTime = changeTime; }
}
