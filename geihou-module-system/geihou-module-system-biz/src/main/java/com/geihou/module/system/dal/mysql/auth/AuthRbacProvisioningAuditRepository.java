package com.geihou.module.system.dal.mysql.auth;

import com.geihou.module.system.dal.dataobject.auth.AuthRbacProvisioningAuditDO;
import org.springframework.stereotype.Repository;

/**
 * Append-only audit repository for RBAC provisioning.
 *
 * <p>H148 RECOVERY DESIGN DECISION: only {@code insert} is exposed.
 * No update, no delete, no soft-delete path.
 */
@Repository
public class AuthRbacProvisioningAuditRepository {

    private final AuthRbacProvisioningAuditMapper mapper;

    public AuthRbacProvisioningAuditRepository(AuthRbacProvisioningAuditMapper mapper) {
        this.mapper = mapper;
    }

    public int insert(AuthRbacProvisioningAuditDO entity) {
        return mapper.insert(entity);
    }
}
