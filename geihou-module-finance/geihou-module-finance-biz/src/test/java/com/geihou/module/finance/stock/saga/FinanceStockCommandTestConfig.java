package com.geihou.module.finance.stock.saga;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.framework.tenant.config.TenantMyBatisAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot test configuration for {@code finance.stock.saga} tests.
 *
 * <p>Imports MyBatis-Plus and tenant isolation auto-configurations.
 * Scans the saga package for {@link FinanceStockCommandStoreImpl} and
 * maps {@link com.geihou.module.finance.stock.saga.dal.mapper.FinanceStockCommandMapper}.
 *
 * <p>Does NOT scan checkout/order/cart packages - this is a focused config
 * for the durable command store slice only.
 */
@Configuration
@EnableAutoConfiguration
@Import({GeihouMyBatisAutoConfiguration.class, TenantMyBatisAutoConfiguration.class})
@MapperScan({"com.geihou.module.finance.stock.saga.dal.mapper"})
@ComponentScan(basePackages = {"com.geihou.module.finance.stock.saga"})
public class FinanceStockCommandTestConfig {
}
