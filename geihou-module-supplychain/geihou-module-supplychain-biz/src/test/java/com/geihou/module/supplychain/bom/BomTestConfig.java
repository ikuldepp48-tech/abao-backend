package com.geihou.module.supplychain.bom;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.framework.tenant.config.TenantMyBatisAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Shared Spring Boot test configuration for BOM integration tests.
 *
 * <p>Imports MyBatis-Plus and tenant isolation auto-configurations.
 * Scans bom mappers and services for dependency injection.
 *
 * <p>Source: TASK-G2-02A.
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan({
    "com.geihou.module.supplychain.bom",
    "com.geihou.module.supplychain.stock.mq"
})
@Import({GeihouMyBatisAutoConfiguration.class, TenantMyBatisAutoConfiguration.class})
@MapperScan("com.geihou.module.supplychain.bom.dal.mapper")
public class BomTestConfig {
}
