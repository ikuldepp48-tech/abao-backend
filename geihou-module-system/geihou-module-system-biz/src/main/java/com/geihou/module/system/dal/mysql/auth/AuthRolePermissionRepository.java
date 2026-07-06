package com.geihou.module.system.dal.mysql.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.module.system.dal.dataobject.auth.AuthRolePermissionDO;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRolePermissionRepository {

    private final AuthRolePermissionMapper mapper;

    public AuthRolePermissionRepository(AuthRolePermissionMapper mapper) {
        this.mapper = mapper;
    }

    public List<AuthRolePermissionDO> selectActiveByTenantIdAndRoleIds(Long tenantId, Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.selectList(activeQuery()
                .eq(AuthRolePermissionDO::getTenantId, tenantId)
                .in(AuthRolePermissionDO::getRoleId, roleIds));
    }

    private static LambdaQueryWrapper<AuthRolePermissionDO> activeQuery() {
        return new LambdaQueryWrapper<AuthRolePermissionDO>().eq(AuthRolePermissionDO::getDeleted, false);
    }
}
