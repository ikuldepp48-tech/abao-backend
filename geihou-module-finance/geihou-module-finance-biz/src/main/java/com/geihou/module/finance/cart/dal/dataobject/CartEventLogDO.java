package com.geihou.module.finance.cart.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for cart_event_log table.
 *
 * <p>INSERT-only: no update/delete path. No deleted field.
 * No @TableLogic since this table does not support soft delete.
 * Every cart operation must write to this table for audit (root-cause 3 defense).
 * create_time is the only timestamp — immutable after insert.
 */
@TableName("cart_event_log")
public class CartEventLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long cartId;

    // Event type — ENUM_CART_EVENT_TYPE
    private String eventType;

    private LocalDateTime eventTime;

    // Operator
    private Long operatorUserId;
    private String operatorRole;

    // Event details
    private Long skuId;
    private Integer quantityBefore;
    private Integer quantityAfter;
    private BigDecimal amountBefore;
    private BigDecimal amountAfter;

    // Extra (JSON)
    private String extra;

    // Request info
    private String clientIp;
    private String userAgent;
    private String deviceId;

    // Only create_time — immutable
    private LocalDateTime createTime;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getCartId() { return cartId; }
    public void setCartId(Long cartId) { this.cartId = cartId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    public String getOperatorRole() { return operatorRole; }
    public void setOperatorRole(String operatorRole) { this.operatorRole = operatorRole; }

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public Integer getQuantityBefore() { return quantityBefore; }
    public void setQuantityBefore(Integer quantityBefore) { this.quantityBefore = quantityBefore; }

    public Integer getQuantityAfter() { return quantityAfter; }
    public void setQuantityAfter(Integer quantityAfter) { this.quantityAfter = quantityAfter; }

    public BigDecimal getAmountBefore() { return amountBefore; }
    public void setAmountBefore(BigDecimal amountBefore) { this.amountBefore = amountBefore; }

    public BigDecimal getAmountAfter() { return amountAfter; }
    public void setAmountAfter(BigDecimal amountAfter) { this.amountAfter = amountAfter; }

    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
