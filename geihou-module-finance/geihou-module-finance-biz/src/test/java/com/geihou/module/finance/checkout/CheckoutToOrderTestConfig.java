package com.geihou.module.finance.checkout;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.framework.tenant.config.TenantMyBatisAutoConfiguration;
import com.geihou.module.finance.cart.CartTestConfig;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.stock.StockTestConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot test configuration for G1-04C checkout-to-order conversion tests.
 *
 * <p>Scans cart, checkout, order, and stock packages together so that
 * CheckoutService.convertToOrder can delegate to OrderService.createFromCheckout
 * with all mappers and services available in a single Spring context.
 *
 * <p>Reuses CartTestConfig via @Import for mock ProductApi and BusinessDateCalculator.
 * Imports StockTestConfig for mock StockEventApi + StockQueryApi.
 * Both CartTestConfig and OrderTestConfig are excluded from component scan to avoid
 * bean definition conflicts.
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = {
        "com.geihou.module.finance.cart",
        "com.geihou.module.finance.checkout",
        "com.geihou.module.finance.order",
        "com.geihou.module.finance.stock"
    },
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
            classes = {CartTestConfig.class, OrderTestConfig.class})
    }
)
@Import({GeihouMyBatisAutoConfiguration.class, TenantMyBatisAutoConfiguration.class, CartTestConfig.class, StockTestConfig.class})
@MapperScan({
    "com.geihou.module.finance.cart.dal.mapper",
    "com.geihou.module.finance.checkout.dal.mapper",
    "com.geihou.module.finance.order.dal.mapper"
})
public class CheckoutToOrderTestConfig {

    // Beans (businessDateCalculator, productApi) are provided by CartTestConfig via @Import.
    // Mock StockEventApi + StockQueryApi are provided by StockTestConfig via @Import.
    // CartTestConfig.MockProductApi has SKU 1001 (Beef Burger, spuId=101, skuCode=SKU_BEEF)
    // and SPU 101 (spuName=Burger, categoryId=10) — sufficient for conversion tests.
}
