package com.geihou.module.system.dal.dataobject.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("auth_two_factor_temp_token")
public class AuthTwoFactorTempTokenDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("temp_token_key")
    private String tempTokenKey;

    @TableField("token_hash")
    private String tokenHash;

    @TableField("user_id")
    private Long userId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("user_role")
    private String userRole;

    @TableField("expire_time")
    private LocalDateTime expireTime;

    @TableField("consumed_time")
    private LocalDateTime consumedTime;

    @TableField("two_factor_fail_count")
    private Integer twoFactorFailCount;

    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTempTokenKey() {
        return tempTokenKey;
    }

    public void setTempTokenKey(String tempTokenKey) {
        this.tempTokenKey = tempTokenKey;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public LocalDateTime getConsumedTime() {
        return consumedTime;
    }

    public void setConsumedTime(LocalDateTime consumedTime) {
        this.consumedTime = consumedTime;
    }

    public Integer getTwoFactorFailCount() {
        return twoFactorFailCount;
    }

    public void setTwoFactorFailCount(Integer twoFactorFailCount) {
        this.twoFactorFailCount = twoFactorFailCount;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
