package com.geihou.module.system.dal.mysql.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.module.system.dal.dataobject.auth.AuthPermissionDO;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class AuthPermissionRepository {

    private final AuthPermissionMapper mapper;

    public AuthPermissionRepository(AuthPermissionMapper mapper) {
        this.mapper = mapper;
    }

    public List<AuthPermissionDO> selectActiveByIds(Collection<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.selectList(activeQuery()
                .in(AuthPermissionDO::getId, permissionIds));
    }

    private static LambdaQueryWrapper<AuthPermissionDO> activeQuery() {
        return new LambdaQueryWrapper<AuthPermissionDO>().eq(AuthPermissionDO::getDeleted, false);
    }
}
