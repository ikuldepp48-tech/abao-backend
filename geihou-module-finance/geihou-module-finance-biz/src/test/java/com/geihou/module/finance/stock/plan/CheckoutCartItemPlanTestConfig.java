package com.geihou.module.finance.stock.plan;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.framework.tenant.config.TenantMyBatisAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot test configuration for {@code finance.stock.plan} tests.
 *
 * <p>Imports MyBatis-Plus and tenant isolation auto-configurations.
 * Scans the plan package for {@link CheckoutCartItemPlanStoreImpl} and
 * maps {@link com.geihou.module.finance.stock.plan.dal.mapper.CheckoutCartItemPlanMapper}.
 *
 * <p>Does NOT scan checkout/order/cart/saga packages - this is a focused
 * config for the classification plan slice only.
 */
@Configuration
@EnableAutoConfiguration
@Import({GeihouMyBatisAutoConfiguration.class, TenantMyBatisAutoConfiguration.class})
@MapperScan({"com.geihou.module.finance.stock.plan.dal.mapper"})
@ComponentScan(basePackages = {"com.geihou.module.finance.stock.plan"})
public class CheckoutCartItemPlanTestConfig {
}
