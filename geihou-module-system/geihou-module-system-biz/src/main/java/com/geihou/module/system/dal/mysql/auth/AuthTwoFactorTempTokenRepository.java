package com.geihou.module.system.dal.mysql.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.geihou.module.system.dal.dataobject.auth.AuthTwoFactorTempTokenDO;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class AuthTwoFactorTempTokenRepository {

    private final AuthTwoFactorTempTokenMapper mapper;

    public AuthTwoFactorTempTokenRepository(AuthTwoFactorTempTokenMapper mapper) {
        this.mapper = mapper;
    }

    public int insert(AuthTwoFactorTempTokenDO entity) {
        return mapper.insert(entity);
    }

    public AuthTwoFactorTempTokenDO selectUsableByTokenKey(String tempTokenKey, LocalDateTime now) {
        return mapper.selectOne(new LambdaQueryWrapper<AuthTwoFactorTempTokenDO>()
                .eq(AuthTwoFactorTempTokenDO::getTempTokenKey, tempTokenKey)
                .isNull(AuthTwoFactorTempTokenDO::getConsumedTime)
                .gt(AuthTwoFactorTempTokenDO::getExpireTime, now));
    }

    public AuthTwoFactorTempTokenDO selectByTokenKey(String tempTokenKey) {
        return mapper.selectOne(new LambdaQueryWrapper<AuthTwoFactorTempTokenDO>()
                .eq(AuthTwoFactorTempTokenDO::getTempTokenKey, tempTokenKey));
    }

    public boolean consume(String tempTokenKey, LocalDateTime now) {
        return mapper.update(null, new LambdaUpdateWrapper<AuthTwoFactorTempTokenDO>()
                .set(AuthTwoFactorTempTokenDO::getConsumedTime, now)
                .eq(AuthTwoFactorTempTokenDO::getTempTokenKey, tempTokenKey)
                .isNull(AuthTwoFactorTempTokenDO::getConsumedTime)
                .gt(AuthTwoFactorTempTokenDO::getExpireTime, now)) == 1;
    }

    public boolean recordFailure(String tempTokenKey, int maxAttempts, LocalDateTime now) {
        return mapper.update(null, new LambdaUpdateWrapper<AuthTwoFactorTempTokenDO>()
                .setSql("two_factor_fail_count = two_factor_fail_count + 1")
                .setSql("consumed_time = CASE WHEN two_factor_fail_count + 1 >= " + maxAttempts
                        + " THEN {0} ELSE consumed_time END", now)
                .eq(AuthTwoFactorTempTokenDO::getTempTokenKey, tempTokenKey)
                .isNull(AuthTwoFactorTempTokenDO::getConsumedTime)
                .gt(AuthTwoFactorTempTokenDO::getExpireTime, now)) == 1;
    }
}
