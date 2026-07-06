package com.geihou.framework.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geihou.framework.security.core.filter.GeihouAdminRouteGuardFilter;
import com.geihou.framework.security.core.filter.GeihouSecurityContextFilter;
import com.geihou.framework.security.core.handler.GeihouSecurityErrorHandler;
import com.geihou.framework.security.core.interceptor.RequirePermissionInterceptor;
import com.geihou.framework.security.core.spi.DenyAllPermissionChecker;
import com.geihou.framework.security.core.spi.DenyAllTokenValidator;
import com.geihou.framework.security.core.spi.PermissionChecker;
import com.geihou.framework.security.core.spi.TokenValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration for Geihou request security context wiring.
 */
@AutoConfiguration
@ConditionalOnClass(jakarta.servlet.Filter.class)
@EnableConfigurationProperties(GeihouSecurityProperties.class)
public class GeihouSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TokenValidator.class)
    public TokenValidator tokenValidator() {
        return new DenyAllTokenValidator();
    }

    @Bean
    @ConditionalOnMissingBean(PermissionChecker.class)
    public PermissionChecker permissionChecker() {
        return new DenyAllPermissionChecker();
    }

    @Bean
    @ConditionalOnMissingBean(GeihouSecurityErrorHandler.class)
    public GeihouSecurityErrorHandler geihouSecurityErrorHandler(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new GeihouSecurityErrorHandler(objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean(GeihouSecurityContextFilter.class)
    public GeihouSecurityContextFilter geihouSecurityContextFilter(
            TokenValidator tokenValidator,
            GeihouSecurityErrorHandler errorHandler) {
        return new GeihouSecurityContextFilter(tokenValidator, errorHandler);
    }

    @Bean
    @ConditionalOnMissingBean(GeihouAdminRouteGuardFilter.class)
    public GeihouAdminRouteGuardFilter geihouAdminRouteGuardFilter(
            GeihouSecurityProperties properties,
            GeihouSecurityErrorHandler errorHandler) {
        return new GeihouAdminRouteGuardFilter(properties, errorHandler);
    }

    @Bean
    @ConditionalOnClass(HandlerInterceptor.class)
    @ConditionalOnMissingBean(RequirePermissionInterceptor.class)
    public RequirePermissionInterceptor requirePermissionInterceptor(
            PermissionChecker permissionChecker,
            GeihouSecurityErrorHandler errorHandler) {
        return new RequirePermissionInterceptor(permissionChecker, errorHandler);
    }

    @Bean
    @ConditionalOnClass(WebMvcConfigurer.class)
    @ConditionalOnMissingBean(RequirePermissionWebMvcConfigurer.class)
    public RequirePermissionWebMvcConfigurer requirePermissionWebMvcConfigurer(
            RequirePermissionInterceptor interceptor) {
        return new RequirePermissionWebMvcConfigurer(interceptor);
    }
}
