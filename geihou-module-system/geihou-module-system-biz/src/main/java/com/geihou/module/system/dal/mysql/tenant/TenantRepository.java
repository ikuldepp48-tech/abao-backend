package com.geihou.module.system.dal.mysql.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.module.system.dal.dataobject.tenant.TenantDO;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRepository {

    private static final String ACTIVE = "ACTIVE";

    private final TenantMapper mapper;

    public TenantRepository(TenantMapper mapper) {
        this.mapper = mapper;
    }

    public TenantDO selectActiveByTenantCode(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<TenantDO>()
                .eq(TenantDO::getDeleted, false)
                .eq(TenantDO::getStatus, ACTIVE)
                .eq(TenantDO::getTenantCode, tenantCode.trim()));
    }
}
