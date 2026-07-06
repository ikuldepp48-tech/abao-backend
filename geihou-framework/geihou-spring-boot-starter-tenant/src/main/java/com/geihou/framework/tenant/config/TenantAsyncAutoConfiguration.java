package com.geihou.framework.tenant.config;

import com.geihou.framework.tenant.core.context.TenantTaskDecorator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

@AutoConfiguration
public class TenantAsyncAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public TenantTaskDecorator tenantTaskDecorator() {
        return new TenantTaskDecorator();
    }
}
