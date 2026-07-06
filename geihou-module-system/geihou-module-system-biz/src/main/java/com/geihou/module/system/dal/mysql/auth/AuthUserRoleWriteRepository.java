package com.geihou.module.system.dal.mysql.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.module.system.dal.dataobject.auth.AuthUserRoleDO;
import org.springframework.stereotype.Repository;

/**
 * Write repository for {@code auth_user_role} assignments.
 *
 * <p>H150 RECOVERY DESIGN DECISION: independent from H146
 * {@link AuthUserRoleRepository} (read-only). This repository provides
 * additive + idempotent insert only — no update, no delete, no soft-delete
 * path.
 */
@Repository
public class AuthUserRoleWriteRepository {

    private final AuthUserRoleMapper mapper;

    public AuthUserRoleWriteRepository(AuthUserRoleMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Check whether an active (deleted=false) {@code auth_user_role} row
     * exists for the given {@code (tenantId, userId, roleId)} triple.
     *
     * @param tenantId tenant id (must be positive)
     * @param userId   user id (must be positive)
     * @param roleId   role id (must be positive, tenant-owned)
     * @return {@code true} if an active row exists
     */
    public boolean existsActive(Long tenantId, Long userId, Long roleId) {
        Long count = mapper.selectCount(new LambdaQueryWrapper<AuthUserRoleDO>()
                .eq(AuthUserRoleDO::getTenantId, tenantId)
                .eq(AuthUserRoleDO::getUserId, userId)
                .eq(AuthUserRoleDO::getRoleId, roleId)
                .eq(AuthUserRoleDO::getDeleted, false));
        return count != null && count > 0;
    }

    /**
     * Insert a new {@code auth_user_role} row. Additive only.
     *
     * <p>No update, no delete path. The caller is responsible for
     * idempotency checking via {@link #existsActive} before calling this.
     *
     * @param entity the DO to insert (tenantId/userId/roleId/creator/updater
     *               must be set by the caller; deleted must be false)
     * @return rows affected (1 on success)
     */
    public int insert(AuthUserRoleDO entity) {
        return mapper.insert(entity);
    }
}
