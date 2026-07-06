package com.geihou.module.supplychain.stock;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.framework.tenant.config.TenantMyBatisAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Shared Spring Boot test configuration for supplychain module integration tests.
 *
 * <p>Imports MyBatis-Plus and tenant isolation auto-configurations.
 * Scans stock and bom mappers and services for dependency injection.
 *
 * <p>BOM packages are included because {@code StockApiImpl} → {@code StockCheckServiceImpl}
 * depends on {@code ProductMasterMapper} and {@code BomExplosionService} from the BOM module.
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan({
    "com.geihou.module.supplychain.stock",
    "com.geihou.module.supplychain.bom",
    "com.geihou.module.supplychain.production",
    "com.geihou.module.supplychain.transfer"
})
@Import({GeihouMyBatisAutoConfiguration.class, TenantMyBatisAutoConfiguration.class})
@MapperScan({
    "com.geihou.module.supplychain.stock.dal.mapper",
    "com.geihou.module.supplychain.bom.dal.mapper",
    "com.geihou.module.supplychain.production.dal.mapper",
    "com.geihou.module.supplychain.transfer.dal.mapper"
})
public class SupplychainTestConfig {
}
