package com.geihou.module.system.dal.mysql.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.geihou.module.system.dal.dataobject.auth.AuthRefreshTokenDO;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRefreshTokenRepository {

    private final AuthRefreshTokenMapper mapper;

    public AuthRefreshTokenRepository(AuthRefreshTokenMapper mapper) {
        this.mapper = mapper;
    }

    public int insert(AuthRefreshTokenDO entity) {
        return mapper.insert(entity);
    }

    public AuthRefreshTokenDO selectUsableByTokenKey(String tokenKey, LocalDateTime now) {
        return mapper.selectOne(new LambdaQueryWrapper<AuthRefreshTokenDO>()
                .eq(AuthRefreshTokenDO::getTokenKey, tokenKey)
                .isNull(AuthRefreshTokenDO::getConsumedTime)
                .isNull(AuthRefreshTokenDO::getRevokedTime)
                .gt(AuthRefreshTokenDO::getExpireTime, now));
    }

    public boolean revoke(String tokenKey, String reason, LocalDateTime now) {
        return mapper.update(null, new LambdaUpdateWrapper<AuthRefreshTokenDO>()
                .set(AuthRefreshTokenDO::getRevokedTime, now)
                .set(AuthRefreshTokenDO::getRevokeReason, reason)
                .eq(AuthRefreshTokenDO::getTokenKey, tokenKey)
                .isNull(AuthRefreshTokenDO::getRevokedTime)) == 1;
    }
}
