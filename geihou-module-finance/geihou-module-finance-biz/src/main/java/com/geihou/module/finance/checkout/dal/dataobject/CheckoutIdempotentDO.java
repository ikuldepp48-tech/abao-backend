package com.geihou.module.finance.checkout.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Data object for checkout_idempotent table.
 *
 * <p>INSERT-only pattern: no update/delete path. No deleted field.
 * No @TableLogic since this table does not support soft delete.
 * DB unique key (tenant_id, idempotent_key) provides idempotent guarantee.
 * expire_time allows periodic cleanup of old entries.
 */
@TableName("checkout_idempotent")
public class CheckoutIdempotentDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String idempotentKey;

    private Long sessionId;

    private String sessionToken;

    private String status;

    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String idempotentKey) { this.idempotentKey = idempotentKey; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
