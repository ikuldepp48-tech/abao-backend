package com.geihou.module.system.dal.mysql.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.module.system.dal.dataobject.auth.AuthRoleDO;
import com.geihou.module.system.dal.dataobject.auth.AuthRolePermissionDO;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Separate read/write provisioning repository for tenant RBAC clone provisioning.
 *
 * <p>H148 RECOVERY DESIGN DECISION: independent from H146 read repositories.
 * Read + insert only — no update, no delete, no soft-delete path.
 * May use JdbcTemplate + existing bare mappers.
 */
@Repository
public class GeihouTenantRbacProvisioningRepository {

    private final JdbcTemplate jdbcTemplate;
    private final AuthRoleMapper roleMapper;
    private final AuthRolePermissionMapper rolePermissionMapper;

    /** Locked tenant row snapshot. */
    public record TenantRow(Long id, String merchantType, String status, String lifeStage) {}

    public GeihouTenantRbacProvisioningRepository(
            JdbcTemplate jdbcTemplate,
            AuthRoleMapper roleMapper,
            AuthRolePermissionMapper rolePermissionMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    /**
     * SELECT ... FROM tenants WHERE id = ? AND deleted = 0 FOR UPDATE.
     * Returns null if the tenant does not exist.
     */
    public TenantRow selectTenantForUpdate(Long tenantId) {
        List<TenantRow> rows = jdbcTemplate.query(
                "SELECT id, merchant_type, status, life_stage FROM tenants "
                        + "WHERE id = ? AND deleted = 0 FOR UPDATE",
                (rs, rowNum) -> new TenantRow(
                        rs.getLong("id"),
                        rs.getString("merchant_type"),
                        rs.getString("status"),
                        rs.getString("life_stage")),
                tenantId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Read tenant_id=0 builtin ACTIVE template roles by role_code. */
    public List<AuthRoleDO> selectTemplateRolesByRoleCodes(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<AuthRoleDO>()
                .eq(AuthRoleDO::getTenantId, 0L)
                .eq(AuthRoleDO::getIsBuiltin, true)
                .eq(AuthRoleDO::getStatus, "ACTIVE")
                .eq(AuthRoleDO::getDeleted, false)
                .in(AuthRoleDO::getRoleCode, roleCodes));
    }

    /** Read existing tenant roles (deleted=0) by tenant_id. */
    public List<AuthRoleDO> selectTenantRolesByTenantId(Long tenantId) {
        return roleMapper.selectList(new LambdaQueryWrapper<AuthRoleDO>()
                .eq(AuthRoleDO::getTenantId, tenantId)
                .eq(AuthRoleDO::getDeleted, false));
    }

    /** Read tenant_id=0 template grants by template role_ids. */
    public List<AuthRolePermissionDO> selectTemplateGrantsByRoleIds(Collection<Long> templateRoleIds) {
        if (templateRoleIds == null || templateRoleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<AuthRolePermissionDO>()
                .eq(AuthRolePermissionDO::getTenantId, 0L)
                .eq(AuthRolePermissionDO::getDeleted, false)
                .in(AuthRolePermissionDO::getRoleId, templateRoleIds));
    }

    /** Read existing tenant grants (deleted=0) by tenant_id. */
    public List<AuthRolePermissionDO> selectTenantGrantsByTenantId(Long tenantId) {
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<AuthRolePermissionDO>()
                .eq(AuthRolePermissionDO::getTenantId, tenantId)
                .eq(AuthRolePermissionDO::getDeleted, false));
    }

    /**
     * Validate template scope references:
     * - template roles must have tenant_id=0, is_builtin=1, status=ACTIVE
     * - template grants must have tenant_id=0
     * - template grants must reference existing, non-deleted permissions
     * Throws IllegalStateException on any malformed reference (fail closed).
     */
    public void validateExpectedScopeReferences(List<AuthRoleDO> templateRoles,
                                                 List<AuthRolePermissionDO> templateGrants,
                                                 Long expectedTenantId) {
        for (AuthRoleDO role : templateRoles) {
            if (role.getTenantId() == null || role.getTenantId() != 0L) {
                throw new IllegalStateException(
                        "Template role has wrong tenant_id: " + role.getTenantId()
                                + " for role_code: " + role.getRoleCode());
            }
            if (!Boolean.TRUE.equals(role.getIsBuiltin())) {
                throw new IllegalStateException(
                        "Template role is not builtin: " + role.getRoleCode());
            }
            if (!"ACTIVE".equals(role.getStatus())) {
                throw new IllegalStateException(
                        "Template role is not ACTIVE: " + role.getRoleCode());
            }
        }
        if (!templateGrants.isEmpty()) {
            Set<Long> permissionIds = new java.util.HashSet<>();
            for (AuthRolePermissionDO g : templateGrants) {
                if (g.getTenantId() == null || g.getTenantId() != 0L) {
                    throw new IllegalStateException(
                            "Template grant has wrong tenant_id: " + g.getTenantId());
                }
                permissionIds.add(g.getPermissionId());
            }
            Map<Long, String> permMap = selectPermissionCodeMapByIds(permissionIds);
            for (AuthRolePermissionDO grant : templateGrants) {
                if (!permMap.containsKey(grant.getPermissionId())) {
                    throw new IllegalStateException(
                            "Template grant references missing or deleted permission: "
                                    + grant.getPermissionId());
                }
            }
        }
    }

    /**
     * Read-only helper: map permission_id -> permission_code for the given IDs.
     * Used for B-type CK permission filtering.
     */
    public Map<Long, String> selectPermissionCodeMapByIds(Collection<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT id, permission_code FROM auth_permission WHERE deleted = 0 AND id IN (");
        boolean first = true;
        for (Long ignored : permissionIds) {
            if (!first) sql.append(",");
            sql.append("?");
            first = false;
        }
        sql.append(")");
        Object[] args = permissionIds.toArray();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args);
        Map<Long, String> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(((Number) row.get("id")).longValue(),
                       (String) row.get("permission_code"));
        }
        return result;
    }

    /** Insert a new tenant role; returns the generated id. */
    public long insertTenantRole(AuthRoleDO role) {
        roleMapper.insert(role);
        Long id = role.getId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("Failed to insert tenant role, no generated id");
        }
        return id;
    }

    /** Insert a new tenant grant. */
    public void insertTenantGrant(AuthRolePermissionDO grant) {
        rolePermissionMapper.insert(grant);
    }
}
