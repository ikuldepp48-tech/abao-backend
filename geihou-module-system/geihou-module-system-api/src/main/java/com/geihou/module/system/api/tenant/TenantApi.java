package com.geihou.module.system.api.tenant;

import com.geihou.module.system.api.tenant.dto.TenantRespDTO;

/**
 * Tenant API contract used by Geihou modules.
 *
 * <p>This is a pure Java contract in the first compile slice. RPC annotations,
 * implementation, caching, and runtime behavior are intentionally out of scope.</p>
 */
public interface TenantApi {

    /**
     * Gets the current thread's tenant ID.
     *
     * @return current tenant ID, or {@code null} when no tenant is bound
     */
    Long getCurrentTenantId();

    /**
     * Gets basic tenant information.
     *
     * @param tenantId tenant ID
     * @return tenant DTO, or {@code null} when not found
     */
    TenantRespDTO getTenant(Long tenantId);

    /**
     * Checks whether the tenant has enabled a subsystem.
     *
     * @param tenantId tenant ID
     * @param subsystemId subsystem ID from ENUM_SUBSYSTEM_ID
     * @return true when the subsystem is enabled
     */
    boolean isSubsystemEnabled(Long tenantId, Integer subsystemId);

    /**
     * Gets the tenant merchant type.
     *
     * @param tenantId tenant ID
     * @return merchant type from ENUM_MERCHANT_TYPE
     */
    String getMerchantType(Long tenantId);

    /**
     * Gets the business-day cutoff hour.
     *
     * @param tenantId tenant ID
     * @return cutoff hour in the 0-23 range
     */
    Integer getBusinessDayCutoffHour(Long tenantId);
}
