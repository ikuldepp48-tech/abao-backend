package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeihouRbacPermissionCheckerTest {

    @Test
    void hasPermissionShouldDenyNullPrincipal() {
        GeihouRbacPermissionChecker checker = new GeihouRbacPermissionChecker(new StubReadService());

        assertThat(checker.hasPermission(null, "cart:staff-assisted")).isFalse();
    }

    @Test
    void hasPermissionShouldDenyBlankPermission() {
        GeihouRbacPermissionChecker checker = new GeihouRbacPermissionChecker(new StubReadService());

        assertThat(checker.hasPermission(principal(1L, 1001L, "OWNER"), " ")).isFalse();
    }

    @Test
    void hasPermissionShouldAllowWhenRbacReadModelHasPermission() {
        StubReadService readService = new StubReadService();
        readService.grant(1L, 1001L, GeihouAccessTokenUserRole.OWNER, "cart:staff-assisted");
        GeihouRbacPermissionChecker checker = new GeihouRbacPermissionChecker(readService);

        assertThat(checker.hasPermission(principal(1L, 1001L, "OWNER"), "cart:staff-assisted")).isTrue();
    }

    @Test
    void hasPermissionShouldDenyStoreStaffWithoutPermissionCode() {
        StubReadService readService = new StubReadService();
        readService.grant(1L, 1001L, GeihouAccessTokenUserRole.STORE_STAFF, "finance:core-profit:read");
        GeihouRbacPermissionChecker checker = new GeihouRbacPermissionChecker(readService);

        assertThat(checker.hasPermission(principal(1L, 1001L, "STORE_STAFF"), "cart:staff-assisted")).isFalse();
    }

    @Test
    void hasPermissionShouldAllowCashierViaStoreStaffFamilyWhenRbacHasPermission() {
        StubReadService readService = new StubReadService();
        readService.grant(1L, 1001L, GeihouAccessTokenUserRole.STORE_STAFF, "cart:staff-assisted");
        GeihouRbacPermissionChecker checker = new GeihouRbacPermissionChecker(readService);

        assertThat(checker.hasPermission(principal(1L, 1001L, "STORE_STAFF"), "cart:staff-assisted")).isTrue();
    }

    @Test
    void hasPermissionShouldKeepTenantIsolation() {
        StubReadService readService = new StubReadService();
        readService.grant(2L, 1001L, GeihouAccessTokenUserRole.OWNER, "cart:staff-assisted");
        GeihouRbacPermissionChecker checker = new GeihouRbacPermissionChecker(readService);

        assertThat(checker.hasPermission(principal(1L, 1001L, "OWNER"), "cart:staff-assisted")).isFalse();
    }

    @Test
    void hasPermissionShouldDenyUnknownTokenScope() {
        GeihouRbacPermissionChecker checker = new GeihouRbacPermissionChecker(new StubReadService());

        assertThat(checker.hasPermission(principal(1L, 1001L, "cart:staff-assisted"), "cart:staff-assisted"))
                .isFalse();
    }

    @Test
    void hasPermissionShouldFailClosedWhenReadModelThrows() {
        GeihouRbacPermissionChecker checker = new GeihouRbacPermissionChecker(new ThrowingReadService());

        assertThat(checker.hasPermission(principal(1L, 1001L, "OWNER"), "cart:staff-assisted")).isFalse();
    }

    private static GeihouPrincipal principal(Long tenantId, Long userId, String role) {
        return new GeihouPrincipal(userId, "user", tenantId, Set.of(role), Set.of(), "token-1");
    }

    private static class StubReadService extends GeihouAuthRolePermissionReadService {

        private final java.util.Map<Key, List<String>> grants = new java.util.HashMap<>();

        StubReadService() {
            super(null, null, null, null);
        }

        void grant(Long tenantId, Long userId, GeihouAccessTokenUserRole role, String permission) {
            grants.put(new Key(tenantId, userId, role), List.of(permission));
        }

        @Override
        public List<String> resolveAssignedPermissionCodes(Long tenantId, Long userId,
                                                           GeihouAccessTokenUserRole userRole) {
            return grants.getOrDefault(new Key(tenantId, userId, userRole), List.of());
        }
    }

    private static class ThrowingReadService extends StubReadService {

        @Override
        public List<String> resolveAssignedPermissionCodes(Long tenantId, Long userId,
                                                           GeihouAccessTokenUserRole userRole) {
            throw new IllegalStateException("rbac unavailable");
        }
    }

    private record Key(Long tenantId, Long userId, GeihouAccessTokenUserRole role) {
    }
}
