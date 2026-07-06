package com.geihou.module.finance.checkout;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.framework.tenant.config.TenantMyBatisAutoConfiguration;
import com.geihou.module.finance.api.product.ProductApi;
import com.geihou.module.finance.api.product.dto.SkuRespDTO;
import com.geihou.module.finance.api.product.dto.SpuRespDTO;
import com.geihou.module.finance.api.product.dto.AddonGroupRespDTO;
import com.geihou.module.finance.api.product.dto.ComboItemRespDTO;
import com.geihou.module.finance.api.product.dto.SkuAvailabilityRespDTO;
import com.geihou.module.finance.cart.CartTestConfig;
import com.geihou.module.finance.order.service.BusinessDateCalculator;
import com.geihou.module.finance.stock.StockTestConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared Spring Boot test configuration for checkout module integration tests.
 *
 * <p>Imports MyBatis-Plus and tenant isolation auto-configurations.
 * Scans cart, checkout, and stock packages for dependency injection.
 * Reuses CartTestConfig for mock ProductApi and BusinessDateCalculator.
 * Imports StockTestConfig for mock StockEventApi + StockQueryApi.
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = {"com.geihou.module.finance.cart", "com.geihou.module.finance.checkout", "com.geihou.module.finance.stock"},
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = CartTestConfig.class)
)
@Import({GeihouMyBatisAutoConfiguration.class, TenantMyBatisAutoConfiguration.class, CartTestConfig.class, StockTestConfig.class})
@MapperScan({"com.geihou.module.finance.cart.dal.mapper", "com.geihou.module.finance.checkout.dal.mapper"})
public class CheckoutTestConfig {

    // Beans (businessDateCalculator, productApi) are provided by CartTestConfig via @Import
    // Mock StockEventApi + StockQueryApi are provided by StockTestConfig via @Import
}
