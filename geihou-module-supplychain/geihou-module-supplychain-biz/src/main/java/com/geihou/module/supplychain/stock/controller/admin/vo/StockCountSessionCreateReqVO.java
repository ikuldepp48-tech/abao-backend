package com.geihou.module.supplychain.stock.controller.admin.vo;

import java.time.LocalDateTime;

/**
 * Request VO for creating a stock count session.
 *
 * <p>Source: TASK-G2-02I-2 §6.1.
 */
public class StockCountSessionCreateReqVO {

    private Long tenantId;
    private Long locationId;
    /** FULL / CYCLE / SPOT */
    private String countType;
    private Long operatorUserId;
    private LocalDateTime scheduledTime;

    // --- Getters and Setters ---

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    public String getCountType() { return countType; }
    public void setCountType(String countType) { this.countType = countType; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    public LocalDateTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalDateTime scheduledTime) { this.scheduledTime = scheduledTime; }
}
