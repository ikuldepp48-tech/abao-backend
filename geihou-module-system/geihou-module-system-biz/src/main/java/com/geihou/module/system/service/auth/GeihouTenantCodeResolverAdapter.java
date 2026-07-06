package com.geihou.module.system.service.auth;

import com.geihou.module.system.dal.dataobject.tenant.TenantDO;
import com.geihou.module.system.dal.mysql.tenant.TenantRepository;
import java.util.Optional;

public class GeihouTenantCodeResolverAdapter
        implements GeihouAdminLoginAuthenticationService.TenantCodeResolverPort {

    private final TenantRepository tenantRepository;

    public GeihouTenantCodeResolverAdapter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public Optional<Long> resolveTenantId(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return Optional.empty();
        }
        TenantDO tenant = tenantRepository.selectActiveByTenantCode(tenantCode.trim());
        if (tenant == null || tenant.getId() == null || tenant.getId() <= 0) {
            return Optional.empty();
        }
        return Optional.of(tenant.getId());
    }
}
