package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geihou.module.system.dal.dataobject.auth.AuthPermissionDO;
import com.geihou.module.system.dal.dataobject.auth.AuthRoleDO;
import com.geihou.module.system.dal.dataobject.auth.AuthRolePermissionDO;
import com.geihou.module.system.dal.dataobject.auth.AuthUserRoleDO;
import com.geihou.module.system.dal.mysql.auth.AuthPermissionRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRolePermissionRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRoleRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRoleRepository;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeihouAuthRolePermissionReadServiceTest {

    // ---- resolveActiveRoleCodes: identity allowlist ----

    @Test
    void resolveActiveRoleCodesShouldReturnOnlyAllowlistedRoleCodesForOwner() {
        Stubs stubs = new Stubs();
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 10L));
        stubs.roleRows.add(stubs.role(10L, 1L, "OWNER", "ACTIVE", false));
        stubs.roleRows.add(stubs.role(11L, 1L, "PLATFORM_OPERATOR", "ACTIVE", false)); // not in allowlist

        GeihouAuthRolePermissionReadService service = stubs.buildService();

        List<String> codes = service.resolveActiveRoleCodes(1L, 1001L, GeihouAccessTokenUserRole.OWNER);

        assertThat(codes).containsExactly("OWNER");
    }

    @Test
    void resolveActiveRoleCodesShouldReturnAllowlistedCodesForStoreStaff() {
        Stubs stubs = new Stubs();
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 10L));
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 11L));
        stubs.roleRows.add(stubs.role(10L, 1L, "CASHIER", "ACTIVE", false));
        stubs.roleRows.add(stubs.role(11L, 1L, "WAITER", "ACTIVE", false));

        List<String> codes = stubs.buildService()
                .resolveActiveRoleCodes(1L, 1001L, GeihouAccessTokenUserRole.STORE_STAFF);

        assertThat(codes).containsExactly("CASHIER", "WAITER");
    }

    @Test
    void resolveActiveRoleCodesShouldReturnAllowlistedCodesForAllTenantIdentities() {
        // CK_WORKER
        Stubs s1 = new Stubs();
        s1.userRoleRows.add(s1.userRole(1L, 1001L, 10L));
        s1.roleRows.add(s1.role(10L, 1L, "CK_WORKER", "ACTIVE", false));
        assertThat(s1.buildService().resolveActiveRoleCodes(1L, 1001L, GeihouAccessTokenUserRole.CK_WORKER))
                .containsExactly("CK_WORKER");

        // STORE_MANAGER
        Stubs s2 = new Stubs();
        s2.userRoleRows.add(s2.userRole(1L, 1001L, 10L));
        s2.roleRows.add(s2.role(10L, 1L, "SHOP_MANAGER", "ACTIVE", false));
        assertThat(s2.buildService().resolveActiveRoleCodes(1L, 1001L, GeihouAccessTokenUserRole.STORE_MANAGER))
                .containsExactly("SHOP_MANAGER");

        // CK_MANAGER
        Stubs s3 = new Stubs();
        s3.userRoleRows.add(s3.userRole(1L, 1001L, 10L));
        s3.roleRows.add(s3.role(10L, 1L, "CK_MANAGER", "ACTIVE", false));
        assertThat(s3.buildService().resolveActiveRoleCodes(1L, 1001L, GeihouAccessTokenUserRole.CK_MANAGER))
                .containsExactly("CK_MANAGER");

        // CUSTOMER
        Stubs s4 = new Stubs();
        s4.userRoleRows.add(s4.userRole(1L, 1001L, 10L));
        s4.roleRows.add(s4.role(10L, 1L, "CUSTOMER", "ACTIVE", false));
        assertThat(s4.buildService().resolveActiveRoleCodes(1L, 1001L, GeihouAccessTokenUserRole.CUSTOMER))
                .containsExactly("CUSTOMER");
    }

    @Test
    void resolveActiveRoleCodesShouldReturnAllowlistedCodesForPlatformIdentities() {
        // CONSULTANT (tenantId=0)
        Stubs s1 = new Stubs();
        s1.userRoleRows.add(s1.userRole(0L, 2001L, 10L));
        s1.roleRows.add(s1.role(10L, 0L, "CONSULTANT", "ACTIVE", false));
        assertThat(s1.buildService().resolveActiveRoleCodes(0L, 2001L, GeihouAccessTokenUserRole.CONSULTANT))
                .containsExactly("CONSULTANT");

        // PLATFORM_ADMIN (tenantId=0)
        Stubs s2 = new Stubs();
        s2.userRoleRows.add(s2.userRole(0L, 2002L, 10L));
        s2.userRoleRows.add(s2.userRole(0L, 2002L, 11L));
        s2.roleRows.add(s2.role(10L, 0L, "PLATFORM_OPERATOR", "ACTIVE", false));
        s2.roleRows.add(s2.role(11L, 0L, "PLATFORM_DEVELOPER", "ACTIVE", false));
        assertThat(s2.buildService().resolveActiveRoleCodes(0L, 2002L, GeihouAccessTokenUserRole.PLATFORM_ADMIN))
                .containsExactly("PLATFORM_DEVELOPER", "PLATFORM_OPERATOR");
    }

    // ---- resolveActiveRoleCodes: fail closed ----

    @Test
    void resolveActiveRoleCodesShouldReturnEmptyForNullUserRoles() {
        Stubs stubs = new Stubs();
        assertThat(stubs.buildService()
                .resolveActiveRoleCodes(1L, 1001L, null)).isEmpty();
    }

    @Test
    void resolveActiveRoleCodesShouldReturnEmptyForNullTenantOrUserId() {
        Stubs stubs = new Stubs();
        assertThat(stubs.buildService()
                .resolveActiveRoleCodes(null, 1001L, GeihouAccessTokenUserRole.OWNER)).isEmpty();
        assertThat(stubs.buildService()
                .resolveActiveRoleCodes(1L, null, GeihouAccessTokenUserRole.OWNER)).isEmpty();
    }

    @Test
    void resolveActiveRoleCodesShouldReturnEmptyForNonPositiveUserId() {
        Stubs stubs = new Stubs();
        assertThat(stubs.buildService()
                .resolveActiveRoleCodes(1L, 0L, GeihouAccessTokenUserRole.OWNER)).isEmpty();
        assertThat(stubs.buildService()
                .resolveActiveRoleCodes(1L, -1L, GeihouAccessTokenUserRole.OWNER)).isEmpty();
    }

    @Test
    void resolveActiveRoleCodesShouldReturnEmptyForTenantScopeMismatch() {
        Stubs stubs = new Stubs();
        // OWNER is tenant scope, tenantId=0 should fail
        assertThat(stubs.buildService()
                .resolveActiveRoleCodes(0L, 1001L, GeihouAccessTokenUserRole.OWNER)).isEmpty();
        // CONSULTANT is platform scope, tenantId=1 should fail
        assertThat(stubs.buildService()
                .resolveActiveRoleCodes(1L, 1001L, GeihouAccessTokenUserRole.CONSULTANT)).isEmpty();
    }

    @Test
    void resolveActiveRoleCodesShouldReturnEmptyWhenNoUserRoles() {
        Stubs stubs = new Stubs();
        assertThat(stubs.buildService()
                .resolveActiveRoleCodes(1L, 9999L, GeihouAccessTokenUserRole.OWNER)).isEmpty();
    }

    @Test
    void resolveActiveRoleCodesShouldExcludeDisabledAndDeletedRoles() {
        Stubs stubs = new Stubs();
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 10L));
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 11L));
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 12L));
        stubs.roleRows.add(stubs.role(10L, 1L, "OWNER", "DISABLED", false)); // filtered by status
        stubs.roleRows.add(stubs.role(11L, 1L, "OWNER", "ACTIVE", true));   // filtered by deleted
        stubs.roleRows.add(stubs.role(12L, 1L, "OWNER", "ACTIVE", false));  // survives

        // Genuinely exercise stub filtering: only the ACTIVE non-deleted role survives.
        assertThat(stubs.buildService()
                .resolveActiveRoleCodes(1L, 1001L, GeihouAccessTokenUserRole.OWNER))
                .containsExactly("OWNER");
    }

    @Test
    void resolveActiveRoleCodesShouldDedupAndSortAndBeImmutable() {
        Stubs stubs = new Stubs();
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 10L));
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 11L));
        stubs.roleRows.add(stubs.role(10L, 1L, "OWNER", "ACTIVE", false));
        stubs.roleRows.add(stubs.role(11L, 1L, "OWNER", "ACTIVE", false)); // duplicate code

        List<String> codes = stubs.buildService()
                .resolveActiveRoleCodes(1L, 1001L, GeihouAccessTokenUserRole.OWNER);

        assertThat(codes).containsExactly("OWNER"); // deduped
        assertThatThrownBy(() -> codes.add("HACK")).isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- I-02: corrupted null role/permission codes must fail closed ----

    @Test
    void resolveActiveRoleCodesShouldFailClosedWhenRoleCodeIsNull() {
        Stubs stubs = new Stubs();
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 10L));
        stubs.roleRows.add(stubs.role(10L, 1L, null, "ACTIVE", false)); // corrupted null roleCode

        // Must fail closed as empty list, never NPE, never grant.
        assertThat(stubs.buildService()
                .resolveActiveRoleCodes(1L, 1001L, GeihouAccessTokenUserRole.OWNER)).isEmpty();
    }

    @Test
    void resolveActiveRoleCodesShouldFailClosedWhenRoleCodeIsBlank() {
        Stubs stubs = new Stubs();
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 10L));
        stubs.roleRows.add(stubs.role(10L, 1L, "  ", "ACTIVE", false)); // blank roleCode

        // Must fail closed as empty list, blank codes must never match allowlist.
        assertThat(stubs.buildService()
                .resolveActiveRoleCodes(1L, 1001L, GeihouAccessTokenUserRole.OWNER)).isEmpty();
    }

    @Test
    void resolveAssignedPermissionCodesShouldFailClosedWhenRoleCodeIsNull() {
        Stubs stubs = new Stubs();
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 10L));
        stubs.roleRows.add(stubs.role(10L, 1L, null, "ACTIVE", false)); // corrupted null roleCode
        stubs.rolePermissionRows.add(stubs.rolePermission(1L, 10L, 100L));
        stubs.permissionRows.add(stubs.permission(100L, "finance:core-profit:read", false));

        // Must fail closed as empty list, never NPE, never grant.
        assertThat(stubs.buildService()
                .resolveAssignedPermissionCodes(1L, 1001L, GeihouAccessTokenUserRole.OWNER)).isEmpty();
    }

    @Test
    void resolveAssignedPermissionCodesShouldFailClosedWhenPermissionCodeIsNull() {
        Stubs stubs = new Stubs();
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 10L));
        stubs.roleRows.add(stubs.role(10L, 1L, "OWNER", "ACTIVE", false));
        stubs.rolePermissionRows.add(stubs.rolePermission(1L, 10L, 100L));
        stubs.permissionRows.add(stubs.permission(100L, null, false)); // corrupted null permissionCode

        // Must fail closed as empty list, never NPE, never grant.
        assertThat(stubs.buildService()
                .resolveAssignedPermissionCodes(1L, 1001L, GeihouAccessTokenUserRole.OWNER)).isEmpty();
    }

    @Test
    void resolveAssignedPermissionCodesShouldFailClosedWhenPermissionCodeIsBlank() {
        Stubs stubs = new Stubs();
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 10L));
        stubs.roleRows.add(stubs.role(10L, 1L, "OWNER", "ACTIVE", false));
        stubs.rolePermissionRows.add(stubs.rolePermission(1L, 10L, 100L));
        stubs.permissionRows.add(stubs.permission(100L, "  ", false)); // blank permissionCode

        // Must fail closed as empty list, blank codes must never be returned.
        assertThat(stubs.buildService()
                .resolveAssignedPermissionCodes(1L, 1001L, GeihouAccessTokenUserRole.OWNER)).isEmpty();
    }

    // ---- resolveAssignedPermissionCodes ----

    @Test
    void resolveAssignedPermissionCodesShouldFollowStrictChain() {
        Stubs stubs = new Stubs();
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 10L));
        stubs.roleRows.add(stubs.role(10L, 1L, "OWNER", "ACTIVE", false));
        stubs.rolePermissionRows.add(stubs.rolePermission(1L, 10L, 100L));
        stubs.rolePermissionRows.add(stubs.rolePermission(1L, 10L, 101L));
        stubs.permissionRows.add(stubs.permission(100L, "finance:core-profit:read", false));
        stubs.permissionRows.add(stubs.permission(101L, "cart:staff-assisted", false));

        List<String> codes = stubs.buildService()
                .resolveAssignedPermissionCodes(1L, 1001L, GeihouAccessTokenUserRole.OWNER);

        assertThat(codes).containsExactly("cart:staff-assisted", "finance:core-profit:read");
    }

    @Test
    void resolveAssignedPermissionCodesShouldReturnEmptyForNoRoles() {
        Stubs stubs = new Stubs();
        assertThat(stubs.buildService()
                .resolveAssignedPermissionCodes(1L, 9999L, GeihouAccessTokenUserRole.OWNER)).isEmpty();
    }

    @Test
    void resolveAssignedPermissionCodesShouldReturnEmptyForTenantScopeMismatch() {
        Stubs stubs = new Stubs();
        assertThat(stubs.buildService()
                .resolveAssignedPermissionCodes(0L, 1001L, GeihouAccessTokenUserRole.OWNER)).isEmpty();
        assertThat(stubs.buildService()
                .resolveAssignedPermissionCodes(1L, 1001L, GeihouAccessTokenUserRole.CONSULTANT)).isEmpty();
    }

    @Test
    void resolveAssignedPermissionCodesShouldDedupAndSortAndBeImmutable() {
        Stubs stubs = new Stubs();
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 10L));
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 11L));
        stubs.roleRows.add(stubs.role(10L, 1L, "OWNER", "ACTIVE", false));
        stubs.roleRows.add(stubs.role(11L, 1L, "OWNER", "ACTIVE", false));
        // Both roles link to the same permission
        stubs.rolePermissionRows.add(stubs.rolePermission(1L, 10L, 100L));
        stubs.rolePermissionRows.add(stubs.rolePermission(1L, 11L, 100L));
        stubs.permissionRows.add(stubs.permission(100L, "finance:core-profit:read", false));

        List<String> codes = stubs.buildService()
                .resolveAssignedPermissionCodes(1L, 1001L, GeihouAccessTokenUserRole.OWNER);

        assertThat(codes).containsExactly("finance:core-profit:read"); // deduped
        assertThatThrownBy(() -> codes.add("HACK")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resolveAssignedPermissionCodesShouldExcludeNonAllowlistedRolePermissions() {
        Stubs stubs = new Stubs();
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 10L));
        stubs.userRoleRows.add(stubs.userRole(1L, 1001L, 11L));
        // OWNER allowlist is only "OWNER", so SHOP_MANAGER role's permissions are excluded
        stubs.roleRows.add(stubs.role(10L, 1L, "OWNER", "ACTIVE", false));
        stubs.roleRows.add(stubs.role(11L, 1L, "SHOP_MANAGER", "ACTIVE", false));
        stubs.rolePermissionRows.add(stubs.rolePermission(1L, 10L, 100L));
        stubs.rolePermissionRows.add(stubs.rolePermission(1L, 11L, 101L));
        stubs.permissionRows.add(stubs.permission(100L, "finance:core-profit:read", false));
        stubs.permissionRows.add(stubs.permission(101L, "cart:staff-assisted", false));

        List<String> codes = stubs.buildService()
                .resolveAssignedPermissionCodes(1L, 1001L, GeihouAccessTokenUserRole.OWNER);

        assertThat(codes).containsExactly("finance:core-profit:read");
    }

    @Test
    void resolveAssignedPermissionCodesShouldReturnConsultantFinancePresent() {
        Stubs stubs = new Stubs();
        stubs.userRoleRows.add(stubs.userRole(0L, 2001L, 10L));
        stubs.roleRows.add(stubs.role(10L, 0L, "CONSULTANT", "ACTIVE", false));
        stubs.rolePermissionRows.add(stubs.rolePermission(0L, 10L, 100L));
        stubs.rolePermissionRows.add(stubs.rolePermission(0L, 10L, 101L));
        stubs.permissionRows.add(stubs.permission(100L, "finance:core-profit:read", false));
        stubs.permissionRows.add(stubs.permission(101L, "consultant:dashboard:read", false));

        List<String> codes = stubs.buildService()
                .resolveAssignedPermissionCodes(0L, 2001L, GeihouAccessTokenUserRole.CONSULTANT);

        assertThat(codes).contains("finance:core-profit:read");
        assertThat(codes).contains("consultant:dashboard:read");
    }

    // ---- Stub infrastructure ----

    private static final class Stubs {
        final List<AuthUserRoleDO> userRoleRows = new java.util.ArrayList<>();
        final List<AuthRoleDO> roleRows = new java.util.ArrayList<>();
        final List<AuthRolePermissionDO> rolePermissionRows = new java.util.ArrayList<>();
        final List<AuthPermissionDO> permissionRows = new java.util.ArrayList<>();

        AuthUserRoleDO userRole(Long tenantId, Long userId, Long roleId) {
            AuthUserRoleDO dobj = new AuthUserRoleDO();
            dobj.setTenantId(tenantId);
            dobj.setUserId(userId);
            dobj.setRoleId(roleId);
            dobj.setDeleted(false);
            return dobj;
        }

        AuthRoleDO role(Long id, Long tenantId, String roleCode, String status, boolean deleted) {
            AuthRoleDO dobj = new AuthRoleDO();
            dobj.setId(id);
            dobj.setTenantId(tenantId);
            dobj.setRoleCode(roleCode);
            dobj.setRoleName(roleCode);
            dobj.setStatus(status);
            dobj.setDeleted(deleted);
            return dobj;
        }

        AuthRolePermissionDO rolePermission(Long tenantId, Long roleId, Long permissionId) {
            AuthRolePermissionDO dobj = new AuthRolePermissionDO();
            dobj.setTenantId(tenantId);
            dobj.setRoleId(roleId);
            dobj.setPermissionId(permissionId);
            dobj.setDeleted(false);
            return dobj;
        }

        AuthPermissionDO permission(Long id, String code, boolean deleted) {
            AuthPermissionDO dobj = new AuthPermissionDO();
            dobj.setId(id);
            dobj.setPermissionCode(code);
            dobj.setPermissionName(code);
            dobj.setRiskLevel("MEDIUM");
            dobj.setDeleted(deleted);
            return dobj;
        }

        GeihouAuthRolePermissionReadService buildService() {
            AuthUserRoleRepository userRoleRepo = new AuthUserRoleRepository(null) {
                @Override
                public List<AuthUserRoleDO> selectActiveByTenantIdAndUserId(Long tenantId, Long userId) {
                    return userRoleRows.stream()
                            .filter(r -> r.getTenantId().equals(tenantId) && r.getUserId().equals(userId)
                                    && !Boolean.TRUE.equals(r.getDeleted()))
                            .collect(java.util.stream.Collectors.toList());
                }
            };
            AuthRoleRepository roleRepo = new AuthRoleRepository(null) {
                @Override
                public List<AuthRoleDO> selectActiveByTenantIdAndIds(Long tenantId, Collection<Long> roleIds) {
                    if (roleIds == null || roleIds.isEmpty()) return List.of();
                    return roleRows.stream()
                            .filter(r -> r.getTenantId().equals(tenantId) && roleIds.contains(r.getId())
                                    && !Boolean.TRUE.equals(r.getDeleted())
                                    && "ACTIVE".equals(r.getStatus()))
                            .collect(java.util.stream.Collectors.toList());
                }
            };
            AuthRolePermissionRepository rpRepo = new AuthRolePermissionRepository(null) {
                @Override
                public List<AuthRolePermissionDO> selectActiveByTenantIdAndRoleIds(Long tenantId, Collection<Long> roleIds) {
                    if (roleIds == null || roleIds.isEmpty()) return List.of();
                    return rolePermissionRows.stream()
                            .filter(r -> r.getTenantId().equals(tenantId) && roleIds.contains(r.getRoleId())
                                    && !Boolean.TRUE.equals(r.getDeleted()))
                            .collect(java.util.stream.Collectors.toList());
                }
            };
            AuthPermissionRepository permRepo = new AuthPermissionRepository(null) {
                @Override
                public List<AuthPermissionDO> selectActiveByIds(Collection<Long> permissionIds) {
                    if (permissionIds == null || permissionIds.isEmpty()) return List.of();
                    return permissionRows.stream()
                            .filter(r -> permissionIds.contains(r.getId()) && !Boolean.TRUE.equals(r.getDeleted()))
                            .collect(java.util.stream.Collectors.toList());
                }
            };
            return new GeihouAuthRolePermissionReadService(userRoleRepo, roleRepo, rpRepo, permRepo);
        }
    }
}
