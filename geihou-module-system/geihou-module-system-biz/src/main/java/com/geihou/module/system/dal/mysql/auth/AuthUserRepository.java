package com.geihou.module.system.dal.mysql.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.geihou.module.system.dal.dataobject.auth.AuthUserDO;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class AuthUserRepository {

    private final AuthUserMapper mapper;

    public AuthUserRepository(AuthUserMapper mapper) {
        this.mapper = mapper;
    }

    public int insert(AuthUserDO entity) {
        return mapper.insert(entity);
    }

    public AuthUserDO selectById(Long id) {
        return mapper.selectOne(activeQuery().eq(AuthUserDO::getId, id));
    }

    public AuthUserDO selectByTenantIdAndPhone(Long tenantId, String phone) {
        return mapper.selectOne(activeQuery()
                .eq(AuthUserDO::getTenantId, tenantId)
                .eq(AuthUserDO::getPhone, phone));
    }

    public AuthUserDO selectByTenantIdAndUsername(Long tenantId, String username) {
        return mapper.selectOne(activeQuery()
                .eq(AuthUserDO::getTenantId, tenantId)
                .eq(AuthUserDO::getUsername, username));
    }

    public AuthUserDO selectByTenantIdAndWechatOpenid(Long tenantId, String wechatOpenid) {
        return mapper.selectOne(activeQuery()
                .eq(AuthUserDO::getTenantId, tenantId)
                .eq(AuthUserDO::getWechatOpenid, wechatOpenid));
    }

    public boolean existsByTenantIdAndPhone(Long tenantId, String phone) {
        return mapper.selectCount(activeQuery()
                .eq(AuthUserDO::getTenantId, tenantId)
                .eq(AuthUserDO::getPhone, phone)) > 0;
    }

    public boolean existsByTenantIdAndUsername(Long tenantId, String username) {
        return mapper.selectCount(activeQuery()
                .eq(AuthUserDO::getTenantId, tenantId)
                .eq(AuthUserDO::getUsername, username)) > 0;
    }

    public boolean existsByTenantIdAndWechatOpenid(Long tenantId, String wechatOpenid) {
        return mapper.selectCount(activeQuery()
                .eq(AuthUserDO::getTenantId, tenantId)
                .eq(AuthUserDO::getWechatOpenid, wechatOpenid)) > 0;
    }

    public int updateLoginFailureState(Long userId,
                                       int loginFailCount,
                                       String status,
                                       String statusReason,
                                       LocalDateTime lockUntil,
                                       LocalDateTime updateTime) {
        return mapper.update(null, new LambdaUpdateWrapper<AuthUserDO>()
                .set(AuthUserDO::getLoginFailCount, loginFailCount)
                .set(AuthUserDO::getStatus, status)
                .set(AuthUserDO::getStatusReason, statusReason)
                .set(AuthUserDO::getLockUntil, lockUntil)
                .set(AuthUserDO::getUpdateTime, updateTime)
                .eq(AuthUserDO::getId, userId)
                .eq(AuthUserDO::getDeleted, false));
    }

    public int resetLoginState(Long userId, String lastLoginIp, LocalDateTime updateTime) {
        return mapper.update(null, new LambdaUpdateWrapper<AuthUserDO>()
                .set(AuthUserDO::getLoginFailCount, 0)
                .set(AuthUserDO::getStatus, "ACTIVE")
                .set(AuthUserDO::getStatusReason, null)
                .set(AuthUserDO::getLockUntil, null)
                .set(AuthUserDO::getLastLoginIp, lastLoginIp)
                .set(AuthUserDO::getLastLoginTime, updateTime)
                .set(AuthUserDO::getUpdateTime, updateTime)
                .eq(AuthUserDO::getId, userId)
                .eq(AuthUserDO::getDeleted, false));
    }

    private static LambdaQueryWrapper<AuthUserDO> activeQuery() {
        return new LambdaQueryWrapper<AuthUserDO>().eq(AuthUserDO::getDeleted, false);
    }
}
