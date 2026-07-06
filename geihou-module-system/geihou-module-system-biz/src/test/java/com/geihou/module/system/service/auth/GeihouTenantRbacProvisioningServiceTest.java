package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geihou.module.system.dal.dataobject.auth.AuthRbacProvisioningAuditDO;
import com.geihou.module.system.dal.dataobject.auth.AuthRoleDO;
import com.geihou.module.system.dal.dataobject.auth.AuthRolePermissionDO;
import com.geihou.module.system.dal.mysql.auth.AuthRbacProvisioningAuditMapper;
import com.geihou.module.system.dal.mysql.auth.AuthRbacProvisioningAuditRepository;
import com.geihou.module.system.dal.mysql.auth.AuthRoleMapper;
import com.geihou.module.system.dal.mysql.auth.AuthRolePermissionMapper;
import com.geihou.module.system.dal.mysql.auth.GeihouTenantRbacProvisioningRepository;
import com.geihou.module.system.dal.mysql.auth.GeihouTenantRbacProvisioningRepository.TenantRow;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Pure unit test for {@link GeihouTenantRbacProvisioningService}.
 * No H2, no Spring context. Uses stub repositories and a no-op transaction manager.
 */
class GeihouTenantRbacProvisioningServiceTest {

    // ---- T-25/T-28: static guard — no update/delete in production code ----

    @Test
    void productionRepositoryMustHaveNoUpdateOrDeleteMethods() throws Exception {
        String repoPath = "src/main/java/com/geihou/module/system/dal/mysql/auth/"
                + "GeihouTenantRbacProvisioningRepository.java";
        String auditRepoPath = "src/main/java/com/geihou/module/system/dal/mysql/auth/"
                + "AuthRbacProvisioningAuditRepository.java";
        String sourceDir = System.getProperty("user.dir");
        // Try multiple resolution paths
        java.nio.file.Path base = java.nio.file.Paths.get(sourceDir);
        java.nio.file.Path repoFile = base.resolve(repoPath);
        if (!java.nio.file.Files.exists(repoFile)) {
            // When running from geihou-module-system-biz module
            repoFile = base.resolve("geihou-module-system/geihou-module-system-biz/" + repoPath);
        }
        if (!java.nio.file.Files.exists(repoFile)) {
            // When running from reactor root
            repoFile = base.resolve("abao-backend/geihou-module-system/geihou-module-system-biz/" + repoPath);
        }
        String repoContent = java.nio.file.Files.readString(repoFile);
        assertThat(repoContent).doesNotContain("UPDATE ", "DELETE ", ".update(", ".delete(");
        assertThat(repoContent).doesNotContain("setDeleted(true)", "setDeleted(1)");

        java.nio.file.Path auditRepoFile = findFile(base, auditRepoPath);
        String auditRepoContent = java.nio.file.Files.readString(auditRepoFile);
        assertThat(auditRepoContent).doesNotContain("UPDATE ", "DELETE ", ".update(", ".delete(");
    }

    @Test
    void auditRepositoryMustOnlyExposeInsert() throws Exception {
        String content = readProductionFile("AuthRbacProvisioningAuditRepository.java");
        // Only insert method should be public, no update/delete
        assertThat(content).contains("public int insert(");
        assertThat(content).doesNotContain("public.*update", "public.*delete");
    }

    // ---- Service flow: successful A-type provisioning ----

    @Test
    void shouldProvisionTypeATenantWith8RolesAnd32Grants() {
        Stubs stubs = new Stubs();
        stubs.tenant = new TenantRow(2L, "A", "ACTIVE", "EXPLORATION");
        stubs.seedTemplateRolesAndGrants("A");
        GeihouTenantRbacProvisioningService svc = stubs.buildService();

        GeihouTenantRbacProvisioningService.ProvisioningResult result =
                svc.ensureTenantRbac(2L, "test-op");

        assertThat(result.outcome()).isEqualTo("SUCCEEDED");
        assertThat(result.merchantType()).isEqualTo("A");
        assertThat(result.rolesExpected()).isEqualTo(8);
        assertThat(result.rolesInserted()).isEqualTo(8);
        assertThat(result.rolesSkipped()).isEqualTo(0);
        assertThat(result.grantsExpected()).isEqualTo(32);
        assertThat(result.grantsInserted()).isEqualTo(32);
        assertThat(result.grantsSkipped()).isEqualTo(0);
        assertThat(result.grantsPreserved()).isEqualTo(0);
        assertThat(result.errorClass()).isNull();
        assertThat(result.errorDetail()).isNull();

        // Verify audit: STARTED + SUCCEEDED
        assertThat(stubs.auditInserts).hasSize(2);
        assertThat(stubs.auditInserts.get(0).getOutcome()).isEqualTo("STARTED");
        assertThat(stubs.auditInserts.get(1).getOutcome()).isEqualTo("SUCCEEDED");
    }

    // ---- Service flow: successful B-type provisioning ----

    @Test
    void shouldProvisionTypeBTenantWith6RolesAnd4Grants() {
        Stubs stubs = new Stubs();
        stubs.tenant = new TenantRow(1L, "B", "ACTIVE", "EXPLORATION");
        stubs.seedTemplateRolesAndGrants("B");
        GeihouTenantRbacProvisioningService svc = stubs.buildService();

        GeihouTenantRbacProvisioningService.ProvisioningResult result =
                svc.ensureTenantRbac(1L, "test-op");

        assertThat(result.outcome()).isEqualTo("SUCCEEDED");
        assertThat(result.merchantType()).isEqualTo("B");
        assertThat(result.rolesExpected()).isEqualTo(6);
        assertThat(result.rolesInserted()).isEqualTo(6);
        assertThat(result.rolesSkipped()).isEqualTo(0);
        // B-type: 4 grants (OWNER=2, SHOP_MANAGER=1, CASHIER=1)
        assertThat(result.grantsExpected()).isEqualTo(4);
        assertThat(result.grantsInserted()).isEqualTo(4);
        assertThat(result.grantsSkipped()).isEqualTo(0);

        // Verify audit
        assertThat(stubs.auditInserts).hasSize(2);
        assertThat(stubs.auditInserts.get(0).getOutcome()).isEqualTo("STARTED");
        assertThat(stubs.auditInserts.get(1).getOutcome()).isEqualTo("SUCCEEDED");
    }

    // ---- T-10: idempotent repeat ----

    @Test
    void idempotentRepeatShouldSkipAllRolesAndGrants() {
        Stubs stubs = new Stubs();
        stubs.tenant = new TenantRow(1L, "B", "ACTIVE", "EXPLORATION");
        stubs.seedTemplateRolesAndGrants("B");
        // Pre-populate tenant roles and grants as if already provisioned
        stubs.seedExistingTenantRolesAndGrants(1L, "B");
        GeihouTenantRbacProvisioningService svc = stubs.buildService();

        GeihouTenantRbacProvisioningService.ProvisioningResult result =
                svc.ensureTenantRbac(1L, "test-op");

        assertThat(result.outcome()).isEqualTo("SUCCEEDED");
        assertThat(result.rolesInserted()).isEqualTo(0);
        assertThat(result.rolesSkipped()).isEqualTo(6);
        assertThat(result.grantsInserted()).isEqualTo(0);
        assertThat(result.grantsSkipped()).isEqualTo(4);
    }

    // ---- T-12/T-19: is_admin mismatch fails closed ----

    @Test
    void isAdminMismatchShouldFailClosed() {
        Stubs stubs = new Stubs();
        stubs.tenant = new TenantRow(1L, "B", "ACTIVE", "EXPLORATION");
        stubs.seedTemplateRolesAndGrants("B");
        // Pre-populate OWNER with wrong is_admin
        stubs.existingTenantRoles.add(stubs.role(100L, 1L, "OWNER", "ACTIVE", true, false));
        GeihouTenantRbacProvisioningService svc = stubs.buildService();

        assertThatThrownBy(() -> svc.ensureTenantRbac(1L, "test-op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is_admin");

        // Audit: STARTED + FAILED
        assertThat(stubs.auditInserts).hasSize(2);
        assertThat(stubs.auditInserts.get(0).getOutcome()).isEqualTo("STARTED");
        assertThat(stubs.auditInserts.get(1).getOutcome()).isEqualTo("FAILED");
    }

    // ---- T-20: is_builtin mismatch fails closed ----

    @Test
    void isBuiltinMismatchShouldFailClosed() {
        Stubs stubs = new Stubs();
        stubs.tenant = new TenantRow(1L, "B", "ACTIVE", "EXPLORATION");
        stubs.seedTemplateRolesAndGrants("B");
        stubs.existingTenantRoles.add(stubs.role(100L, 1L, "OWNER", "ACTIVE", false, false));
        GeihouTenantRbacProvisioningService svc = stubs.buildService();

        assertThatThrownBy(() -> svc.ensureTenantRbac(1L, "test-op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("builtin");
    }

    // ---- T-21: status mismatch fails closed ----

    @Test
    void statusMismatchShouldFailClosed() {
        Stubs stubs = new Stubs();
        stubs.tenant = new TenantRow(1L, "B", "ACTIVE", "EXPLORATION");
        stubs.seedTemplateRolesAndGrants("B");
        stubs.existingTenantRoles.add(stubs.role(100L, 1L, "OWNER", "DISABLED", true, true));
        GeihouTenantRbacProvisioningService svc = stubs.buildService();

        assertThatThrownBy(() -> svc.ensureTenantRbac(1L, "test-op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status");
    }

    // ---- T-22: tenant mismatch fails closed ----

    @Test
    void tenantMismatchShouldFailClosed() {
        Stubs stubs = new Stubs();
        stubs.tenant = new TenantRow(1L, "B", "ACTIVE", "EXPLORATION");
        stubs.seedTemplateRolesAndGrants("B");
        stubs.existingTenantRoles.add(stubs.role(100L, 999L, "OWNER", "ACTIVE", true, true));
        GeihouTenantRbacProvisioningService svc = stubs.buildService();

        assertThatThrownBy(() -> svc.ensureTenantRbac(1L, "test-op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_id");
    }

    // ---- Tenant not found ----

    @Test
    void tenantNotFoundShouldFailClosed() {
        Stubs stubs = new Stubs();
        stubs.tenant = null; // tenant not found
        stubs.seedTemplateRolesAndGrants("B");
        GeihouTenantRbacProvisioningService svc = stubs.buildService();

        assertThatThrownBy(() -> svc.ensureTenantRbac(999L, "test-op"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant not found");

        // STARTED audit should exist, FAILED audit should exist
        assertThat(stubs.auditInserts).hasSize(2);
        assertThat(stubs.auditInserts.get(1).getOutcome()).isEqualTo("FAILED");
    }

    // ---- STARTED audit failure prevents provisioning ----

    @Test
    void startedAuditFailureShouldPreventProvisioning() {
        Stubs stubs = new Stubs();
        stubs.tenant = new TenantRow(1L, "B", "ACTIVE", "EXPLORATION");
        stubs.seedTemplateRolesAndGrants("B");
        stubs.auditInsertShouldFail = true;
        GeihouTenantRbacProvisioningService svc = stubs.buildService();

        assertThatThrownBy(() -> svc.ensureTenantRbac(1L, "test-op"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("STARTED audit");

        // No provisioning should have happened
        assertThat(stubs.insertedRoles).isEmpty();
        assertThat(stubs.insertedGrants).isEmpty();
    }

    // ---- SUCCEEDED audit failure surfaces ----

    @Test
    void succeededAuditFailureShouldSurface() {
        Stubs stubs = new Stubs();
        stubs.tenant = new TenantRow(1L, "B", "ACTIVE", "EXPLORATION");
        stubs.seedTemplateRolesAndGrants("B");
        stubs.auditInsertFailOnSecondCall = true;
        GeihouTenantRbacProvisioningService svc = stubs.buildService();

        assertThatThrownBy(() -> svc.ensureTenantRbac(1L, "test-op"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SUCCEEDED audit");

        // Provisioning DID happen (main tx committed)
        assertThat(stubs.insertedRoles).hasSize(6);
    }

    // ---- T-16: custom role preserved ----

    @Test
    void customRolesNotInExpectedSetShouldBePreserved() {
        Stubs stubs = new Stubs();
        stubs.tenant = new TenantRow(1L, "B", "ACTIVE", "EXPLORATION");
        stubs.seedTemplateRolesAndGrants("B");
        // Add a custom role not in expected set
        stubs.existingTenantRoles.add(stubs.role(200L, 1L, "CUSTOM_ROLE", "ACTIVE", true, false));
        GeihouTenantRbacProvisioningService svc = stubs.buildService();

        GeihouTenantRbacProvisioningService.ProvisioningResult result =
                svc.ensureTenantRbac(1L, "test-op");

        // Custom role should not be touched
        assertThat(result.rolesExpected()).isEqualTo(6); // only expected B roles
        assertThat(result.rolesInserted()).isEqualTo(6); // all 6 expected are new
        assertThat(result.rolesSkipped()).isEqualTo(0);
    }

    // ---- T-17: extra grants preserved ----

    @Test
    void extraGrantsOnExpectedRolesShouldBePreserved() {
        Stubs stubs = new Stubs();
        stubs.tenant = new TenantRow(1L, "B", "ACTIVE", "EXPLORATION");
        stubs.seedTemplateRolesAndGrants("B");
        // Pre-populate expected roles + extra grant
        stubs.seedExistingTenantRolesAndGrants(1L, "B");
        // Add extra grant on OWNER (permission_id=999, not in template)
        stubs.existingTenantGrants.add(stubs.grant(500L, 1L, 100L, 999L)); // 100=OWNER tenant role id
        GeihouTenantRbacProvisioningService svc = stubs.buildService();

        GeihouTenantRbacProvisioningService.ProvisioningResult result =
                svc.ensureTenantRbac(1L, "test-op");

        assertThat(result.grantsPreserved()).isEqualTo(1);
    }

    // ---- T-18: role_name/description divergence succeeds ----

    @Test
    void roleNameDescriptionDivergenceShouldSucceed() {
        Stubs stubs = new Stubs();
        stubs.tenant = new TenantRow(1L, "B", "ACTIVE", "EXPLORATION");
        stubs.seedTemplateRolesAndGrants("B");
        // Pre-populate OWNER with different name/description but matching security fields
        AuthRoleDO owner = stubs.role(100L, 1L, "OWNER", "ACTIVE", true, true);
        owner.setRoleName("Custom Owner Name");
        owner.setDescription("Custom Description");
        stubs.existingTenantRoles.add(owner);
        GeihouTenantRbacProvisioningService svc = stubs.buildService();

        GeihouTenantRbacProvisioningService.ProvisioningResult result =
                svc.ensureTenantRbac(1L, "test-op");

        assertThat(result.outcome()).isEqualTo("SUCCEEDED");
        assertThat(result.rolesSkipped()).isEqualTo(1); // OWNER skipped
    }

    // ---- T-01: SQL parity ----

    @Test
    void sqlParityFromCreateTableOnward() throws Exception {
        java.nio.file.Path base = java.nio.file.Paths.get(System.getProperty("user.dir"));
        java.nio.file.Path candidate = findFile(base,
                "db/migrations/V01_008__auth_rbac_provisioning_audit.sql");
        java.nio.file.Path runtime = findFile(base,
                "geihou-bootstrap/src/main/resources/db/migration/V01_008__auth_rbac_provisioning_audit.sql");

        String candidateText = java.nio.file.Files.readString(candidate);
        String runtimeText = java.nio.file.Files.readString(runtime);

        String candidateBody = candidateText.substring(candidateText.indexOf("CREATE TABLE"));
        String runtimeBody = runtimeText.substring(runtimeText.indexOf("CREATE TABLE"));

        assertThat(runtimeBody).isEqualTo(candidateBody);
    }

    // ---- Invalid input ----

    @Test
    void nullTenantIdShouldThrow() {
        Stubs stubs = new Stubs();
        GeihouTenantRbacProvisioningService svc = stubs.buildService();
        assertThatThrownBy(() -> svc.ensureTenantRbac(null, "op"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonPositiveTenantIdShouldThrow() {
        Stubs stubs = new Stubs();
        GeihouTenantRbacProvisioningService svc = stubs.buildService();
        assertThatThrownBy(() -> svc.ensureTenantRbac(0L, "op"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.ensureTenantRbac(-1L, "op"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Stub infrastructure ----

    private static class Stubs {
        TenantRow tenant;
        final List<AuthRoleDO> templateRoles = new ArrayList<>();
        final List<AuthRolePermissionDO> templateGrants = new ArrayList<>();
        final List<AuthRoleDO> existingTenantRoles = new ArrayList<>();
        final List<AuthRolePermissionDO> existingTenantGrants = new ArrayList<>();
        final List<AuthRbacProvisioningAuditDO> auditInserts = new ArrayList<>();
        final List<AuthRoleDO> insertedRoles = new ArrayList<>();
        final List<AuthRolePermissionDO> insertedGrants = new ArrayList<>();
        boolean auditInsertShouldFail = false;
        boolean auditInsertFailOnSecondCall = false;
        final AtomicInteger auditCallCount = new AtomicInteger(0);
        private long nextRoleId = 100L;
        private long nextGrantId = 1000L;

        void seedTemplateRolesAndGrants(String type) {
            // Template roles at tenant_id=0
            if ("A".equals(type)) {
                templateRoles.add(role(1L, 0L, "OWNER", "ACTIVE", true, true));
                templateRoles.add(role(2L, 0L, "SHOP_MANAGER", "ACTIVE", true, false));
                templateRoles.add(role(3L, 0L, "CK_MANAGER", "ACTIVE", true, false));
                templateRoles.add(role(4L, 0L, "CASHIER", "ACTIVE", true, false));
                templateRoles.add(role(5L, 0L, "WAITER", "ACTIVE", true, false));
                templateRoles.add(role(6L, 0L, "KITCHEN_COOK", "ACTIVE", true, false));
                templateRoles.add(role(7L, 0L, "CK_WORKER", "ACTIVE", true, false));
                templateRoles.add(role(8L, 0L, "CUSTOMER", "ACTIVE", true, false));
            } else {
                templateRoles.add(role(1L, 0L, "OWNER", "ACTIVE", true, true));
                templateRoles.add(role(2L, 0L, "SHOP_MANAGER", "ACTIVE", true, false));
                templateRoles.add(role(4L, 0L, "CASHIER", "ACTIVE", true, false));
                templateRoles.add(role(5L, 0L, "WAITER", "ACTIVE", true, false));
                templateRoles.add(role(6L, 0L, "KITCHEN_COOK", "ACTIVE", true, false));
                templateRoles.add(role(8L, 0L, "CUSTOMER", "ACTIVE", true, false));
            }
            // Template grants - using permission IDs
            // OWNER: 14 grants (perm 1-14), but B-type filters to 2 (perm 1,2)
            // SHOP_MANAGER: 1 grant (perm 2)
            // CK_MANAGER: 12 grants (perm 3-14) - A only
            // CASHIER: 1 grant (perm 2)
            // CK_WORKER: 4 grants (perm 3,5,7,8) - A only
            // Permission code map: 1=finance:core-profit:read, 2=cart:staff-assisted,
            // 3=ck:dashboard:read, 4=ck:procurement:read, 5=ck:material:read,
            // 6=supplier:read, 7=bom:read, 8=production:write, 9=cost:read,
            // 10=employee:read, 11=delivery:write, 12=iot:read, 13=decision:read,
            // 14=ck:settings:write
            // OWNER grants: perm 1-14
            for (int p = 1; p <= 14; p++) {
                templateGrants.add(grant(1L, 0L, 1L, (long) p)); // role_id=1 (OWNER template)
            }
            // SHOP_MANAGER: perm 2
            templateGrants.add(grant(2L, 0L, 2L, 2L));
            // CK_MANAGER: perm 3-14 (12 grants)
            for (int p = 3; p <= 14; p++) {
                templateGrants.add(grant(3L, 0L, 3L, (long) p));
            }
            // CASHIER: perm 2
            templateGrants.add(grant(4L, 0L, 4L, 2L));
            // CK_WORKER: perm 3,5,7,8 (4 grants)
            templateGrants.add(grant(5L, 0L, 7L, 3L));
            templateGrants.add(grant(6L, 0L, 7L, 5L));
            templateGrants.add(grant(7L, 0L, 7L, 7L));
            templateGrants.add(grant(8L, 0L, 7L, 8L));
        }

        void seedExistingTenantRolesAndGrants(Long tenantId, String type) {
            // Clone all template roles to tenant with new IDs
            Map<Long, Long> remap = new HashMap<>();
            for (AuthRoleDO template : templateRoles) {
                AuthRoleDO tenantRole = role(nextRoleId, tenantId,
                        template.getRoleCode(), "ACTIVE",
                        true, template.getIsAdmin());
                remap.put(template.getId(), nextRoleId);
                existingTenantRoles.add(tenantRole);
                nextRoleId++;
            }
            // Clone grants with B-type filter
            boolean isB = "B".equals(type);
            Set<String> ckCodes = Set.of(
                    "ck:dashboard:read", "ck:procurement:read", "ck:material:read",
                    "supplier:read", "bom:read", "production:write", "cost:read",
                    "employee:read", "delivery:write", "iot:read", "decision:read",
                    "ck:settings:write");
            Map<Long, String> permCodes = new HashMap<>();
            permCodes.put(1L, "finance:core-profit:read");
            permCodes.put(2L, "cart:staff-assisted");
            permCodes.put(3L, "ck:dashboard:read");
            permCodes.put(4L, "ck:procurement:read");
            permCodes.put(5L, "ck:material:read");
            permCodes.put(6L, "supplier:read");
            permCodes.put(7L, "bom:read");
            permCodes.put(8L, "production:write");
            permCodes.put(9L, "cost:read");
            permCodes.put(10L, "employee:read");
            permCodes.put(11L, "delivery:write");
            permCodes.put(12L, "iot:read");
            permCodes.put(13L, "decision:read");
            permCodes.put(14L, "ck:settings:write");

            for (AuthRolePermissionDO templateGrant : templateGrants) {
                String permCode = permCodes.get(templateGrant.getPermissionId());
                if (isB && permCode != null && ckCodes.contains(permCode)) {
                    continue; // filtered
                }
                Long tenantRoleId = remap.get(templateGrant.getRoleId());
                if (tenantRoleId == null) continue;
                existingTenantGrants.add(grant(nextGrantId, tenantId,
                        tenantRoleId, templateGrant.getPermissionId()));
                nextGrantId++;
            }
        }

        GeihouTenantRbacProvisioningService buildService() {
            GeihouTenantRbacProvisioningRepository provisioningRepo =
                    new GeihouTenantRbacProvisioningRepository(null, null, null) {
                        @Override
                        public TenantRow selectTenantForUpdate(Long tid) {
                            return tenant;
                        }

                        @Override
                        public List<AuthRoleDO> selectTemplateRolesByRoleCodes(
                                Collection<String> roleCodes) {
                            return templateRoles.stream()
                                    .filter(r -> roleCodes.contains(r.getRoleCode()))
                                    .toList();
                        }

                        @Override
                        public List<AuthRoleDO> selectTenantRolesByTenantId(Long tid) {
                            return new ArrayList<>(existingTenantRoles);
                        }

                        @Override
                        public List<AuthRolePermissionDO> selectTemplateGrantsByRoleIds(
                                Collection<Long> templateRoleIds) {
                            Set<Long> idSet = new java.util.HashSet<>(templateRoleIds);
                            return templateGrants.stream()
                                    .filter(g -> idSet.contains(g.getRoleId()))
                                    .toList();
                        }

                        @Override
                        public List<AuthRolePermissionDO> selectTenantGrantsByTenantId(Long tid) {
                            return new ArrayList<>(existingTenantGrants);
                        }

                        @Override
                        public void validateExpectedScopeReferences(
                                List<AuthRoleDO> tRoles,
                                List<AuthRolePermissionDO> tGrants,
                                Long expectedTenantId) {
                            // Stub: assume valid
                        }

                        @Override
                        public Map<Long, String> selectPermissionCodeMapByIds(
                                Collection<Long> permIds) {
                            Map<Long, String> map = new LinkedHashMap<>();
                            map.put(1L, "finance:core-profit:read");
                            map.put(2L, "cart:staff-assisted");
                            map.put(3L, "ck:dashboard:read");
                            map.put(4L, "ck:procurement:read");
                            map.put(5L, "ck:material:read");
                            map.put(6L, "supplier:read");
                            map.put(7L, "bom:read");
                            map.put(8L, "production:write");
                            map.put(9L, "cost:read");
                            map.put(10L, "employee:read");
                            map.put(11L, "delivery:write");
                            map.put(12L, "iot:read");
                            map.put(13L, "decision:read");
                            map.put(14L, "ck:settings:write");
                            return map;
                        }

                        @Override
                        public long insertTenantRole(AuthRoleDO role) {
                            role.setId(nextRoleId++);
                            insertedRoles.add(role);
                            return role.getId();
                        }

                        @Override
                        public void insertTenantGrant(AuthRolePermissionDO grant) {
                            grant.setId(nextGrantId++);
                            insertedGrants.add(grant);
                        }
                    };

            AuthRbacProvisioningAuditRepository auditRepo =
                    new AuthRbacProvisioningAuditRepository(null) {
                        @Override
                        public int insert(AuthRbacProvisioningAuditDO entity) {
                            if (auditInsertShouldFail) {
                                throw new RuntimeException("audit insert failed");
                            }
                            if (auditInsertFailOnSecondCall && auditCallCount.incrementAndGet() >= 2) {
                                throw new RuntimeException("SUCCEEDED audit insert failed");
                            }
                            auditInserts.add(entity);
                            return 1;
                        }
                    };

            PlatformTransactionManager txManager = new NoopTransactionManager();
            return new GeihouTenantRbacProvisioningService(
                    provisioningRepo, auditRepo, txManager);
        }

        static AuthRoleDO role(long id, long tenantId, String code,
                               String status, boolean isBuiltin, boolean isAdmin) {
            AuthRoleDO r = new AuthRoleDO();
            r.setId(id);
            r.setTenantId(tenantId);
            r.setRoleCode(code);
            r.setRoleName(code);
            r.setDescription(code);
            r.setIsBuiltin(isBuiltin);
            r.setIsAdmin(isAdmin);
            r.setStatus(status);
            r.setCreator("stub");
            r.setCreateTime(LocalDateTime.now());
            r.setUpdater("stub");
            r.setUpdateTime(LocalDateTime.now());
            r.setDeleted(false);
            return r;
        }

        static AuthRolePermissionDO grant(long id, long tenantId, long roleId, long permId) {
            AuthRolePermissionDO g = new AuthRolePermissionDO();
            g.setId(id);
            g.setTenantId(tenantId);
            g.setRoleId(roleId);
            g.setPermissionId(permId);
            g.setCreator("stub");
            g.setCreateTime(LocalDateTime.now());
            g.setUpdater("stub");
            g.setUpdateTime(LocalDateTime.now());
            g.setDeleted(false);
            return g;
        }
    }

    /** No-op transaction manager that executes callbacks synchronously. */
    private static class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition)
                throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
        }
    }

    // ---- File helpers ----

    private static java.nio.file.Path findFile(java.nio.file.Path base, String relativePath)
            throws java.io.IOException {
        java.nio.file.Path p = base.resolve(relativePath);
        if (java.nio.file.Files.exists(p)) return p;
        p = base.resolve("abao-backend/" + relativePath);
        if (java.nio.file.Files.exists(p)) return p;
        p = base.resolve("geihou-module-system/geihou-module-system-biz/" + relativePath);
        if (java.nio.file.Files.exists(p)) return p;
        // Search upward
        java.nio.file.Path current = base;
        for (int i = 0; i < 6; i++) {
            p = current.resolve(relativePath);
            if (java.nio.file.Files.exists(p)) return p;
            p = current.resolve("abao-backend/" + relativePath);
            if (java.nio.file.Files.exists(p)) return p;
            current = current.getParent();
            if (current == null) break;
        }
        throw new java.io.IOException("Cannot find file: " + relativePath
                + " from base: " + base);
    }

    private static String readProductionFile(String fileName) throws java.io.IOException {
        java.nio.file.Path base = java.nio.file.Paths.get(System.getProperty("user.dir"));
        java.nio.file.Path p = findFile(base,
                "src/main/java/com/geihou/module/system/dal/mysql/auth/" + fileName);
        return java.nio.file.Files.readString(p);
    }
}
