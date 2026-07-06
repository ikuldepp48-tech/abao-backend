package com.geihou.module.finance.product;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.framework.tenant.config.TenantMyBatisAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Shared Spring Boot test configuration for product module integration tests.
 *
 * <p>Imports MyBatis-Plus and tenant isolation auto-configurations.
 * Scans product mappers and services for dependency injection.
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan("com.geihou.module.finance.product")
@Import({GeihouMyBatisAutoConfiguration.class, TenantMyBatisAutoConfiguration.class})
@MapperScan("com.geihou.module.finance.product.dal.mapper")
public class ProductTestConfig {
}
