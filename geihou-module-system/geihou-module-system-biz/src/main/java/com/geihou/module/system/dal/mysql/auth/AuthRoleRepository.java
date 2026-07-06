package com.geihou.module.system.dal.mysql.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.module.system.dal.dataobject.auth.AuthRoleDO;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRoleRepository {

    private final AuthRoleMapper mapper;

    public AuthRoleRepository(AuthRoleMapper mapper) {
        this.mapper = mapper;
    }

    public List<AuthRoleDO> selectActiveByTenantIdAndIds(Long tenantId, Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.selectList(activeQuery()
                .eq(AuthRoleDO::getTenantId, tenantId)
                .in(AuthRoleDO::getId, roleIds)
                .eq(AuthRoleDO::getStatus, "ACTIVE"));
    }

    private static LambdaQueryWrapper<AuthRoleDO> activeQuery() {
        return new LambdaQueryWrapper<AuthRoleDO>().eq(AuthRoleDO::getDeleted, false);
    }
}
