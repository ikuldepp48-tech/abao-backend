package com.geihou.module.finance.cart;

import com.geihou.framework.mybatis.config.GeihouMyBatisAutoConfiguration;
import com.geihou.framework.tenant.config.TenantMyBatisAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.geihou.module.finance.api.product.ProductApi;
import com.geihou.module.finance.api.product.dto.SkuRespDTO;
import com.geihou.module.finance.api.product.dto.SpuRespDTO;
import com.geihou.module.finance.api.product.dto.AddonGroupRespDTO;
import com.geihou.module.finance.api.product.dto.ComboItemRespDTO;
import com.geihou.module.finance.api.product.dto.SkuAvailabilityRespDTO;
import com.geihou.module.finance.order.service.BusinessDateCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared Spring Boot test configuration for cart module integration tests.
 *
 * <p>Imports MyBatis-Plus and tenant isolation auto-configurations.
 * Scans cart mappers and services for dependency injection.
 * Provides a mock ProductApi for SKU existence/status checks (CG-5 degradation).
 * Reuses BusinessDateCalculator from the order module (R-6).
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan("com.geihou.module.finance.cart")
@Import({GeihouMyBatisAutoConfiguration.class, TenantMyBatisAutoConfiguration.class})
@MapperScan("com.geihou.module.finance.cart.dal.mapper")
public class CartTestConfig {

    @Bean
    public BusinessDateCalculator businessDateCalculator() {
        return new BusinessDateCalculator();
    }

    @Bean
    public ProductApi productApi() {
        return new MockProductApi();
    }

    public static class MockProductApi implements ProductApi {

        private final Map<Long, SkuRespDTO> skuStore = new HashMap<>();
        private final Map<Long, SpuRespDTO> spuStore = new HashMap<>();

        public MockProductApi() {
            SpuRespDTO spu1 = new SpuRespDTO();
            spu1.setId(101L);
            spu1.setSpuCode("BURGER");
            spu1.setSpuName("Burger");
            spu1.setCategoryId(10L);
            spu1.setStatus("ACTIVE");
            spuStore.put(101L, spu1);

            SkuRespDTO sku1 = new SkuRespDTO();
            sku1.setId(1001L);
            sku1.setSpuId(101L);
            sku1.setSkuCode("SKU_BEEF");
            sku1.setSkuName("Beef Burger");
            sku1.setSellingPrice(new java.math.BigDecimal("12.00"));
            sku1.setStatus("ACTIVE");
            sku1.setPrimaryImageUrl("https://example.com/beef.jpg");
            skuStore.put(1001L, sku1);

            SkuRespDTO sku2 = new SkuRespDTO();
            sku2.setId(1002L);
            sku2.setSpuId(101L);
            sku2.setSkuCode("SKU_CHICKEN");
            sku2.setSkuName("Chicken Burger");
            sku2.setSellingPrice(new java.math.BigDecimal("10.00"));
            sku2.setStatus("ACTIVE");
            skuStore.put(1002L, sku2);

            // Non-available SKU (PAUSED)
            SkuRespDTO sku3 = new SkuRespDTO();
            sku3.setId(3001L);
            sku3.setSpuId(101L);
            sku3.setSkuCode("SKU_PAUSED");
            sku3.setSkuName("Paused Item");
            sku3.setSellingPrice(new java.math.BigDecimal("8.00"));
            sku3.setStatus("PAUSED");
            skuStore.put(3001L, sku3);
        }

        @Override
        public SpuRespDTO getSpu(Long spuId) {
            return spuStore.get(spuId);
        }

        @Override
        public SkuRespDTO getSku(Long skuId) {
            return skuStore.get(skuId);
        }

        @Override
        public Map<Long, SkuRespDTO> batchGetSkus(List<Long> skuIds) {
            Map<Long, SkuRespDTO> result = new HashMap<>();
            for (Long id : skuIds) {
                SkuRespDTO sku = skuStore.get(id);
                if (sku != null) {
                    result.put(id, sku);
                }
            }
            return result;
        }

        @Override
        public SkuAvailabilityRespDTO checkAvailability(Long skuId, Integer quantity) {
            return null;
        }

        @Override
        public List<ComboItemRespDTO> expandCombo(Long comboSkuId) {
            return new ArrayList<>();
        }

        @Override
        public List<AddonGroupRespDTO> getAddonGroupsBySpu(Long spuId) {
            return new ArrayList<>();
        }
    }
}
