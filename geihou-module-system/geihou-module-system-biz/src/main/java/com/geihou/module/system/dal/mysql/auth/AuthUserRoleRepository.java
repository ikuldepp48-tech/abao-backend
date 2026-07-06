package com.geihou.module.system.dal.mysql.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.module.system.dal.dataobject.auth.AuthUserRoleDO;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class AuthUserRoleRepository {

    private final AuthUserRoleMapper mapper;

    public AuthUserRoleRepository(AuthUserRoleMapper mapper) {
        this.mapper = mapper;
    }

    public List<AuthUserRoleDO> selectActiveByTenantIdAndUserId(Long tenantId, Long userId) {
        return mapper.selectList(activeQuery()
                .eq(AuthUserRoleDO::getTenantId, tenantId)
                .eq(AuthUserRoleDO::getUserId, userId));
    }

    private static LambdaQueryWrapper<AuthUserRoleDO> activeQuery() {
        return new LambdaQueryWrapper<AuthUserRoleDO>().eq(AuthUserRoleDO::getDeleted, false);
    }
}
