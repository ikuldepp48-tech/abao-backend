package com.geihou.framework.security.config;

import com.geihou.framework.security.core.interceptor.RequirePermissionInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers Geihou method-level permission guard.
 */
public class RequirePermissionWebMvcConfigurer implements WebMvcConfigurer {

    private final RequirePermissionInterceptor interceptor;

    public RequirePermissionWebMvcConfigurer(RequirePermissionInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}
