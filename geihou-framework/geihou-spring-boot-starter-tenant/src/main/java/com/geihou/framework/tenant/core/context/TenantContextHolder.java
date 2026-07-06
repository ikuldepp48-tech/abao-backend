package com.geihou.framework.tenant.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * Thread-local holder for the current tenant ID.
 *
 * <p>The holder uses TTL so executor tasks can inherit the submitting thread's
 * tenant snapshot when paired with {@link TenantTaskDecorator}.</p>
 */
public final class TenantContextHolder {

    private static final TransmittableThreadLocal<Long> TENANT_ID_HOLDER = new TransmittableThreadLocal<>();

    private static final TransmittableThreadLocal<Boolean> IGNORE_HOLDER = new TransmittableThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setTenantId(Long tenantId) {
        TENANT_ID_HOLDER.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID_HOLDER.get();
    }

    public static void setIgnore(boolean ignore) {
        IGNORE_HOLDER.set(ignore);
    }

    public static boolean isIgnore() {
        return Boolean.TRUE.equals(IGNORE_HOLDER.get());
    }

    public static void clear() {
        TENANT_ID_HOLDER.remove();
        IGNORE_HOLDER.remove();
    }
}
