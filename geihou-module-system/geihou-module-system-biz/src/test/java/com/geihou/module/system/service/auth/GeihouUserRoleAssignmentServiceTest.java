package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geihou.module.system.dal.dataobject.auth.AuthRoleDO;
import com.geihou.module.system.dal.dataobject.auth.AuthUserRoleDO;
import com.geihou.module.system.dal.mysql.auth.AuthRoleRepository;
import com.geihou.module.system.dal.mysql.auth.AuthUserRoleWriteRepository;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test for {@link GeihouUserRoleAssignmentService}.
 * No H2, no Spring context. Uses stub repositories.
 */
class GeihouUserRoleAssignmentServiceTest {

    // ---- Successful insert ----

    @Test
    void shouldInsertNewAssignmentForOwner() {
        Stubs stubs = new Stubs();
        stubs.roleRows.add(stubs.role(100L, 1L, "OWNER", "ACTIVE", false));
        GeihouUserRoleAssignmentService svc = stubs.buildService();

        GeihouUserRoleAssignmentService.AssignmentResult result =
                svc.assignRole(1L, 1001L, 100L, GeihouAccessTokenUserRole.OWNER, "test-op");

        assertThat(result.outcome()).isEqualTo("inserted");
        assertThat(result.tenantId()).isEqualTo(1L);
        assertThat(result.userId()).isEqualTo(1001L);
        assertThat(result.roleId()).isEqualTo(100L);
        assertThat(result.roleCode()).isEqualTo("OWNER");
        assertThat(stubs.insertedRows).hasSize(1);
        AuthUserRoleDO inserted = stubs.insertedRows.get(0);
        assertThat(inserted.getTenantId()).isEqualTo(1L);
        assertThat(inserted.getUserId()).isEqualTo(1001L);
        assertThat(inserted.getRoleId()).isEqualTo(100L);
        assertThat(inserted.getCreator()).isEqualTo("test-op");
        assertThat(inserted.getDeleted()).isFalse();
    }

    @Test
    void shouldInsertNewAssignmentForStoreStaffWithCashier() {
        Stubs stubs = new Stubs();
        stubs.roleRows.add(stubs.role(101L, 1L, "CASHIER", "ACTIVE", false));
        GeihouUserRoleAssignmentService svc = stubs.buildService();

        GeihouUserRoleAssignmentService.AssignmentResult result =
                svc.assignRole(1L, 1002L, 101L, GeihouAccessTokenUserRole.STORE_STAFF, "test-op");

        assertThat(result.outcome()).isEqualTo("inserted");
        assertThat(result.roleCode()).isEqualTo("CASHIER");
    }

    @Test
    void shouldInsertForAllTenantIdentities() {
        // CK_WORKER
        {
            Stubs s = new Stubs();
            s.roleRows.add(s.role(110L, 1L, "CK_WORKER", "ACTIVE", false));
            var r = s.buildService().assignRole(1L, 10L, 110L,
                    GeihouAccessTokenUserRole.CK_WORKER, "op");
            assertThat(r.outcome()).isEqualTo("inserted");
            assertThat(r.roleCode()).isEqualTo("CK_WORKER");
        }
        // STORE_MANAGER
        {
            Stubs s = new Stubs();
            s.roleRows.add(s.role(111L, 1L, "SHOP_MANAGER", "ACTIVE", false));
            var r = s.buildService().assignRole(1L, 11L, 111L,
                    GeihouAccessTokenUserRole.STORE_MANAGER, "op");
            assertThat(r.outcome()).isEqualTo("inserted");
            assertThat(r.roleCode()).isEqualTo("SHOP_MANAGER");
        }
        // CK_MANAGER
        {
            Stubs s = new Stubs();
            s.roleRows.add(s.role(112L, 1L, "CK_MANAGER", "ACTIVE", false));
            var r = s.buildService().assignRole(1L, 12L, 112L,
                    GeihouAccessTokenUserRole.CK_MANAGER, "op");
            assertThat(r.outcome()).isEqualTo("inserted");
            assertThat(r.roleCode()).isEqualTo("CK_MANAGER");
        }
        // CUSTOMER
        {
            Stubs s = new Stubs();
            s.roleRows.add(s.role(113L, 1L, "CUSTOMER", "ACTIVE", false));
            var r = s.buildService().assignRole(1L, 13L, 113L,
                    GeihouAccessTokenUserRole.CUSTOMER, "op");
            assertThat(r.outcome()).isEqualTo("inserted");
            assertThat(r.roleCode()).isEqualTo("CUSTOMER");
        }
    }

    // ---- Idempotent skip ----

    @Test
    void shouldSkipWhenActiveAssignmentAlreadyExists() {
        Stubs stubs = new Stubs();
        stubs.roleRows.add(stubs.role(100L, 1L, "OWNER", "ACTIVE", false));
        stubs.existingActive = true; // simulate existing active row
        GeihouUserRoleAssignmentService svc = stubs.buildService();

        GeihouUserRoleAssignmentService.AssignmentResult result =
                svc.assignRole(1L, 1001L, 100L, GeihouAccessTokenUserRole.OWNER, "test-op");

        assertThat(result.outcome()).isEqualTo("skipped");
        assertThat(result.roleCode()).isEqualTo("OWNER");
        assertThat(stubs.insertedRows).isEmpty(); // no insert
    }

    @Test
    void idempotentRepeatShouldSkipSecondCall() {
        Stubs stubs = new Stubs();
        stubs.roleRows.add(stubs.role(100L, 1L, "OWNER", "ACTIVE", false));
        GeihouUserRoleAssignmentService svc = stubs.buildService();

        // First call inserts
        var first = svc.assignRole(1L, 1001L, 100L,
                GeihouAccessTokenUserRole.OWNER, "test-op");
        assertThat(first.outcome()).isEqualTo("inserted");

        // Simulate the row now existing
        stubs.existingActive = true;

        // Second call skips
        var second = svc.assignRole(1L, 1001L, 100L,
                GeihouAccessTokenUserRole.OWNER, "test-op");
        assertThat(second.outcome()).isEqualTo("skipped");
    }

    // ---- Parameter validation fail closed ----

    @Test
    void nullTenantIdShouldThrow() {
        Stubs stubs = new Stubs();
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(null, 1L, 1L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void nonPositiveTenantIdShouldThrow() {
        Stubs stubs = new Stubs();
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(0L, 1L, 1L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(-1L, 1L, 1L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullUserIdShouldThrow() {
        Stubs stubs = new Stubs();
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, null, 1L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void nonPositiveUserIdShouldThrow() {
        Stubs stubs = new Stubs();
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 0L, 1L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, -1L, 1L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullRoleIdShouldThrow() {
        Stubs stubs = new Stubs();
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, null, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roleId");
    }

    @Test
    void nonPositiveRoleIdShouldThrow() {
        Stubs stubs = new Stubs();
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, 0L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, -1L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullUserRoleShouldThrow() {
        Stubs stubs = new Stubs();
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, 1L, null, "op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userRole");
    }

    @Test
    void blankOperatorShouldThrow() {
        Stubs stubs = new Stubs();
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, 1L, GeihouAccessTokenUserRole.OWNER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator");
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, 1L, GeihouAccessTokenUserRole.OWNER, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, 1L, GeihouAccessTokenUserRole.OWNER, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Tenant scope mismatch ----

    @Test
    void tenantScopeMismatchShouldFailClosed() {
        Stubs stubs = new Stubs();
        // OWNER is tenant scope, tenantId=0 should fail
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(0L, 1L, 1L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalArgumentException.class);
        // CONSULTANT is platform scope, tenantId=1 should fail
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, 1L, GeihouAccessTokenUserRole.CONSULTANT, "op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not accept");
    }

    // ---- Role not found / wrong tenant / disabled / deleted ----

    @Test
    void roleNotFoundShouldFailClosed() {
        Stubs stubs = new Stubs();
        // No role rows — query returns empty
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, 999L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void roleWithWrongTenantIdShouldFailClosed() {
        Stubs stubs = new Stubs();
        // Role exists but has tenant_id=2; query for tenant_id=1 returns empty
        stubs.roleRows.add(stubs.role(100L, 2L, "OWNER", "ACTIVE", false));
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, 100L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void disabledRoleShouldFailClosed() {
        Stubs stubs = new Stubs();
        // Role is DISABLED — filtered by query (status != ACTIVE)
        stubs.roleRows.add(stubs.role(100L, 1L, "OWNER", "DISABLED", false));
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, 100L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deletedRoleShouldFailClosed() {
        Stubs stubs = new Stubs();
        // Role is deleted — filtered by query
        stubs.roleRows.add(stubs.role(100L, 1L, "OWNER", "ACTIVE", true));
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, 100L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }

    // ---- Disallowed role code ----

    @Test
    void disallowedRoleCodeShouldFailClosed() {
        Stubs stubs = new Stubs();
        // OWNER identity but role_code is PLATFORM_OPERATOR — not in allowlist
        stubs.roleRows.add(stubs.role(100L, 1L, "PLATFORM_OPERATOR", "ACTIVE", false));
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, 100L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void disallowedRoleCodeForStoreStaffShouldFailClosed() {
        Stubs stubs = new Stubs();
        // STORE_STAFF allowlist is CASHIER/WAITER/KITCHEN_COOK; OWNER is not allowed
        stubs.roleRows.add(stubs.role(100L, 1L, "OWNER", "ACTIVE", false));
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, 100L, GeihouAccessTokenUserRole.STORE_STAFF, "op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void nullRoleCodeShouldFailClosed() {
        Stubs stubs = new Stubs();
        stubs.roleRows.add(stubs.role(100L, 1L, null, "ACTIVE", false));
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, 100L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null/blank");
    }

    @Test
    void blankRoleCodeShouldFailClosed() {
        Stubs stubs = new Stubs();
        stubs.roleRows.add(stubs.role(100L, 1L, "  ", "ACTIVE", false));
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, 100L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null/blank");
    }

    // ---- Tenant0 template role id forbidden ----

    @Test
    void tenant0TemplateRoleIdShouldFailClosed() {
        Stubs stubs = new Stubs();
        // Simulate tenant0 template role (tenant_id=0) — query for tenant_id=1 returns empty
        stubs.roleRows.add(stubs.role(1L, 0L, "OWNER", "ACTIVE", false));
        assertThatThrownBy(() -> stubs.buildService()
                .assignRole(1L, 1L, 1L, GeihouAccessTokenUserRole.OWNER, "op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }

    // ---- Stub infrastructure ----

    private static final class Stubs {
        final List<AuthRoleDO> roleRows = new java.util.ArrayList<>();
        final List<AuthUserRoleDO> insertedRows = new java.util.ArrayList<>();
        boolean existingActive = false;

        AuthRoleDO role(Long id, Long tenantId, String roleCode, String status, boolean deleted) {
            AuthRoleDO r = new AuthRoleDO();
            r.setId(id);
            r.setTenantId(tenantId);
            r.setRoleCode(roleCode);
            r.setRoleName(roleCode);
            r.setStatus(status);
            r.setDeleted(deleted);
            r.setIsBuiltin(true);
            r.setIsAdmin(false);
            r.setCreator("stub");
            r.setCreateTime(LocalDateTime.now());
            r.setUpdater("stub");
            r.setUpdateTime(LocalDateTime.now());
            return r;
        }

        GeihouUserRoleAssignmentService buildService() {
            AuthRoleRepository roleRepo = new AuthRoleRepository(null) {
                @Override
                public List<AuthRoleDO> selectActiveByTenantIdAndIds(Long tenantId, Collection<Long> roleIds) {
                    if (roleIds == null || roleIds.isEmpty()) return List.of();
                    return roleRows.stream()
                            .filter(r -> r.getTenantId().equals(tenantId)
                                    && roleIds.contains(r.getId())
                                    && !Boolean.TRUE.equals(r.getDeleted())
                                    && "ACTIVE".equals(r.getStatus()))
                            .collect(java.util.stream.Collectors.toList());
                }
            };

            AuthUserRoleWriteRepository writeRepo = new AuthUserRoleWriteRepository(null) {
                @Override
                public boolean existsActive(Long tenantId, Long userId, Long roleId) {
                    return existingActive;
                }

                @Override
                public int insert(AuthUserRoleDO entity) {
                    insertedRows.add(entity);
                    return 1;
                }
            };

            return new GeihouUserRoleAssignmentService(roleRepo, writeRepo);
        }
    }
}
