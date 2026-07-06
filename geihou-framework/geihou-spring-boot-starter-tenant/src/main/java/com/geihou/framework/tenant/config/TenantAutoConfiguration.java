package com.geihou.framework.tenant.config;

import com.geihou.framework.tenant.web.TenantContextFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for request tenant context wiring.
 */
@AutoConfiguration
public class TenantAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TenantContextFilter.class)
    public TenantContextFilter tenantContextFilter() {
        return new TenantContextFilter();
    }
}
