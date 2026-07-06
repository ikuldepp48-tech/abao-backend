package com.geihou.framework.tenant.core.aop;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * Runtime bridge from @TenantIgnore to the tenant SQL interceptor.
 */
@Aspect
public class TenantIgnoreAspect {

    @Around("@annotation(com.geihou.framework.tenant.core.annotation.TenantIgnore)"
            + " || @within(com.geihou.framework.tenant.core.annotation.TenantIgnore)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        boolean previousIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);
            return joinPoint.proceed();
        } finally {
            TenantContextHolder.setIgnore(previousIgnore);
        }
    }
}
