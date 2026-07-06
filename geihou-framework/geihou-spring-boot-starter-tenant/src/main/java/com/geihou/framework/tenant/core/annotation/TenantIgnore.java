package com.geihou.framework.tenant.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type or method that is excluded from tenant filtering.
 *
 * <p>This annotation is pure Java in the first compile slice. Runtime
 * interception and audit behavior are intentionally out of scope.</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantIgnore {
}
