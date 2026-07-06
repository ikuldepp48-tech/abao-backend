package com.geihou.module.finance.api.order.dto;

import java.time.LocalDateTime;

/**
 * Order event DTO for OrderApi.publishOrderEvent.
 *
 * <p>Formally connected to OrderApi in G1-01F.
 *
 * <p>CG-OA4 (G1-01F-contract): eventTime (LocalDateTime) is added, sourced from
 * PRD DDL order_event_log.event_time NOT NULL. clientIp is NOT added (cross-service
 * DTO must not carry client IP for security reasons).
 */
public class OrderEventDTO {

    private Long tenantId;
    private Long orderId;
    private String eventType;
    private String beforeStatus;
    private String afterStatus;
    private Long operatorUserId;
    private String operatorRole;
    private String payload;
    private LocalDateTime eventTime;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getBeforeStatus() { return beforeStatus; }
    public void setBeforeStatus(String beforeStatus) { this.beforeStatus = beforeStatus; }

    public String getAfterStatus() { return afterStatus; }
    public void setAfterStatus(String afterStatus) { this.afterStatus = afterStatus; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    public String getOperatorRole() { return operatorRole; }
    public void setOperatorRole(String operatorRole) { this.operatorRole = operatorRole; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
}
