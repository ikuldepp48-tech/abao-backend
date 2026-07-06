package com.geihou.framework.security.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * Thread-local holder for the current Geihou principal.
 */
public final class GeihouSecurityContextHolder {

    private static final TransmittableThreadLocal<GeihouPrincipal> PRINCIPAL_HOLDER =
            new TransmittableThreadLocal<>();

    private GeihouSecurityContextHolder() {
    }

    public static void set(GeihouPrincipal principal) {
        PRINCIPAL_HOLDER.set(principal);
    }

    public static GeihouPrincipal get() {
        return PRINCIPAL_HOLDER.get();
    }

    public static void clear() {
        PRINCIPAL_HOLDER.remove();
    }
}
