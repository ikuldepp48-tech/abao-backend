package com.geihou.framework.tenant.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.framework.tenant.core.aop.TenantIgnoreAspect;
import com.geihou.framework.tenant.core.interceptor.TenantInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-configuration that wires tenant SQL isolation into MyBatis-Plus.
 */
@AutoConfiguration
@AutoConfigureAfter(GeihouMyBatisAutoConfiguration.class)
@ConditionalOnClass({MybatisPlusInterceptor.class, TenantLineInnerInterceptor.class})
public class TenantMyBatisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TenantInterceptor.class)
    public TenantInterceptor tenantInterceptor() {
        return new TenantInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(TenantIgnoreAspect.class)
    public TenantIgnoreAspect tenantIgnoreAspect() {
        return new TenantIgnoreAspect();
    }

    @Bean
    @ConditionalOnBean(MybatisPlusInterceptor.class)
    @ConditionalOnMissingBean(TenantLineInnerInterceptor.class)
    public TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantInterceptor tenantInterceptor,
                                                                 MybatisPlusInterceptor mybatisPlusInterceptor) {
        TenantLineInnerInterceptor tenantLineInnerInterceptor = new TenantLineInnerInterceptor(tenantInterceptor);
        List<InnerInterceptor> innerInterceptors = new ArrayList<>(mybatisPlusInterceptor.getInterceptors());
        innerInterceptors.add(0, tenantLineInnerInterceptor);
        mybatisPlusInterceptor.setInterceptors(innerInterceptors);
        return tenantLineInnerInterceptor;
    }
}
