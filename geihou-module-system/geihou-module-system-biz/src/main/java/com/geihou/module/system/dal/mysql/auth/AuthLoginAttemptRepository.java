package com.geihou.module.system.dal.mysql.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.module.system.dal.dataobject.auth.AuthLoginAttemptDO;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class AuthLoginAttemptRepository {

    private final AuthLoginAttemptMapper mapper;

    public AuthLoginAttemptRepository(AuthLoginAttemptMapper mapper) {
        this.mapper = mapper;
    }

    public int insert(AuthLoginAttemptDO entity) {
        return mapper.insert(entity);
    }

    public List<AuthLoginAttemptDO> selectRecentByUsername(String username, int limit) {
        return mapper.selectList(new LambdaQueryWrapper<AuthLoginAttemptDO>()
                .eq(AuthLoginAttemptDO::getUsername, username)
                .orderByDesc(AuthLoginAttemptDO::getAttemptTime)
                .last("LIMIT " + Math.max(1, limit)));
    }
}
