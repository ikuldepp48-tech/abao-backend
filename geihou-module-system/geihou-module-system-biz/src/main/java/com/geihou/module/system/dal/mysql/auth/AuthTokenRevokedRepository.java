package com.geihou.module.system.dal.mysql.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.module.system.dal.dataobject.auth.AuthTokenRevokedDO;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class AuthTokenRevokedRepository {

    private final AuthTokenRevokedMapper mapper;

    public AuthTokenRevokedRepository(AuthTokenRevokedMapper mapper) {
        this.mapper = mapper;
    }

    public int insert(AuthTokenRevokedDO entity) {
        return mapper.insert(entity);
    }

    public AuthTokenRevokedDO selectByJti(String jti) {
        return mapper.selectOne(AuthTokenRevokedDO::getJti, jti);
    }

    public List<AuthTokenRevokedDO> selectByUserId(Long userId) {
        return mapper.selectList(AuthTokenRevokedDO::getUserId, userId);
    }

    public boolean existsByJti(String jti) {
        return mapper.selectCount(AuthTokenRevokedDO::getJti, jti) > 0;
    }

    public int deleteExpired(LocalDateTime before) {
        return mapper.delete(new LambdaQueryWrapper<AuthTokenRevokedDO>()
                .lt(AuthTokenRevokedDO::getExpireTime, before));
    }
}
