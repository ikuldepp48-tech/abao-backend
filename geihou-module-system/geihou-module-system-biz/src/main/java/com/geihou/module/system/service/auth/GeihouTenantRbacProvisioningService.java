package com.geihou.module.system.service.auth;

import com.geihou.module.system.dal.dataobject.auth.AuthRbacProvisioningAuditDO;
import com.geihou.module.system.dal.dataobject.auth.AuthRoleDO;
import com.geihou.module.system.dal.dataobject.auth.AuthRolePermissionDO;
import com.geihou.module.system.dal.mysql.auth.AuthRbacProvisioningAuditRepository;
import com.geihou.module.system.dal.mysql.auth.GeihouTenantRbacProvisioningRepository;
import com.geihou.module.system.dal.mysql.auth.GeihouTenantRbacProvisioningRepository.TenantRow;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tenant RBAC clone provisioning service.
 *
 * <p>H148 RECOVERY DESIGN DECISION: ensure/converge semantics for existing tenants.
 * Does not create tenants, does not modify tenant status/life_stage/merchant_type.
 * Uses PlatformTransactionManager + TransactionTemplate (no @Transactional proxy).
 *
 * <p>Audit ordering (frozen by H147 §4.11.3):
 * <ol>
 *   <li>REQUIRES_NEW: insert STARTED, commit before main</li>
 *   <li>REQUIRED: main provisioning (lock tenant + converge RBAC)</li>
 *   <li>REQUIRES_NEW: insert SUCCEEDED after main commit</li>
 *   <li>REQUIRES_NEW: insert FAILED after main rollback/catch</li>
 * </ol>
 */
public class GeihouTenantRbacProvisioningService {

    private static final Set<String> TYPE_A_ROLE_CODES = Set.of(
            "OWNER", "SHOP_MANAGER", "CK_MANAGER", "CASHIER", "WAITER",
            "KITCHEN_COOK", "CK_WORKER", "CUSTOMER");

    private static final Set<String> TYPE_B_ROLE_CODES = Set.of(
            "OWNER", "SHOP_MANAGER", "CASHIER", "WAITER", "KITCHEN_COOK", "CUSTOMER");

    /** 12 CK permission codes filtered out for B-type tenants (Controller correction 2026-06-19). */
    private static final Set<String> CK_PERMISSION_CODES = Set.of(
            "ck:dashboard:read", "ck:procurement:read", "ck:material:read", "supplier:read",
            "bom:read", "production:write", "cost:read", "employee:read", "delivery:write",
            "iot:read", "decision:read", "ck:settings:write");

    private final GeihouTenantRbacProvisioningRepository provisioningRepository;
    private final AuthRbacProvisioningAuditRepository auditRepository;
    private final TransactionTemplate requiredTx;
    private final TransactionTemplate requiresNewTx;

    /** Immutable result of a provisioning attempt. */
    public record ProvisioningResult(
            String attemptId,
            Long tenantId,
            String merchantType,
            String outcome,
            int rolesExpected,
            int rolesInserted,
            int rolesSkipped,
            int grantsExpected,
            int grantsInserted,
            int grantsSkipped,
            int grantsPreserved,
            String errorClass,
            String errorDetail) {}

    /** Internal summary from the main provisioning transaction. */
    private record ProvisioningSummary(
            String merchantType,
            int rolesExpected,
            int rolesInserted,
            int rolesSkipped,
            int grantsExpected,
            int grantsInserted,
            int grantsSkipped,
            int grantsPreserved) {}

    public GeihouTenantRbacProvisioningService(
            GeihouTenantRbacProvisioningRepository provisioningRepository,
            AuthRbacProvisioningAuditRepository auditRepository,
            PlatformTransactionManager transactionManager) {
        this.provisioningRepository = provisioningRepository;
        this.auditRepository = auditRepository;
        this.requiredTx = new TransactionTemplate(transactionManager);
        this.requiredTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Ensure/converge RBAC for an existing tenant. Idempotent.
     *
     * @param tenantId target tenant id (must exist in tenants table)
     * @param operator who triggered this provisioning
     * @return immutable provisioning result
     * @throws IllegalArgumentException if tenantId is null or non-positive
     * @throws IllegalStateException if tenant not found, security fields mismatch,
     *         or malformed template references (fail closed)
     * @throws RuntimeException if STARTED/SUCCEEDED audit finalization fails
     */
    public ProvisioningResult ensureTenantRbac(Long tenantId, String operator) {
        if (tenantId == null || tenantId <= 0L) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        String safeOperator = (operator == null) ? "" : operator;
        String attemptId = UUID.randomUUID().toString();

        // Step 2: REQUIRES_NEW — insert STARTED audit, commit before main.
        try {
            requiresNewTx.executeWithoutResult(status ->
                    insertAudit(attemptId, tenantId, null, "STARTED",
                            0, 0, 0, 0, 0, 0, 0, null, null, safeOperator));
        } catch (Exception e) {
            throw new RuntimeException(
                    "STARTED audit insertion failed for attempt " + attemptId, e);
        }

        // Step 3: REQUIRED — main provisioning transaction.
        ProvisioningSummary summary;
        try {
            summary = requiredTx.execute(status -> doProvisioning(tenantId, safeOperator));
        } catch (Exception e) {
            // Step 5: REQUIRES_NEW — insert FAILED audit after rollback.
            String errorClass = e.getClass().getName();
            String errorDetail = truncate(e.getMessage(), 1024);
            try {
                requiresNewTx.executeWithoutResult(status ->
                        insertAudit(attemptId, tenantId, null, "FAILED",
                                0, 0, 0, 0, 0, 0, 0, errorClass, errorDetail, safeOperator));
            } catch (Exception auditEx) {
                e.addSuppressed(auditEx);
            }
            throw e;
        }

        // Step 4: REQUIRES_NEW — insert SUCCEEDED audit after main commit.
        try {
            requiresNewTx.executeWithoutResult(status ->
                    insertAudit(attemptId, tenantId, summary.merchantType(), "SUCCEEDED",
                            summary.rolesExpected(), summary.rolesInserted(), summary.rolesSkipped(),
                            summary.grantsExpected(), summary.grantsInserted(), summary.grantsSkipped(),
                            summary.grantsPreserved(), null, null, safeOperator));
        } catch (Exception e) {
            // Main tx already committed; surface audit-finalization failure, do not fake success.
            throw new RuntimeException(
                    "SUCCEEDED audit finalization failed for attempt " + attemptId
                            + " (main provisioning already committed)", e);
        }

        return new ProvisioningResult(
                attemptId, tenantId, summary.merchantType(), "SUCCEEDED",
                summary.rolesExpected(), summary.rolesInserted(), summary.rolesSkipped(),
                summary.grantsExpected(), summary.grantsInserted(), summary.grantsSkipped(),
                summary.grantsPreserved(), null, null);
    }

    // ---- Main provisioning logic ----

    private ProvisioningSummary doProvisioning(Long tenantId, String operator) {
        // 1. Lock tenant SELECT FOR UPDATE.
        TenantRow tenant = provisioningRepository.selectTenantForUpdate(tenantId);
        if (tenant == null) {
            throw new IllegalStateException("Tenant not found: " + tenantId);
        }

        // 2. Derive expected role codes from merchant_type.
        Set<String> expectedRoleCodes = deriveRoleCodes(tenant.merchantType());

        // 3. Read template roles.
        List<AuthRoleDO> templateRoles =
                provisioningRepository.selectTemplateRolesByRoleCodes(expectedRoleCodes);

        // 4. Read template grants.
        List<Long> templateRoleIds = templateRoles.stream()
                .map(AuthRoleDO::getId).collect(Collectors.toList());
        List<AuthRolePermissionDO> templateGrants =
                provisioningRepository.selectTemplateGrantsByRoleIds(templateRoleIds);

        // 5. Validate expected scope references (fail closed on malformed).
        provisioningRepository.validateExpectedScopeReferences(
                templateRoles, templateGrants, tenantId);

        // 6. Build permission_id -> permission_code map (for B-type CK filter).
        Set<Long> permissionIds = templateGrants.stream()
                .map(AuthRolePermissionDO::getPermissionId)
                .collect(Collectors.toSet());
        Map<Long, String> permCodeMap =
                provisioningRepository.selectPermissionCodeMapByIds(permissionIds);

        // 7. Read existing tenant roles.
        List<AuthRoleDO> tenantRoles =
                provisioningRepository.selectTenantRolesByTenantId(tenantId);
        Map<String, AuthRoleDO> tenantRolesByCode = tenantRoles.stream()
                .collect(Collectors.toMap(AuthRoleDO::getRoleCode, r -> r, (a, b) -> a));

        // 8. Converge roles.
        int rolesExpected = expectedRoleCodes.size();
        int rolesInserted = 0;
        int rolesSkipped = 0;
        Map<Long, Long> roleIdRemap = new HashMap<>(); // template role_id -> tenant role_id

        for (AuthRoleDO templateRole : templateRoles) {
            AuthRoleDO existing = tenantRolesByCode.get(templateRole.getRoleCode());
            if (existing != null) {
                validateRoleSecurityFields(existing, templateRole, tenantId);
                roleIdRemap.put(templateRole.getId(), existing.getId());
                rolesSkipped++;
            } else {
                AuthRoleDO newRole = cloneRole(templateRole, tenantId, operator);
                long newId = provisioningRepository.insertTenantRole(newRole);
                roleIdRemap.put(templateRole.getId(), newId);
                rolesInserted++;
            }
        }

        // 9. Filter template grants for B-type (remove 12 CK permission codes).
        boolean isBType = "B".equals(tenant.merchantType());
        List<AuthRolePermissionDO> filteredTemplateGrants = templateGrants.stream()
                .filter(g -> {
                    if (!isBType) return true;
                    String permCode = permCodeMap.get(g.getPermissionId());
                    return permCode == null || !CK_PERMISSION_CODES.contains(permCode);
                })
                .collect(Collectors.toList());

        // 10. Read existing tenant grants.
        List<AuthRolePermissionDO> tenantGrants =
                provisioningRepository.selectTenantGrantsByTenantId(tenantId);
        Set<String> existingGrantKeys = tenantGrants.stream()
                .map(g -> g.getRoleId() + ":" + g.getPermissionId())
                .collect(Collectors.toSet());

        // 11. Converge grants.
        int grantsExpected = filteredTemplateGrants.size();
        int grantsInserted = 0;
        int grantsSkipped = 0;
        Set<String> expectedGrantKeys = new HashSet<>();
        Set<Long> expectedTenantRoleIds = new HashSet<>(roleIdRemap.values());

        for (AuthRolePermissionDO templateGrant : filteredTemplateGrants) {
            Long tenantRoleId = roleIdRemap.get(templateGrant.getRoleId());
            if (tenantRoleId == null) {
                throw new IllegalStateException(
                        "No role remap for template grant role_id: " + templateGrant.getRoleId());
            }
            String grantKey = tenantRoleId + ":" + templateGrant.getPermissionId();
            expectedGrantKeys.add(grantKey);

            if (existingGrantKeys.contains(grantKey)) {
                grantsSkipped++;
            } else {
                AuthRolePermissionDO newGrant =
                        cloneGrant(templateGrant, tenantId, tenantRoleId, operator);
                provisioningRepository.insertTenantGrant(newGrant);
                grantsInserted++;
            }
        }

        // 12. Count preserved grants (extra grants on expected roles not in expected set).
        int grantsPreserved = 0;
        for (AuthRolePermissionDO tenantGrant : tenantGrants) {
            if (expectedTenantRoleIds.contains(tenantGrant.getRoleId())) {
                String key = tenantGrant.getRoleId() + ":" + tenantGrant.getPermissionId();
                if (!expectedGrantKeys.contains(key)) {
                    grantsPreserved++;
                }
            }
        }

        return new ProvisioningSummary(
                tenant.merchantType(),
                rolesExpected, rolesInserted, rolesSkipped,
                grantsExpected, grantsInserted, grantsSkipped, grantsPreserved);
    }

    // ---- Helpers ----

    private static Set<String> deriveRoleCodes(String merchantType) {
        if ("A".equals(merchantType)) return TYPE_A_ROLE_CODES;
        if ("B".equals(merchantType)) return TYPE_B_ROLE_CODES;
        throw new IllegalStateException("Unknown merchant_type: " + merchantType);
    }

    private static void validateRoleSecurityFields(AuthRoleDO existing, AuthRoleDO template,
                                                    Long expectedTenantId) {
        if (!Objects.equals(existing.getRoleCode(), template.getRoleCode())) {
            throw new IllegalStateException("Role code mismatch: " + existing.getRoleCode()
                    + " vs " + template.getRoleCode());
        }
        if (!Objects.equals(existing.getTenantId(), expectedTenantId)) {
            throw new IllegalStateException("Role " + existing.getRoleCode()
                    + " has wrong tenant_id: " + existing.getTenantId()
                    + " expected: " + expectedTenantId);
        }
        if (!Boolean.TRUE.equals(existing.getIsBuiltin())) {
            throw new IllegalStateException("Role " + existing.getRoleCode()
                    + " is not builtin (is_builtin != 1)");
        }
        if (!"ACTIVE".equals(existing.getStatus())) {
            throw new IllegalStateException("Role " + existing.getRoleCode()
                    + " has wrong status: " + existing.getStatus() + " expected: ACTIVE");
        }
        if (!Objects.equals(existing.getIsAdmin(), template.getIsAdmin())) {
            throw new IllegalStateException("Role " + existing.getRoleCode()
                    + " has wrong is_admin: " + existing.getIsAdmin()
                    + " expected: " + template.getIsAdmin());
        }
        // role_name and description may differ — preserve existing.
    }

    private static AuthRoleDO cloneRole(AuthRoleDO template, Long tenantId, String operator) {
        AuthRoleDO role = new AuthRoleDO();
        role.setTenantId(tenantId);
        role.setRoleCode(template.getRoleCode());
        role.setRoleName(template.getRoleName());
        role.setDescription(template.getDescription());
        role.setIsBuiltin(true);
        role.setIsAdmin(template.getIsAdmin());
        role.setStatus("ACTIVE");
        role.setCreator(operator);
        role.setCreateTime(LocalDateTime.now());
        role.setUpdater(operator);
        role.setUpdateTime(LocalDateTime.now());
        role.setDeleted(false);
        return role;
    }

    private static AuthRolePermissionDO cloneGrant(AuthRolePermissionDO template,
                                                    Long tenantId, Long tenantRoleId,
                                                    String operator) {
        AuthRolePermissionDO grant = new AuthRolePermissionDO();
        grant.setTenantId(tenantId);
        grant.setRoleId(tenantRoleId);
        grant.setPermissionId(template.getPermissionId());
        grant.setCreator(operator);
        grant.setCreateTime(LocalDateTime.now());
        grant.setUpdater(operator);
        grant.setUpdateTime(LocalDateTime.now());
        grant.setDeleted(false);
        return grant;
    }

    private void insertAudit(String attemptId, Long tenantId, String merchantType,
                             String outcome,
                             int rolesExpected, int rolesInserted, int rolesSkipped,
                             int grantsExpected, int grantsInserted, int grantsSkipped,
                             int grantsPreserved,
                             String errorClass, String errorDetail,
                             String operator) {
        AuthRbacProvisioningAuditDO audit = new AuthRbacProvisioningAuditDO();
        audit.setAttemptId(attemptId);
        audit.setTenantId(tenantId);
        audit.setMerchantType(merchantType);
        audit.setOutcome(outcome);
        audit.setRolesExpected(rolesExpected);
        audit.setRolesInserted(rolesInserted);
        audit.setRolesSkipped(rolesSkipped);
        audit.setGrantsExpected(grantsExpected);
        audit.setGrantsInserted(grantsInserted);
        audit.setGrantsSkipped(grantsSkipped);
        audit.setGrantsPreserved(grantsPreserved);
        audit.setErrorClass(errorClass);
        audit.setErrorDetail(errorDetail);
        audit.setTriggeredBy(operator);
        audit.setCreator(operator);
        audit.setCreateTime(LocalDateTime.now());
        audit.setUpdater(operator);
        audit.setUpdateTime(LocalDateTime.now());
        auditRepository.insert(audit);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
