package com.geihou.module.system.dal.mysql.auth;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.geihou.module.system.dal.dataobject.auth.AuthCaptchaChallengeDO;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class AuthCaptchaChallengeRepository {

    private final AuthCaptchaChallengeMapper mapper;

    public AuthCaptchaChallengeRepository(AuthCaptchaChallengeMapper mapper) {
        this.mapper = mapper;
    }

    public int insert(AuthCaptchaChallengeDO entity) {
        return mapper.insert(entity);
    }

    public AuthCaptchaChallengeDO selectUsableByKey(String captchaKey, LocalDateTime now) {
        return mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuthCaptchaChallengeDO>()
                .eq(AuthCaptchaChallengeDO::getCaptchaKey, captchaKey)
                .isNull(AuthCaptchaChallengeDO::getConsumedTime)
                .gt(AuthCaptchaChallengeDO::getExpireTime, now));
    }

    public boolean consume(String captchaKey, LocalDateTime now) {
        return mapper.update(null, new LambdaUpdateWrapper<AuthCaptchaChallengeDO>()
                .set(AuthCaptchaChallengeDO::getConsumedTime, now)
                .eq(AuthCaptchaChallengeDO::getCaptchaKey, captchaKey)
                .isNull(AuthCaptchaChallengeDO::getConsumedTime)
                .gt(AuthCaptchaChallengeDO::getExpireTime, now)) == 1;
    }
}
