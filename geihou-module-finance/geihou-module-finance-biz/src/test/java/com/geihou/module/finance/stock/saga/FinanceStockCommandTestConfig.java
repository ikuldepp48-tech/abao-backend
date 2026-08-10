package com.geihou.module.finance.stock.saga;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.framework.tenant.config.TenantMyBatisAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot test configuration for {@code finance.stock.saga} tests.
 *
 * <p>Imports MyBatis-Plus and tenant isolation auto-configurations.
 * Explicitly imports the saga services under test and maps the saga, cart and
 * checkout mapper packages (the finalizer touches cart and checkout_session).
 *
 * <p>The explicit imports keep test-only configuration classes in this package
 * out of the application context.
 */
@Configuration
@EnableAutoConfiguration
@Import({
        GeihouMyBatisAutoConfiguration.class,
        TenantMyBatisAutoConfiguration.class,
        FinanceStockCommandStoreImpl.class,
        FinanceStockReserveCompletionServiceImpl.class,
        FinanceStockSagaIntentStoreImpl.class,
        CheckoutStockSagaFinalizerImpl.class,
        FinanceStockCommandCreationGateProperties.class,
        FinanceStockCommandCreationGateValidator.class})
@MapperScan({
        "com.geihou.module.finance.stock.saga.dal.mapper",
        "com.geihou.module.finance.cart.dal.mapper",
        "com.geihou.module.finance.checkout.dal.mapper"})
public class FinanceStockCommandTestConfig {
}
